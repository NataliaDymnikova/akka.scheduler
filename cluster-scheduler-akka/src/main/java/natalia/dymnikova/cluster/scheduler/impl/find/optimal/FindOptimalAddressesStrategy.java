// Copyright (c) 2016 Natalia Dymnikova
// Available via the MIT license
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// documentation files (the "Software"), to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
// and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
// TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
// CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
// OR OTHER DEALINGS IN THE SOFTWARE.

package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;
import natalia.dymnikova.cluster.scheduler.impl.GetAddressesStrategy;
import natalia.dymnikova.cluster.scheduler.impl.find.optimal.GroupOfAddresses.Group;
import natalia.dymnikova.monitoring.MonitoringClient.Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.AbstractMap.SimpleEntry;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Created by dyma on 01.05.16.
 */
@Lazy
@Component
public class FindOptimalAddressesStrategy implements GetAddressesStrategy {
    public static final Logger log = LoggerFactory.getLogger(FindOptimalAddressesStrategy.class);

    @Autowired
    private Comparator<List<Map<String, Long>>> comparator;

    @Autowired
    private MembersStates states;

    @Autowired
    private GroupOfAddresses groupOfAddresses;

    @Autowired
    private NetworkMap networkMap;

    @Override
    public List<Optional<Address>> getNodes(final Tree<List<Address>> versionsList) {
        final List<Address> addresses = versionsList.stream().flatMap(Collection::stream).distinct().collect(toList());

        final Map<Address, Snapshot> statesMap = states.getStates(addresses);

        final ClusterMap clusterMap = new ClusterMap(networkMap);
        clusterMap.setValuesForNodes(toMap(statesMap));

        final List<GroupsRouteWithValues> bestGroups = findBestGroups(
                versionsList,
                singletonList(new GroupsRouteWithValues(groupOfAddresses))
        );

        final GroupsRouteWithValues bestGroupRoute = bestGroups.stream()
                .map(group -> new SimpleEntry<>(group, group.getValues().stream().mapToLong(v -> v.orElse(0L)).sum()))
                .min((o1, o2) -> Long.compare(o1.getValue(), o2.getValue()))
                .map(SimpleEntry::getKey).orElse(new GroupsRouteWithValues(groupOfAddresses));

        final Tree<List<Address>> versions = createVersionsFromGroups(
                versionsList,
                bestGroupRoute.getGroups().stream().filter(Optional::isPresent).map(Optional::get).collect(toList())
        );

        return findAllWays(versions, singletonList(new RouteWithValues(clusterMap))).stream()
                .min((o1, o2) -> comparator.compare(
                        o1.getValues().stream().filter(Optional::isPresent).map(Optional::get).collect(toList()),
                        o2.getValues().stream().filter(Optional::isPresent).map(Optional::get).collect(toList()))
                ).map(RouteWithValues::getAddresses)
                .orElse(emptyList());
    }

    private Tree<List<Address>> createVersionsFromGroups(Tree<List<Address>> versionsList,
                                                         List<Group> bestGroups) {
        final List<Address> rootTree = versionsList.getRoot().stream()
                .filter(a -> bestGroups.stream()
                        .filter(g -> g.contains(a))
                        .findAny().isPresent())
                .collect(toList());

        if (bestGroups.isEmpty()) {
            return  new Tree<>(rootTree);
        } else {
            return new Tree<>(
                    rootTree,
                    versionsList.getChildren().stream()
                            .map(l -> createVersionsFromGroups(l, bestGroups.subList(1, bestGroups.size())))
                            .collect(toList())
            );
        }
    }

    private List<GroupsRouteWithValues> findBestGroups(Tree<List<Address>> variants,
                                                       List<GroupsRouteWithValues> routs) {
        if (variants.getChildren().isEmpty()) {
            if (variants.getRoot().isEmpty()) {
                return routs.stream().map(rout -> new GroupsRouteWithValues(rout, null)).collect(toList());
            } else {
                return variants.getRoot().stream()
                        .flatMap(address -> routs.stream().map(rout -> new GroupsRouteWithValues(rout, address)))
                        .collect(toList());
            }
        }

        final List<Tree<List<Address>>> children = variants.getChildren();
        final List<GroupsRouteWithValues> bestWay = new ArrayList<>();
        children.forEach(child -> bestWay.addAll(findBestGroups(child, routs)));

        if (variants.getRoot().isEmpty()) {
            return bestWay.stream().map(rout -> new GroupsRouteWithValues(rout, null)).collect(toList());
        } else {
            return variants.getRoot().stream()
                    .flatMap(address -> bestWay.stream().map(rout -> new GroupsRouteWithValues(address, rout)))
                    .collect(toList());
        }
    }

    private List<RouteWithValues> findAllWays(final Tree<List<Address>> variants,
                                              final List<RouteWithValues> routs) {
        if (variants.getChildren().isEmpty()) {
            if (variants.getRoot().isEmpty()) {
                return routs.stream().map(rout -> new RouteWithValues(rout, null)).collect(toList());
            } else {
                return variants.getRoot().stream()
                        .flatMap(address -> routs.stream().map(rout -> new RouteWithValues(rout, address)))
                        .collect(toList());
            }
        }

        final List<Tree<List<Address>>> children = variants.getChildren();
        final List<RouteWithValues> bestWay = new ArrayList<>();
        children.forEach(child -> bestWay.addAll(findAllWays(child, routs)));

        if (variants.getRoot().isEmpty()) {
            return bestWay.stream().map(rout -> new RouteWithValues(rout, null)).collect(toList());
        } else {
            return variants.getRoot().stream()
                    .flatMap(address -> bestWay.stream().map(rout -> new RouteWithValues(address, rout)))
                    .collect(toList());
        }
    }

    private Map<Address, Map<String,Long>> toMap(final Map<Address, Snapshot> statesMap) {
        final Map<Address,Map<String,Long>> result = new HashMap<>();
        statesMap.entrySet().forEach(state -> result.put(state.getKey(), toMap(state.getValue())));
        return result;
    }

    private Map<String, Long> toMap(final Snapshot value) {
        final Map<String, Long> result = new HashMap<>();
        value.getCategoryList().forEach(category ->
                category.getEntityList().forEach(entity ->
                        entity.getTagsList().forEach(tags -> {
                            tags.getMetricList().forEach(metric -> result.put(
                                    category.getName() + entity.getName() + metric.getName(),
                                    metric.getValueCase().getNumber() == 4 ? metric.getHistogram().getCount() : metric.getCounter())
                            );
                        })
                )
        );
        return result;
    }

}
