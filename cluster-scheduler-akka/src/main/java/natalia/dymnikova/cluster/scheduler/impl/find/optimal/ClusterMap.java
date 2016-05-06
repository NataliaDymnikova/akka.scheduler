package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dyma on 03.05.16.
 */
public class ClusterMap {

    private final NetworkMap networkMap;

    private Map<Address, Map<Address, Map<String, Long>>> map = new HashMap<>();

    public ClusterMap(final NetworkMap networkMap, final Map<Address, Map<String, Long>> values) {
        this.networkMap = networkMap;

        values.entrySet().forEach(entry -> {
            final Map<Address, Map<String, Long>> value = new HashMap<>();
            values.entrySet()
                    .forEach(entry2 -> value.put(entry2.getKey(), new HashMap<>(entry2.getValue())));
            map.put(entry.getKey(), value);
        });

        map.entrySet().stream().forEach(entry ->
                entry.getValue().entrySet().stream().forEach(entry2 ->
                        entry2.getValue().putIfAbsent(
                                "Network",
                                networkMap.getValue(entry.getKey(), entry2.getKey()).orElse(0L)
                        )
                )
        );
    }

    public Map<String, Long> getValue(final Address from, final Address to) {
        return map.getOrDefault(from, new HashMap<>()).getOrDefault(to, new HashMap<>());
    }

}
