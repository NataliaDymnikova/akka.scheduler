package natalia.dymnikova.test;

import akka.actor.Address;
import natalia.dymnikova.cluster.scheduler.impl.find.optimal.MembersStates;
import natalia.dymnikova.monitoring.MonitoringClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static natalia.dymnikova.monitoring.MonitoringClient.Snapshot;

/**
 * Created by dyma on 06.05.16.
 */
@Lazy
@Component
public class MemberStatesForTest implements MembersStates {
    @Override
    public Snapshot getStates(final Address address) {
        return Snapshot.getDefaultInstance();
    }

    @Override
    public Map<Address, Snapshot> getStates(final List<Address> addresses) {
        final Map<Address, Snapshot> map = new HashMap<>();
        addresses.forEach(address -> map.put(address, Snapshot.getDefaultInstance()));
        return map;
    }
}
