package natalia.dymnikova.test;

import akka.actor.Address;
import natalia.dymnikova.cluster.scheduler.impl.find.optimal.GroupOfAddresses;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

/**
 * Created by dyma on 06.05.16.
 */
@Lazy
@Component
@Primary
public class GroupOfAddressesForTests implements GroupOfAddresses {

    public static final List<Group> groups = new ArrayList<>();
    static {
        groups.add(new Group(new ArrayList<>(MockNetworkMap.addresses.subList(0, 5))));
        groups.add(new Group(new ArrayList<>(MockNetworkMap.addresses.subList(5, 8))));
        groups.add(new Group(new ArrayList<>(MockNetworkMap.addresses.subList(8, MockNetworkMap.n))));
    }

    @Override
    public List<Group> getGroups() {
        return groups;
    }

    @Override
    public Group getGroup(final Address address) {
        for (final Group group : groups) {
            if (group.contains(address)) {
                return group;
            }
        }

        groups.get(0).addGroup(address);
        return groups.get(0);
    }

    @Override
    public long getDistance(final Group group1, final Group group2) {
        return abs(groups.indexOf(group1) - groups.indexOf(group2)) * 100;
    }

}
