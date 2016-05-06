package natalia.dymnikova.test;

import akka.actor.Address;
import natalia.dymnikova.cluster.scheduler.impl.find.optimal.MembersStates;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static natalia.dymnikova.monitoring.MonitoringClient.Snapshot;

/**
 * Created by dyma on 06.05.16.
 */
@Lazy
@Component
public class EmptyMemberStatesForTest implements MembersStates {
    @Override
    public Snapshot getState(Address address) {
        return Snapshot.getDefaultInstance();
    }
}
