package natalia.dymnikova.cluster.scheduler.impl;

import akka.actor.Address;
import natalia.dymnikova.cluster.scheduler.SetFlowStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Created by Natalia on 07.01.2016.
 */
@Component
public class FindFirstSetFlowStrategy implements SetFlowStrategy {
    @Override
    public List<Optional<Address>> getNodes(final List<List<Address>> versionsList) {
        return versionsList.stream().map(optionals -> optionals.stream().findFirst()).collect(toList());
    }
}
