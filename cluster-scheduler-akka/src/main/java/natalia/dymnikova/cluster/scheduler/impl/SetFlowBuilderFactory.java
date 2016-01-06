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
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.StageContainer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import static natalia.dymnikova.util.MoreFutures.getUncheckedNow;

/**
 * 
 */
@Component
public class SetFlowBuilderFactory {

    public SetFlow makeFlow(final String flowName, final List<StageContainer> resolvedStages) {
        final SetFlow.Builder flow = SetFlow.newBuilder()
                .setFlowName(flowName);

        for (final StageContainer stageContainer : resolvedStages) {
            final Optional<Address> first = getUncheckedNow(stageContainer.candidates)
                    .stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();

            first.map(address -> flow.addStages(Stage.newBuilder()
                    .setOperator(stageContainer.remoteBytes)
                    .setAddress(address.toString())
                    .build())
            ).orElseThrow(() ->
                    new NoSuchElementException("No candidate for stage " + stageContainer.remote)
            );
        }

        return flow.build();
    }
}
