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
import akka.cluster.Cluster;
import natalia.dymnikova.cluster.scheduler.RemoteSubscription;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.StageContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Local;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getStageList;
import static natalia.dymnikova.util.MoreFutures.allOf;

/**
 *
 */
@Lazy
@Component
public class CreateAndSendSetFlow {
    private static final Logger log = LoggerFactory.getLogger(CreateAndSendSetFlow.class);


    @Autowired
    private SetFlowFactory setFlowFactory;

    @Autowired
    private SetFlowDestinationFactory setFlowDestinationFactory;

    @Autowired
    private Cluster cluster;

    public RemoteSubscription sendSetFlow(final StageContainer lastStage,
                                          final String flowName,
                                          final Optional<String> parentFlowName) {
        final List<StageContainer> stages = lastStage.getStages();
        makeId(stages);

        log.debug("Resolved {} candidates for flow {}", stages, flowName);

        final SetFlow flow = setFlowFactory.makeFlow(
                flowName, parentFlowName, stages
        );

        final List<Map.Entry<ActorSelection, Address>> actorSelections =
                setFlowDestinationFactory.buildDestinations(flow);

        final Address selfAddress = cluster.selfAddress();

        actorSelections.stream()
                .filter(entry -> entry.getValue().equals(selfAddress))
                .map(Map.Entry::getKey)
                .findFirst()
                .map(selection -> new AbstractMap.SimpleEntry<>(selection, createLocalSetFlow(stages, flow)))
                .ifPresent(e -> e.getKey().tell(e.getValue(), null));

        actorSelections.forEach(s -> {
            if (!selfAddress.equals(s.getValue())) {
                s.getKey().tell(flow, null);
            }
        });

        return new RemoteSubscriptionImpl(getStageList(flow.getStage())
                .stream()
                .map(stage -> stage.getAddress())
                .collect(toList()), flow);
    }

    private void makeId(final List<StageContainer> stages) {
        int i = 0;
        for (final StageContainer stage : stages) {
            stage.id = i++;
        }
    }

    private CompletableFuture<List<List<Optional<ActorSelection>>>> resolveCandidates(final List<StageContainer> stages, final String flowName) {
        log.debug("Resolving {} candidates for flow {}", stages.size(), flowName);

        @SuppressWarnings("unchecked")
        final CompletableFuture<List<Optional<ActorSelection>>>[] objects = stages
                .stream()
                .map(s -> s.candidates)
                .toArray(CompletableFuture[]::new);

        return allOf(objects);
    }

    private LocalSetFlow createLocalSetFlow(final List<StageContainer> stageContainers, final SetFlow flow) {
        final List<Map.Entry<Integer, Object>> actions = new ArrayList<>();
        final List<Stage> stageList = getStageList(flow.getStage());
        final List<Stage> localStages = stageList.stream()
                .filter(stage -> stage.getType().equals(Local))
                .collect(toList());
        final List<StageContainer> localStageContainers = stageContainers.stream()
                .filter(stage -> stage.stageType.equals(Local))
                .collect(toList());

        for (final StageContainer localStage : localStageContainers) {
            actions.add(new AbstractMap.SimpleEntry<>(localStage.id, localStage.action));
        }

        return new LocalSetFlow(flow, actions);
    }
}
