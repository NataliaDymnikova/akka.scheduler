package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Created by dyma on 03.05.16.
 */
@Lazy
@Component
public class NetworkMap {
    private final Map<Address, Map<Address, Long>> map = new HashMap<>();

    public NetworkMap() {
        // create map
    }

    public Optional<Long> getValue(final Address from, final Address to) {
        return ofNullable(map.getOrDefault(from, null)).map(m -> m.getOrDefault(to, null));
    }
}
