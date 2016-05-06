package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import natalia.dymnikova.monitoring.MonitoringClient.Snapshot;

import java.util.List;
import java.util.Map;

/**
 * Created by dyma on 06.05.16.
 */
public interface MembersStates {

    Snapshot getStates(final Address address);
    Map<Address, Snapshot> getStates(final List<Address> addresses);
}
