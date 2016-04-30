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

import akka.actor.ActorSelection;
import akka.actor.Address;
import akka.actor.AddressFromURIString;
import natalia.dymnikova.cluster.ActorSystemAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map.Entry;

import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.ActorPaths.computePool;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getStageList;

/**
 */
@Lazy
@Component
public class SetFlowDestinationFactory {

    @Autowired
    private ActorSystemAdapter adapter;

    public List<Entry<ActorSelection, Address>> buildDestinations(final SetFlow flow) {
        return getStageList(flow.getStage()).stream()
                .map(Stage::getAddress)
                .distinct()
                .map(AddressFromURIString::apply)
                .map(address -> new AbstractMap.SimpleEntry<>(adapter.actorSelection(computePool(address)), address))
                .collect(toList());
    }

}
