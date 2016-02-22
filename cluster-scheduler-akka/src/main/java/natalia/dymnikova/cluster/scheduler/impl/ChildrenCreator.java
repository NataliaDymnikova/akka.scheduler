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
import akka.cluster.Cluster;
import akka.japi.Pair;
import natalia.dymnikova.cluster.SpringAkkaExtensionId;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.util.AutowireHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import rx.Observable;
import rx.Observable.Operator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.NotSet;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.stageName;

/**
 *
 */
public abstract class ChildrenCreator implements Function<FlowControlActor, List<ActorRef>> {
    private static final Logger log = LoggerFactory.getLogger(ChildrenCreator.class);

    protected Flow.SetFlow flow;

    @Autowired
    protected Cluster cluster;

    @Autowired
    protected SpringAkkaExtensionId.AkkaExtension extension;

    @Autowired
    protected ApplicationContext context;

    @Autowired
    protected Codec codec;

    @Autowired
    private AutowireHelper autowireHelper;

    public ChildrenCreator(final Flow.SetFlow flow) {
        this.flow = flow;
    }

    @Override
    public List<ActorRef> apply(final FlowControlActor flowControlActor) {

        final String selfAddress = cluster.selfAddress().toString();

        final List<ActorRef> activeStages = new ArrayList<>();

        final List<Flow.Stage> stagesList = flow.getStagesList();
        for (int i = 0, stagesListSize = stagesList.size(); i < stagesListSize; i++) {
            final Flow.Stage stage = stagesList.get(i);

            if (selfAddress.equals(stage.getAddress()) && !stage.getType().equals(NotSet)) {
                final ActorRef stageRef = flowControlActor.actorOf(
                        stageActorProps(flowControlActor, flow, i), stageName(i)
                );

                activeStages.add(stageRef);
            }
        }

        return activeStages;
    }

    private Props stageActorProps(final FlowControlActor flowControlActor,
                                  final Flow.SetFlow flow,
                                  final int number) {
        // TODO unify how stage actors are created - final stage received no ActorSelection but calculates it on itself
        if (number == 0) {
            flowControlActor.actorOf(extension.props(StartingJobActor.class, flow), "StartingJobActor");
            return StartStageActor.props(
                    extension,
                    flowControlActor.actorSelection(remoteStageActorPath(flow, number + 1)),
                    flow.getStages(number)
            );
        } else {
            final ActorSelection nextStage;
            if (number == flow.getStagesCount() - 1) {
                nextStage = flowControlActor.actorSelection(remoteStageActorPath(flow, number));
            } else {
                nextStage = flowControlActor.actorSelection(remoteStageActorPath(flow, number + 1));
            }

            log.debug("Create {} stage in ChildrenCreator", number);

            return IntermediateStageActor.props(
                    extension,
                    flowControlActor.actorSelection(remoteStageActorPath(flow, number - 1)),
                    nextStage,
                    (RemoteOperator) codec.unpackAndAutowire(flow.getStages(number).getOperator()),
                    context.getBean(
                            OutSubscriberFactory.class,
                            (BiConsumer<Pair<ActorSelection, ActorRef>, ActorRef>) (pair, self) ->
                                    pair.first().tell(Flow.Completed.getDefaultInstance(), self))
            );
        }
    }
}
