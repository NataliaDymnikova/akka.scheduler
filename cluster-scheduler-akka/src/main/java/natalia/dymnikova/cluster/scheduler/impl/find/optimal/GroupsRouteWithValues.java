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
import natalia.dymnikova.cluster.scheduler.impl.find.optimal.GroupOfAddresses.Group;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * Created by dyma on 03.05.16.
 */
public class GroupsRouteWithValues {

    private List<Optional<Entry<Group, Long>>> route;
    private final GroupOfAddresses groupOfAddresses;

    public GroupsRouteWithValues(final GroupOfAddresses groupOfAddresses) {
        route = new ArrayList<>();
        this.groupOfAddresses = groupOfAddresses;
    }

    public GroupsRouteWithValues(final GroupsRouteWithValues previous, final Address next) {
        this(previous, previous.groupOfAddresses.getGroup(next));
    }

    public GroupsRouteWithValues(final Address previous, final GroupsRouteWithValues next) {
        this.groupOfAddresses = next.groupOfAddresses;
        route = new ArrayList<>();
        setNextPoint(groupOfAddresses.getGroup(previous));
        next.route.forEach(route::add);
    }

    public GroupsRouteWithValues(final GroupsRouteWithValues previous, final Group next) {
        this.groupOfAddresses = previous.groupOfAddresses;
        route = new ArrayList<>(previous.route);
        setNextPoint(next);
    }

    public GroupsRouteWithValues(final GroupsRouteWithValues route) {
        this(route.groupOfAddresses);
        this.route.addAll(route.route);
    }

    public void setNextPoint(final Group group) {
        final Optional<Group> prev;
        if (route.isEmpty()) {
            prev = ofNullable(group);
        } else {
            prev = route.get(route.size() - 1).map(Entry::getKey);
        }

        route.add(prev.map(a -> new SimpleEntry<>(group, groupOfAddresses.getDistance(group, a))));
    }

    public List<Optional<Group>> getGroups() {
        return route.stream().map(o -> o.map(Entry::getKey)).collect(toList());
    }
    public List<Optional<Long>> getValues() {
        return route.stream().map(o -> o.map(Entry::getValue)).collect(toList());
    }
    public GroupOfAddresses getGroupOfAddresses() {
        return groupOfAddresses;
    }

    public void setNextRoute(final GroupsRouteWithValues next) {
        next.route.forEach(op -> {
            if (op.isPresent()) {
                setNextPoint(op.get().getKey());
            }
        });
    }
}
