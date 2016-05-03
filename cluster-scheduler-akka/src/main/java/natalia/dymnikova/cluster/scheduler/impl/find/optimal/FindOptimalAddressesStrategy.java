package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import natalia.dymnikova.cluster.ActorPaths;
import natalia.dymnikova.cluster.ActorSystemAdapter;
import natalia.dymnikova.cluster.scheduler.akka.Flow.MemberState.GetMemberState;
import natalia.dymnikova.cluster.scheduler.akka.Flow.MemberState.MembersStates;
import natalia.dymnikova.cluster.scheduler.impl.GetAddressesStrategy;
import natalia.dymnikova.configuration.ConfigValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static akka.util.Timeout.apply;
import static java.time.Duration.ofMinutes;
import static java.util.Arrays.asList;
import static java.util.Arrays.fill;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.util.ScalaToJava.toJava;
import static natalia.dymnikova.util.MoreFutures.allOf;
import static natalia.dymnikova.util.MoreThrowables.unchecked;

/**
 * Created by dyma on 01.05.16.
 */
@Lazy
@Component
public class FindOptimalAddressesStrategy implements GetAddressesStrategy {
    public static final Logger log = LoggerFactory.getLogger(FindOptimalAddressesStrategy.class);

    @Autowired
    private ActorSystemAdapter adapter;

    @Autowired
    private Comparator<List<Map<String, Long>>> comparator;

    @Autowired
    private ClusterMap clusterMap;

    @ConfigValue("natalia-dymnikova.scheduler.get-address-strategy.waiting-time")
    private Duration timeout = ofMinutes(5);

    @Override
    public List<Optional<Address>> getNodes(List<List<Address>> versionsList) {
        final List<Address> addresses = versionsList.stream().flatMap(Collection::stream).collect(toList());
        final GetMemberState msg = GetMemberState.getDefaultInstance();

        @SuppressWarnings("unchecked")
        final CompletableFuture<Optional<Entry<Address, Map<String, Long>>>>[] futures = addresses.stream()
                .distinct()
                .map(ActorPaths::computePool)
                .map(actorPath -> toJava(adapter.ask(adapter.actorSelection(actorPath), msg,
                        apply(timeout.getNano(), NANOSECONDS)))
                        .thenApply(o -> {
                            log.debug("actor: {} answer: {}", actorPath, o.getClass().getName());
                            if (o instanceof MembersStates) {
                                return of(new SimpleEntry<>(actorPath.address(), createMap((MembersStates) o)));
                            } else {
                                return empty();
                            }
                        }))
                .toArray(CompletableFuture[]::new);

        try {

            return allOf(futures).thenApply(list -> {
                clusterMap.setValuesForNodes(list);

                return findBestWay(versionsList, singletonList(new RouteWithValues(clusterMap))).stream()
                        .min((o1, o2) -> comparator.compare(
                                o1.getValues().stream().filter(Optional::isPresent).map(Optional::get).collect(toList()),
                                o2.getValues().stream().filter(Optional::isPresent).map(Optional::get).collect(toList()))
                        ).map(RouteWithValues::getAddresses)
                        .orElseGet(() -> singletonList(empty()));
            }).get(timeout.toMillis(), MILLISECONDS);

        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw unchecked(e);
        }
    }

    private List<RouteWithValues> findBestWay(
            final List<List<Address>> variants,
            final List<RouteWithValues> routs
    ) {
        if (variants.size() == 1) {
            if (variants.get(0).isEmpty()) {
                return routs.stream().map(rout -> new RouteWithValues(rout, null)).collect(toList());
            } else {
                return variants.get(0).stream()
                        .flatMap(address -> routs.stream().map(rout -> new RouteWithValues(rout, address)))
                        .collect(toList());
            }
        }

        final List<List<Address>> subList = variants.subList(1, variants.size());
        final List<RouteWithValues> bestWay = findBestWay(subList, routs);

        if (variants.get(0).isEmpty()) {
            return bestWay.stream().map(rout -> new RouteWithValues(rout, null)).collect(toList());
        } else {
            return variants.get(0).stream()
                    .flatMap(address -> bestWay.stream().map(rout -> new RouteWithValues(rout, address)))
                    .collect(toList());
        }
    }

    private Map<String, Long> createMap(final MembersStates states) {
        final Map<String, Long> map = new HashMap<>();
        states.getStateList().forEach(state -> map.put(state.getName(), state.getValue()));
        return map;
    }

}
