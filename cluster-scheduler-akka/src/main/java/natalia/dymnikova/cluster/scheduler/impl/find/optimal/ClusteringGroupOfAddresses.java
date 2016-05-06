package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import akka.cluster.Cluster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * Created by dyma on 06.05.16.
 */
@Lazy
@Component
public class ClusteringGroupOfAddresses implements GroupOfAddresses {

    @Autowired
    private Cluster cluster;

    @Autowired
    private NetworkMap networkMap;

    private final Map<Address, Group> groups = new HashMap<>();
    private final Map<Group, Map<Group, Long>> distances = new HashMap<>();

    @Override
    public List<Group> getGroups() {
        return groups.values().stream().distinct().collect(toList());
    }

    @Override
    public Group getGroup(final Address address) {
        if (groups.containsKey(address)) {
            return groups.get(address);
        } else {
            return addNewAddress(address);
        }
    }

    private Group addNewAddress(final Address address) {
        final LinkedList<Address> addresses = new LinkedList<>();
        addresses.add(address);
        final Group newGroup = new Group(addresses);
        groups.put(address, newGroup);

        addNewDistance(newGroup);

        return newGroup;
    }

    private void addNewDistance(final Group newGroup) {
        distances.entrySet().stream().forEach(entry ->
                entry.getValue().put(newGroup, getNewDistance(entry.getKey(), newGroup))
        );

        final HashMap<Group, Long> map = new HashMap<>();
        distances.keySet().forEach(group -> map.put(group, getNewDistance(newGroup, group)));
        distances.put(newGroup, map);
    }

    private long getNewDistance(final Group from, final Group to) {
        return (long) from.getAddress().stream().flatMapToLong(address ->
                to.getAddress().stream().mapToLong(address2 ->
                        networkMap.getValue(address, address2).get()
                )
        ).average().getAsDouble();
    }

    @Override
    public long getDistance(final Group group1, final Group group2) {
        return distances.get(group1).get(group2);
    }

}
