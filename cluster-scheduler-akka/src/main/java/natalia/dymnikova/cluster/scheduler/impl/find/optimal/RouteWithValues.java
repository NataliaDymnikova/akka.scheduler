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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * Created by dyma on 03.05.16.
 */
public class RouteWithValues {

    private List<Optional<Entry<Address, Map<String, Long>>>> route;
    private final ClusterMap clusterMap;

    public RouteWithValues(final ClusterMap clusterMap) {
        route = new ArrayList<>();
        this.clusterMap = clusterMap;
    }

    public RouteWithValues(final RouteWithValues previous, final Address next) {
        this.clusterMap = previous.clusterMap;
        route = previous.route;
        setNextPoint(next);
    }

    public RouteWithValues(final Address previous, final RouteWithValues next) {
        this.clusterMap = next.clusterMap;
        route = new ArrayList<>();
        setNextPoint(previous);
        next.route.forEach(route::add);
    }

    public void setNextPoint(final Address address) {
        final Optional<Address> prev;
        if (route.isEmpty()) {
            prev = ofNullable(address);
        } else {
            prev = route.get(route.size() - 1).map(Entry::getKey);
        }

        route.add(prev.map(a -> new SimpleEntry<>(address, clusterMap.getValue(a, address))));
    }

    public List<Optional<Address>> getAddresses() {
        return route.stream().map(o -> o.map(Entry::getKey)).collect(toList());
    }
    public List<Optional<Map<String, Long>>> getValues() {
        return route.stream().map(o -> o.map(Entry::getValue)).collect(toList());
    }
}
