package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Created by dyma on 03.05.16.
 */
@Lazy
@Component
public class ClusterMap {

    @Autowired
    private NetworkMap networkMap;

    private Map<Address, Map<Address, Map<String, Long>>> map = new HashMap<>();

    public ClusterMap() {
    }

    ClusterMap(final NetworkMap networkMap) {
        this.networkMap = networkMap;
    }

    public void setValuesForNodes(final Map<Address, Map<String, Long>> values) {

        values.entrySet().forEach(entry -> {
            final HashMap<Address, Map<String, Long>> value = new HashMap<>();
            values.entrySet()
                    .forEach(entry2 -> value.put(entry2.getKey(), entry2.getValue()));
            map.put(entry.getKey(), value);
        });

        map.entrySet().stream().forEach(entry -> {
            entry.getValue().entrySet().stream().forEach(entry2 ->
                    entry2.getValue().put(
                            "Network",
                            networkMap.getValue(entry.getKey(), entry2.getKey()).orElse(0L)
                    )
            );
        });
    }

    public Map<String, Long> getValue(final Address from, final Address to) {
        return map.getOrDefault(from, new HashMap<>()).getOrDefault(to, new HashMap<>());
    }

}
