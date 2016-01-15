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

package natalia.dymnikova.cluster.scheduler.impl;

import akka.actor.Address;
import natalia.dymnikova.cluster.scheduler.GetAddressesStrategy;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * Created by Natalia on 13.01.2016.
 */

@Component
public class RoundRobinGetAddressesStrategy implements GetAddressesStrategy {

    private final Set<Address> nodes = new HashSet<>();

    @Override
    public List<Optional<Address>> getNodes(final List<List<Address>> versionsList) {
        nodes.addAll(versionsList.stream().flatMap(Collection::stream).collect(toList()));

        final List<Optional<Address>> result = versionsList.stream()
                .map(optionals -> nodes.stream()
                        .filter(optionals::contains)
                        .findFirst()
                ).collect(toList());
        final List<Address> addressesWithoutEmpty = result.stream()
                .map(opt -> opt.orElse(null))
                .filter(address -> address != null)
                .collect(toList());
        nodes.removeAll(addressesWithoutEmpty);
        nodes.addAll(addressesWithoutEmpty);

        return result;
    }
}
