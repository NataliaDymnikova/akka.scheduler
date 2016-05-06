package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import natalia.dymnikova.monitoring.MonitoringClient.Snapshot;
import natalia.dymnikova.test.ComparatorForTests;
import natalia.dymnikova.test.MockNetworkMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import scala.concurrent.Future$;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by dyma on 01.05.16.
 */
@RunWith(MockitoJUnitRunner.class)
public class FindOptimalAddressesStrategyTest {

    @Spy
    private Comparator<List<Map<String, Long>>> comparator = new ComparatorForTests();

    @Mock
    private MembersStates states;

    private NetworkMap networkMap = new MockNetworkMap();

    @Spy
    private ClusterMap clusterMap = new ClusterMap(networkMap);

    @InjectMocks
    private FindOptimalAddressesStrategy strategy;

    @Before
    public void setUp() throws Exception {
        doAnswer(invocation -> createSnapshot((Address) invocation.getArguments()[0])).when(states).getState(any());
    }

    @Test
    public void shouldWorkForSimpleList() throws Exception {
        final List<Address> addresses = MockNetworkMap.addresses;
        final Tree<List<Address>> list = new Tree<>(addresses.subList(0,1), asList(
                new Tree<>(addresses.subList(1, 2))
        ));

        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).collect(toList()),
                contains(addresses.get(0), addresses.get(1))
        );
    }

    @Test
    public void shouldWorkWithSimpleChoice() throws Exception {
        final List<Address> addresses = MockNetworkMap.addresses;
        final Tree<List<Address>> list = new Tree<>(addresses.subList(0,2), asList(
                new Tree<>(addresses.subList(1, 2))
        ));

        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).collect(toList()),
                contains(addresses.get(1), addresses.get(1))
        );
    }

    @Test
    public void shouldWorkWithTreeFlow() throws Exception {
        final List<Address> addresses = MockNetworkMap.addresses;
        final Tree<List<Address>> list = new Tree<>(addresses.subList(0,2), asList(
                new Tree<>(addresses.subList(1, 2)),
                new Tree<>(addresses.subList(0, 1))
        ));

        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).collect(toList()),
                contains(addresses.get(1), addresses.get(1), addresses.get(0))
        );
    }

    public scala.concurrent.Future<Object> future(Object result) {
        return Future$.MODULE$.successful(result);
    }


    private Snapshot createSnapshot(final Address address) {
        return Snapshot.newBuilder().build();
    }

}