package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.ActorSelection;
import akka.actor.Address;
import natalia.dymnikova.cluster.ActorSystemAdapter;
import natalia.dymnikova.cluster.scheduler.akka.Flow.MemberState.MembersStates;
import natalia.dymnikova.cluster.scheduler.akka.Flow.MemberState.State;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import scala.concurrent.Future$;

import java.util.*;
import java.util.Map.Entry;

import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Created by dyma on 01.05.16.
 */
@RunWith(MockitoJUnitRunner.class)
public class FindOptimalAddressesStrategyTest {

    @Spy
    private Comparator<List<Map<String, Long>>> comparator = new TestComparator();

    @Mock
    private ActorSystemAdapter adapter;

    private NetworkMap networkMap = mock(NetworkMap.class);

    @Spy
    private ClusterMap clusterMap = new ClusterMap(networkMap);

    @InjectMocks
    private FindOptimalAddressesStrategy strategy;

    @Before
    public void setUp() throws Exception {
        doReturn(of(13L)).when(networkMap).getValue(any(), any());
        doReturn(future(MembersStates.newBuilder()
                .addState(State.newBuilder().setName("name").setValue(11L))
                .build())
        ).when(adapter).ask(any(ActorSelection.class), any(), any());

    }

    @Test
    public void shouldWorkForSimpleList() throws Exception {
        final List<List<Address>> list = new ArrayList<>();
        final ArrayList<Address> addresses = new ArrayList<>();
        addresses.add(Address.apply("akka.tcp", "0"));
        list.add(addresses);
        list.add(addresses);
        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).map(Address::toString).collect(toList()),
                contains("akka.tcp://0", "akka.tcp://0")
        );
    }


    public scala.concurrent.Future<Object> future(Object result) {
        return Future$.MODULE$.successful(result);
    }

    private class TestComparator implements Comparator<List<Map<String, Long>>> {
        @Override
        public int compare(final List<Map<String, Long>> o1, final List<Map<String, Long>> o2) {
            return Long.compare(
                    o1.get(0).entrySet().stream().map(Entry::getValue).mapToLong(l->l).sum(),
                    o2.get(0).entrySet().stream().map(Entry::getValue).mapToLong(l->l).sum()
            );
        }
    }
}