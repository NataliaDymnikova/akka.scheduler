package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * Created by dyma on 03.05.16.
 */
public class RouteWithValues {

    private List<Optional<Entry<Address, Map<String, Long>>>> route;
    private final ClusterMap clusterMap;

    public RouteWithValues(final ClusterMap clusterMap) {
        route = new ArrayList<>();
        this.clusterMap = clusterMap;
    }

    public RouteWithValues(final RouteWithValues previous, final Address next) {
        this.clusterMap = previous.clusterMap;
        route = previous.route;
        setNextPoint(next);
    }

    public void setNextPoint(final Address address) {
        final Optional<Address> prev;
        if (route.isEmpty()) {
            prev = ofNullable(address);
        } else {
            prev = route.get(route.size() - 1).map(Entry::getKey);
        }

        route.add(prev.map(a -> new SimpleEntry<>(address, clusterMap.getValue(a, address))));
    }

    public List<Optional<Address>> getAddresses() {
        return route.stream().map(o -> o.map(Entry::getKey)).collect(toList());
    }
    public List<Optional<Map<String, Long>>> getValues() {
        return route.stream().map(o -> o.map(Entry::getValue)).collect(toList());
    }
}
