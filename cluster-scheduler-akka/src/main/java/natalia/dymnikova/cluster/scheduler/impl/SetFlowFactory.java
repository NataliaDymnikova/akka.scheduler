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
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.StageContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Merge;
import static natalia.dymnikova.util.MoreFutures.getUncheckedNow;

/**
 *
 */
@Lazy
@Component
public class SetFlowFactory {

    @Autowired
    private GetAddressStrategyFactory strategyFactory;

    @Autowired
    private FlowMerger flowMerger;

    public SetFlow makeFlow(final String flowName,
                            final Optional<String> parentFlowName,
                            final List<StageContainer> resolvedStages) {
        final GetAddressesStrategy strategy = strategyFactory.getAddressStrategy();
        final SetFlow.Builder flow = SetFlow.newBuilder()
                .setFlowName(flowName);
        parentFlowName.ifPresent(flow::setParentFlowName);

        final List<Optional<Address>> addresses = strategy.getNodes(
                resolvedStages.stream()
                        .map(stageContainer -> stageContainer.candidates.stream()
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(toList()))
                        .collect(toList())
        );


        flow.setStage(makeStages(resolvedStages, addresses, 0));
        return flow.build();
    }

    private Stage makeStages(final List<StageContainer> resolvedStages,
                             final List<Optional<Address>> addresses,
                             final int i) {
        final StageContainer stageContainer = resolvedStages.get(i);
        final Optional<Address> currAddress = addresses.get(i);
        final List<Stage> children = stageContainer.previous.stream()
                .map(stage -> makeStages(resolvedStages, addresses, stage.id))
                .collect(toList());
        return currAddress.map(address -> {
            if (stageContainer.stageType == Merge) {
                return flowMerger.createMergeStages(
                        children,
                        stageContainer,
                        previousStageContainerAddress(resolvedStages, stageContainer, addresses)
                );
            }

            return Stage.newBuilder()
                    .setOperator(stageContainer.remoteBytes)
                    .setAddress(currAddress.get().toString())
                    .setType(stageContainer.stageType)
                    .setId(stageContainer.id)
                    .addAllStages(children)
                    .build();

        }).orElseThrow(() -> new NoSuchElementException("No candidate for stage " + stageContainer.action));
    }

    private Optional<Address> previousStageContainerAddress(final List<StageContainer> resolvedStages, final StageContainer stageContainer, final List<Optional<Address>> address) {
        return address.get(resolvedStages.indexOf(resolvedStages.stream()
                .filter(stage -> stage.previous.contains(stageContainer))
                .findFirst().get()));
    }
}
