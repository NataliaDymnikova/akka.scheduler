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
import akka.actor.OneForOneStrategy;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.japi.pf.ReceiveBuilder;
import com.google.protobuf.Message;
import natalia.dymnikova.cluster.Actor;
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.ActorLogic;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Completed;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteFlowControlActorPath;

/**
 * An actor which represents a supervisor for allocated compute resource/slot. It responsibility is to allocate a slot
 * for a computation and react on happening errors.
 * <p>
 * This actor can control several instances of compute slots belonging to same compute flow.
 * <p>
 */
@Actor
public class FlowControlActor extends ActorLogic {
    private static final Logger log = LoggerFactory.getLogger(FlowControlActor.class);

    @Autowired
    private Cluster cluster;

    private List<ActorRef> activeStages = new ArrayList<>();

    private String selfAddress;

    SetFlow flow;

    private Function<FlowControlActor, List<ActorRef>> childrenCreater;

    public FlowControlActor(final ActorAdapter adapter,
                            final SetFlow flow,
                            final Function<FlowControlActor, List<ActorRef>> childrenCreater) {
        super(adapter);
        this.flow = flow;
        this.childrenCreater = childrenCreater;

        receive(ReceiveBuilder
                .match(State.Error.class, this::handle)
                .match(Completed.class, this::handle)
                .match(Terminated.class, this::handle)
                .build()
        );
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(SupervisorStrategy.makeDecider(param -> {

            handle(State.Error.newBuilder()
                    .setMessage(param.getClass().getName() + " " + param.getMessage())
                    .build());

            return SupervisorStrategy.stop();
        }));
    }

    @Override
    public void preStart() throws Exception {
        selfAddress = cluster.selfAddress().toString();

        log.info("Starting {} at {}", flow.getFlowName(), selfAddress);

        childrenCreater.apply(this).stream()
                .peek(this::watch)
                .forEach(activeStages::add);
    }

    public void handle(final Terminated terminated) {
        final boolean removed = activeStages.removeIf(e ->
                e.path().name().equals(terminated.actor().path().name())
        );

        assert removed;

        if (activeStages.isEmpty()) {
            stop(self());
        }
    }

    public void handle(final Completed completed) {
        sendMessageToAll(completed);
    }

    public void handle(final State.Error error) {
        sendMessageToAll(error);

        activeStages.forEach(e -> e.tell(error, self()));
    }

    private void sendMessageToAll(final Message error) {
        flow.getStagesList().stream().filter(this::notSelf).forEach(e ->
                remoteFlowControlActor(e).tell(error, self())
        );
    }

    private ActorSelection remoteFlowControlActor(final Stage e) {
        return actorSelection(remoteFlowControlActorPath(flow, e.getAddress()));
    }

    private boolean notSelf(final Stage stage) {
        return !stage.getAddress().equals(selfAddress);
    }
}
