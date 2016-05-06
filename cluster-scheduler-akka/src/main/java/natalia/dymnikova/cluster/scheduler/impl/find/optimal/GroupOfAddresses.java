package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;

import java.util.List;

/**
 * Created by dyma on 06.05.16.
 */
public interface GroupOfAddresses {
    List<List<Address>> getGroups();
    List<Address> getGroup(final Address address);
}
