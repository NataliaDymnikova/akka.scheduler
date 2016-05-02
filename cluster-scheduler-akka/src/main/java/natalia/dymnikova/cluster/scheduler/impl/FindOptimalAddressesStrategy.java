package natalia.dymnikova.cluster.scheduler.impl;

import akka.actor.Address;
import natalia.dymnikova.cluster.ActorPaths;
import natalia.dymnikova.cluster.ActorSystemAdapter;
import natalia.dymnikova.cluster.scheduler.akka.Flow.MemberState.GetMemberState;
import natalia.dymnikova.cluster.scheduler.akka.Flow.MemberState.MembersStates;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static akka.util.Timeout.apply;
import static java.time.Duration.ofMinutes;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
    private Comparator<Map<String, Long>> comparator;

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
                                return Optional.of(new SimpleEntry<>(actorPath.address(), createMap((MembersStates) o)));
                            } else {
                                return Optional.empty();
                            }
                        }))
                .toArray(CompletableFuture[]::new);

        try {
            return allOf(futures).thenApply(list -> {
                final List<Map<Address, Map<String, Long>>> variants = createListOfAddressAndMemberStates(list, versionsList);

                return findBestWay(variants, new HashMap<>()).getKey().stream().map(Optional::ofNullable).collect(toList());
            }).get(timeout.toMillis(), MILLISECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw unchecked(e);
        }
    }

    private Entry<List<Address>, Map<String, Long>> findBestWay(
            final List<Map<Address, Map<String, Long>>> variants,
            final Map<String, Long> values
    ) {
        if (variants.size() == 1) {
            return variants.get(0).entrySet().stream()
                    .min((o1, o2) -> comparator.compare(o1.getValue(), o2.getValue()))
                    .map(entry -> {
                        final List<Address> addresses = new ArrayList<>();
                        addresses.add(entry.getKey());
                        return new SimpleEntry<>(addresses, add(values, entry.getValue()));
                    })
                    .orElse(new SimpleEntry<>(emptyList(), values));
        }

        final List<Map<Address, Map<String, Long>>> subList = variants.subList(1, variants.size());

        return variants.get(0).entrySet().stream()
                .map(entry -> {
                    final Entry<List<Address>, Map<String, Long>> bestWay = findBestWay(subList, add(values, entry.getValue()));
                    bestWay.getKey().add(entry.getKey());
                    return bestWay;
                })
                .min((o1, o2) -> comparator.compare(o1.getValue(), o2.getValue()))
                .orElse(new SimpleEntry<>(emptyList(), values));
    }

    private Map<String, Long> add(final Map<String, Long> values, final Map<String, Long> currentValue) {
        final HashMap<String, Long> result = new HashMap<>();
        currentValue.entrySet().forEach(entry -> result.put(
                entry.getKey(),
                entry.getValue() + values.getOrDefault(entry.getKey(), 0L)
        ));
        return result;
    }

    private List<Map<Address, Map<String, Long>>> createListOfAddressAndMemberStates(
            final List<Optional<Entry<Address, Map<String, Long>>>> list,
            final List<List<Address>> versionsList
    ) {
        final Map<Address, Map<String, Long>> map = createMap(list);
        final List<Map<Address, Map<String, Long>>> result = new ArrayList<>(versionsList.size());

        versionsList.forEach(addresses -> {
            final HashMap<Address, Map<String, Long>> entries = new HashMap<>();
            addresses.forEach(address -> entries.put(address, map.get(address)));
            result.add(entries);
        });

        return result;
    }

    private Map<String, Long> createMap(final MembersStates states) {
        final Map<String, Long> map = new HashMap<>();
        states.getStateList().forEach(state -> map.put(state.getName(), state.getValue()));
        return map;
    }

    private Map<Address, Map<String, Long>> createMap(final List<Optional<Entry<Address, Map<String, Long>>>> list) {
        final Map<Address, Map<String, Long>> map = new HashMap<>();
        list.forEach(entry -> entry.ifPresent(e -> map.put(e.getKey(), e.getValue())));
        return map;
    }
}
