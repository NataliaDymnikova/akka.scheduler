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

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import natalia.dymnikova.cluster.Actor;
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.ActorLogic;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.cluster.scheduler.RemoteStageException;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.StagesUnready;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.configuration.ConfigValue;
import natalia.dymnikova.util.AutowireHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.Completed;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.Data;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.IsReady;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.More;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.OnStart;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static scala.concurrent.duration.Duration.apply;

/**
 * 
 */
@Actor
public class FinalStageActor extends ActorLogic {
    private static final Logger log = LoggerFactory.getLogger(FinalStageActor.class);

    @ConfigValue("natalia-dymnikova.scheduler.subscriber.on-start-pre-fetch-elements")
    private long elementsCount = 10;

    @ConfigValue("natalia-dymnikova.scheduler.subscriber.ready-check-interval")
    private Duration duration = ofSeconds(5);

    @ConfigValue("natalia-dymnikova.scheduler.subscriber.max-ready-check-retries")
    long countIterations = 6;

    @Autowired
    private Codec codec;

    @Autowired
    private AutowireHelper autowireHelper;

    private ActorSelection prevActor;
    private final SetFlow flow;
    private final Stage stage;

    private ActorRef self;

    private RemoteSubscriber<Serializable> subscriber;

    public static Props props(final AkkaExtension extension,
                              final SetFlow flow) {
        return extension.props(
                FinalStageActor.class,
                flow
        );
    }

    public FinalStageActor(final ActorAdapter adapter,
                           final SetFlow flow) {
        super(adapter);
        this.flow = flow;

        receive(new ReceiveAdapter(ReceiveBuilder
                .match(Data.class, this::handle)
                .match(State.Ok.class, this::handle)
                .match(CheckIfReadyResponses.class, this::handle)
                .match(Completed.class, this::handle)
                .match(State.Error.class, this::handle)
                .build(),
                t -> subscriber.onError(t)
        ));

        stage = flow.getStages(flow.getStagesCount() - 1);

        self = self();
    }

    @Override
    public void preStart() throws Exception {
        prevActor = actorSelection(remoteStageActorPath(flow, flow.getStagesCount() - 2));
        subscriber = autowireHelper.autowire(codec.unpackSubscriber(
                stage.getOperator().toByteArray()
        ));

        notReadyStages = new ArrayList<>();

        final List<Stage> stagesList = flow.getStagesList();
        for (int i = 0, stagesListSize = stagesList.size() - 1; i < stagesListSize; i++) {

            final ActorSelection selection = actorSelection(remoteStageActorPath(flow, i));
            notReadyStages.add(selection);

            selection.tell(IsReady.getDefaultInstance(), self);
        }

        scheduler().scheduleOnce(apply(duration.toMillis(), MILLISECONDS), self, new CheckIfReadyResponses(0));
    }

    private void handle(final State.Error error) throws RemoteStageException {
        throw new RemoteStageException(error.getMessage());
    }

    private List<ActorSelection> notReadyStages;

    public void handle(final State.Ok ok) {
        notReadyStages.removeIf(aSelection -> {
                    log.debug("Sender: {}", sender().path().toStringWithoutAddress());
                    log.debug("Selection: {}", aSelection.pathString());
                    return aSelection.pathString().equals(sender().path().toStringWithoutAddress());
                }
        );

        if (notReadyStages.isEmpty()) {
            prevActor.tell(OnStart.newBuilder().setCount(elementsCount).build(), self);
            subscriber.onStart(n -> prevActor.tell(More.newBuilder().setCount(n).build(), self));
        }
    }

    public void handle(final CheckIfReadyResponses checkIfReadyResponses) throws StagesUnready {
        if (notReadyStages.isEmpty()) {
            return;
        }

        log.debug("Not ready stages: {}", notReadyStages);

        if (checkIfReadyResponses.number >= countIterations) {
            final StagesUnready unready = new StagesUnready(notReadyStages.stream()
                    .map(ActorSelection::toString)
                    .collect(toList()));
            subscriber.onError(unready);
            throw unready;
        }

        for (final ActorSelection stage : notReadyStages) {
            stage.tell(IsReady.getDefaultInstance(), self);
        }

        scheduler().scheduleOnce(
                apply(duration.toMillis(), MILLISECONDS),
                self,
                new CheckIfReadyResponses(checkIfReadyResponses.number + 1)
        );
    }

    public void handle(final Data data) {
        subscriber.onNext(codec.unpack(data.getData().newInput()));
    }

    public void handle(final Completed completed) {
        subscriber.onCompleted();
        parent().tell(completed, self);
    }

    static class CheckIfReadyResponses {
        private final long number;

        public CheckIfReadyResponses(final long number) {
            this.number = number;
        }
    }

}
