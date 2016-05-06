package natalia.dymnikova.test;

import akka.actor.Address;
import natalia.dymnikova.cluster.scheduler.impl.find.optimal.NetworkMap;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.lang.Math.abs;
import static java.util.Optional.ofNullable;

/**
 * Created by dyma on 06.05.16.
 */
@Lazy
@Component
public class MockNetworkMap implements NetworkMap {

    public static final int n = 10;
    public static final List<Address> addresses = new ArrayList<>();
    static {
        for (int i = 0; i < n; i++) {
            addresses.add(new Address("akka.tcp", "system", "host:" + i, 0));
        }
    }

    public final Map<Address, Map<Address, Long>> map;

    public MockNetworkMap() {
        map = new HashMap<>();

        for (int i = 0; i < n; i++) {
            final Map<Address, Long> addr = new HashMap<>();
            for (int j = 0; j < n; j++) {
                addr.put(addresses.get(j), abs(j - i) * 10L);
            }
            map.put(addresses.get(i), addr);
        }
    }

    @Override
    public Optional<Long> getValue(final Address from, final Address to) {
        return ofNullable(map.getOrDefault(from, null)).map(m -> m.getOrDefault(to, null));
    }
}
