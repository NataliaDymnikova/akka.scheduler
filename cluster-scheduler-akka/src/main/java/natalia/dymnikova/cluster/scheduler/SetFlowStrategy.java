package natalia.dymnikova.cluster.scheduler;

import akka.actor.Address;

import java.util.List;
import java.util.Optional;

/**
 * Created by Natalia on 07.01.2016.
 */
public interface SetFlowStrategy {
    List<Optional<Address>> getNodes(List<List<Address>> versionsList);
}
