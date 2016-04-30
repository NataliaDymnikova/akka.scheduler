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
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import natalia.dymnikova.cluster.Actor;
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.ActorLogic;
import natalia.dymnikova.cluster.SpringAkkaExtensionId;
import natalia.dymnikova.cluster.scheduler.StagesUnready;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.configuration.ConfigValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getStageList;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static scala.concurrent.duration.Duration.apply;

/**
 *
 */
@Actor
public class StartingJobActor extends ActorLogic {
    private static final Logger log = LoggerFactory.getLogger(StartingJobActor.class);

    private List<ActorSelection> notReadyStages;

    @ConfigValue("natalia-dymnikova.scheduler.subscriber.on-start-pre-fetch-elements")
    private long elementsCount = 10;

    @ConfigValue("natalia-dymnikova.scheduler.subscriber.ready-check-interval")
    private Duration duration = ofSeconds(5);

    @ConfigValue("natalia-dymnikova.scheduler.subscriber.max-ready-check-retries")
    long countIterations = 6;

    private SetFlow flow;
    private ActorSelection prevActor;

    public static Props props(final SpringAkkaExtensionId.AkkaExtension extension,
                              final SetFlow flow) {
        return extension.props(
                StartingJobActor.class,
                flow
        );
    }

    public StartingJobActor(final ActorAdapter adapter,
                            final SetFlow flow) {
        super(adapter);
        this.flow = flow;

        receive(ReceiveBuilder
                .match(Flow.State.Ok.class, this::handle)
                .match(CheckIfReadyResponses.class, this::handle)
                .build());
    }

    @Override
    public void preStart() throws Exception {
        final List<Flow.Stage> stagesList = getStageList(flow.getStage());
        prevActor = actorSelection(remoteStageActorPath(flow, flow.getStage().getId()));

        notReadyStages = new ArrayList<>();

        for (final Flow.Stage stage : stagesList) {
            final ActorSelection selection = actorSelection(remoteStageActorPath(flow, stage.getId()));
            notReadyStages.add(selection);

            selection.tell(Flow.IsReady.getDefaultInstance(), self());
        }

        scheduler().scheduleOnce(apply(duration.toMillis(), MILLISECONDS), self(), new CheckIfReadyResponses(0));
    }

    public void handle(final Flow.State.Ok ok) {
        notReadyStages.removeIf(aSelection ->
                aSelection.pathString().equals(sender().path().toStringWithoutAddress())
        );

        if (notReadyStages.isEmpty()) {
            prevActor.tell(Flow.OnStart.newBuilder().setCount(elementsCount).build(), self());
        }
    }

    public void handle(final CheckIfReadyResponses checkIfReadyResponses) throws StagesUnready {
        if (notReadyStages.isEmpty()) {
            return;
        }

        log.debug("Not ready stages: {}", notReadyStages);

        if (checkIfReadyResponses.number >= countIterations) {
            throw new StagesUnready(notReadyStages.stream()
                    .map(ActorSelection::toString)
                    .collect(toList()));
        }

        for (final ActorSelection stage : notReadyStages) {
            stage.tell(Flow.IsReady.getDefaultInstance(), self());
        }

        scheduler().scheduleOnce(
                apply(duration.toMillis(), MILLISECONDS),
                self(),
                new CheckIfReadyResponses(checkIfReadyResponses.number + 1)
        );
    }

    static class CheckIfReadyResponses {
        private final long number;

        public CheckIfReadyResponses(final long number) {
            this.number = number;
        }
    }
}
