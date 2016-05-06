package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import natalia.dymnikova.monitoring.MonitoringClient.Snapshot;

/**
 * Created by dyma on 06.05.16.
 */
public interface MembersStates {
    Snapshot getState(final Address address);
}
