package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import natalia.dymnikova.monitoring.MonitoringClient.Snapshot;
import natalia.dymnikova.test.ComparatorForTests;
import natalia.dymnikova.test.GroupOfAddressesForTests;
import natalia.dymnikova.test.MemberStatesForTest;
import natalia.dymnikova.test.MockNetworkMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import scala.concurrent.Future$;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

/**
 * Created by dyma on 01.05.16.
 */
@RunWith(MockitoJUnitRunner.class)
public class FindOptimalAddressesStrategyTest {

    @Spy
    private Comparator<List<Map<String, Long>>> comparator = new ComparatorForTests();

    @Spy
    private MembersStates states = new MemberStatesForTest();

    @Spy
    private NetworkMap networkMap = new MockNetworkMap();

    @Spy
    private GroupOfAddresses groupOfAddresses = new GroupOfAddressesForTests();


    @InjectMocks
    private FindOptimalAddressesStrategy strategy;


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

    @Test
    public void shouldWorkWithAddressNotInStorage() throws Exception {
        final Tree<List<Address>> list = new Tree<>(asList(Address.apply("other", "address")));

        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).collect(toList()),
                contains(Address.apply("other", "address"))
        );
    }

    @Test
    public void shouldNotReturnAddressFromDifferentGroupsIfHasInOneGroup() throws Exception {
        final List<Address> addresses = MockNetworkMap.addresses;
        final Tree<List<Address>> list = new Tree<>(addresses.subList(0,2), asList(
                new Tree<>(addresses.subList(0, 10))
        ));

        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).collect(toList()),
                contains(addresses.get(0), addresses.get(0))
        );
    }

    @Test
    public void shouldReturnAddressFromDifferentGroups() throws Exception {
        final List<Address> addresses = MockNetworkMap.addresses;
        final Tree<List<Address>> list = new Tree<>(addresses.subList(0,2), asList(
                new Tree<>(addresses.subList(5, 10))
        ));

        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).collect(toList()),
                contains(addresses.get(1), addresses.get(5))
        );
    }

    @Test
    public void shouldWorkCorrect() throws Exception {
        final List<Address> addresses = MockNetworkMap.addresses;
        final Tree<List<Address>> list = new Tree<>(addresses.subList(3,5), asList(
                new Tree<>(addresses.subList(0, 1)),
                new Tree<>(addresses.subList(2, 3)),
                new Tree<>(addresses.subList(5, 10))
        ));

        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).collect(toList()),
                contains(addresses.get(3), addresses.get(0), addresses.get(2), addresses.get(5))
        );
    }

    @Test
    public void shouldWorkCorrectInDifficultTreeOfAddresses() throws Exception {
        final List<Address> addresses = MockNetworkMap.addresses;
        final Tree<List<Address>> list = new Tree<>(addresses.subList(3,5), asList(
                new Tree<>(addresses.subList(0, 1), singletonList(new Tree<>(addresses.subList(0, 1)))),
                new Tree<>(addresses.subList(2, 3)),
                new Tree<>(addresses.subList(4, 10), singletonList(new Tree<>(addresses.subList(9, 10))))
        ));

        final List<Optional<Address>> nodes = strategy.getNodes(list);
        assertThat(
                nodes.stream().map(Optional::get).collect(toList()),
                contains(addresses.get(3), addresses.get(0), addresses.get(0), addresses.get(2), addresses.get(4), addresses.get(9))
        );
    }

    public scala.concurrent.Future<Object> future(Object result) {
        return Future$.MODULE$.successful(result);
    }


    private Snapshot createSnapshot(final Address address) {
        return Snapshot.newBuilder().build();
    }

}