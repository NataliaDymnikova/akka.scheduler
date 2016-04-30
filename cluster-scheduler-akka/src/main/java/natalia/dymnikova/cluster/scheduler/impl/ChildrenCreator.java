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

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.Pair;
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.SpringAkkaExtensionId;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteMergeOperator;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithFunction;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithSubscriber;
import natalia.dymnikova.util.AutowireHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Local;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Merge;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.NotSet;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getNextStageNumber;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getPreviousStageNumbers;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getStage;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getStageList;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.stageName;

/**
 *
 */
public abstract class ChildrenCreator implements Function<FlowControlActor, List<ActorRef>> {
    private static final Logger log = LoggerFactory.getLogger(ChildrenCreator.class);

    protected List<Stage> stages;
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
        this.stages = getStageList(flow.getStage());
    }

    @Override
    public List<ActorRef> apply(final FlowControlActor flowControlActor) {

        final String selfAddress = cluster.selfAddress().toString();

        final List<ActorRef> activeStages = new ArrayList<>();

        for (final Stage stage : stages) {
            if (selfAddress.equals(stage.getAddress()) && !(stage.getType().equals(Local) || stage.getType().equals(NotSet))) {
                final ActorRef stageRef = flowControlActor.actorOf(
                        stageActorProps(flowControlActor, flow, stage.getId()), stageName(stage.getId())
                );

                activeStages.add(stageRef);
            }
        }

        return activeStages;
    }

    private Props stageActorProps(final FlowControlActor flowControlActor,
                                  final Flow.SetFlow flow,
                                  final int id) {
        final List<ActorPath> prevActor = getPreviousStageNumbers(flow, id).stream()
                .map(num -> remoteStageActorPath(flow, num))
                .collect(toList());
        final Stage stage = getStage(flow, id);

        if (log.isDebugEnabled()) {
            log.debug("Create {} stage in ChildrenCreator", id);
            log.debug("Type: {}", stage.getType());
            log.debug("Previous actor: {}", prevActor);
        }

        // TODO unify how stage actors are created - final stage received no ActorSelection but calculates it on itself
        final Flow.StageType stageType = stage.getType();
        if (stageType == Supplier) {
            final ActorSelection nextActor = flowControlActor.actorSelection(remoteStageActorPath(flow, getNextStageNumber(flow, id)));
            log.debug("Next actor: {}", nextActor);
            final Supplier<?> supplier = autowireHelper.autowire(codec.unpackSupplier(
                    stage.getOperator().toByteArray()
            ));

            return StartStageActor.props(
                    extension,
                    nextActor,
                    flow,
                    supplier
            );
        } else if (stageType == Merge) {
            final ActorSelection nextActor = flowControlActor.actorSelection(remoteStageActorPath(flow, getNextStageNumber(flow, id)));
            log.debug("Next actor: {}", nextActor);
            return MergeStageActor.props(
                    extension,
                    prevActor,
                    nextActor,
                    (RemoteMergeOperator) unpackAndAutowire(getStage(flow, id).getOperator()),
                    flow,
                    createOnCompleteConsumer(stage, flow)
            );
        } else {
            final Optional<ActorSelection> nextActor;
            if (flow.getStage() == stage) {
                flowControlActor.actorOf(StartingJobActor.props(extension, flow), "StartingJobActor");
                nextActor = empty();
            } else {
                nextActor = of(flowControlActor.actorSelection(remoteStageActorPath(flow, getNextStageNumber(flow, id))));
            }
            log.debug("Next actor: {}", nextActor);

            return IntermediateStageActor.props(
                    extension,
                    prevActor.stream().map(flowControlActor::actorSelection).findFirst().get(),
                    nextActor,
                    subscriber -> ((RemoteOperator) unpackAndAutowire(getStage(flow, id).getOperator())).call(subscriber),
                    flow,
                    createOnCompleteConsumer(stage, flow)
            );
        }
    }

    private Remote unpackAndAutowire(final ByteString operatorBytes) {
        assert !operatorBytes.isEmpty() : "ByteString should not be empty";

        final Remote operator = codec.unpackRemote(operatorBytes.toByteArray());

        if (operator instanceof RemoteOperatorWithFunction) {
            return new RemoteOperatorWithFunction<>(autowireHelper.autowire(((RemoteOperatorWithFunction) operator).delegate));
        } else if (operator instanceof RemoteOperatorWithSubscriber) {
            return new RemoteOperatorWithSubscriber<>(autowireHelper.autowire(((RemoteOperatorWithSubscriber) operator).delegate));
        } else {
            return autowireHelper.autowire(operator);
        }
    }

    protected OutSubscriberFactory createOnCompleteConsumer(final Stage stage, final Flow.SetFlow flow) {
        final OutSubscriberFactory onComplete;
        if (flow.getStage() == stage) {
            onComplete = context.getBean(
                    OutSubscriberFactory.class,
                    (BiConsumer<Pair<Optional<ActorSelection>, ActorRef>, ActorRef>) (pair, self) ->
                            pair.second().tell(Flow.Completed.getDefaultInstance(), self)
            );
        } else {
            onComplete = context.getBean(
                    OutSubscriberFactory.class,
                    (BiConsumer<Pair<Optional<ActorSelection>, ActorRef>, ActorRef>) (pair, self) ->
                            pair.first().ifPresent(actor -> actor.tell(Flow.Completed.getDefaultInstance(), self))
            );
        }
        return onComplete;
    }
}
