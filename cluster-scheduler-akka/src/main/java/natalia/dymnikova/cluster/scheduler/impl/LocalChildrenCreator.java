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
import akka.cluster.Cluster;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.OnSubscribeWrapper;
import natalia.dymnikova.util.AutowireHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import rx.Observable.Operator;
import rx.Producer;
import rx.Subscriber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getNextStageNumber;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getPreviousStageNumbers;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getStage;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.stageName;
import static natalia.dymnikova.util.MoreThrowables.unchecked;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 *
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class LocalChildrenCreator extends ChildrenCreator {
    private static final Logger log = LoggerFactory.getLogger(LocalChildrenCreator.class);

    @Autowired
    protected Cluster cluster;

    @Autowired
    private AutowireHelper autowireHelper;

    private LocalSetFlow localFlow;

    public LocalChildrenCreator(final LocalSetFlow flow) {
        super(excludeLastStage(flow.getFlow()));
        this.localFlow = flow;

        log.debug("Create LocalChildrenCreator");
    }

    private static SetFlow excludeLastStage(final SetFlow flow) {
        return flow;
    }

    @Override
    public List<ActorRef> apply(final FlowControlActor flowControlActor) {
        final List<ActorRef> children = super.apply(flowControlActor);
        final List<ActorRef> actorRef = allocateLocalFlowActor(flowControlActor);
        if (actorRef != null) {
            log.debug("Create children LocalChildrenCreator");
            children.addAll(actorRef);
        }
        return children;
    }

    private List<ActorRef> allocateLocalFlowActor(final FlowControlActor flowControlActor) {
        final List<Entry<Integer, Object>> actions = localFlow.getActions();
        final SetFlow flow = localFlow.getFlow();

        final List<ActorRef> refs = new ArrayList<>();

        actions.forEach(entry -> {
            final int id = entry.getKey();
            final Object action = entry.getValue();
            final Flow.Stage stage = getStage(flow, id);

            if (log.isDebugEnabled()) {
                log.debug("Create {} stage in ChildrenCreator", id);
                log.debug("Type: {}", stage.getType());
                log.debug("Previous actor: {}", getPreviousStageNumbers(flow, id));
            }
            if (action instanceof OnSubscribeWrapper) {
                //noinspection unchecked
                final ActorSelection nextActor = flowControlActor.actorSelection(remoteStageActorPath(flow, getNextStageNumber(flow, id)));
                log.debug("Next actor: {}", nextActor);
                refs.add(flowControlActor.actorOf(StartStageActor.props(
                        extension,
                        nextActor,
                        flow,
                        new OnSubscribeWrapper(autowireHelper.autowire(((OnSubscribeWrapper)action).onSubscribe))
                ), stageName(id)));
            } else {
                final Optional<ActorSelection> nextActor;
                if (flow.getStage() == stage) {
                    flowControlActor.actorOf(StartingJobActor.props(extension, flow), "StartingJobActor");
                    nextActor = empty();
                } else {
                    nextActor = of(flowControlActor.actorSelection(remoteStageActorPath(flow, getNextStageNumber(flow, id))));
                }

                refs.add(flowControlActor.actorOf(IntermediateStageActor.props(
                        extension,
                        flowControlActor.actorSelection(remoteStageActorPath(flow, getPreviousStageNumbers(flow, id).get(0))),
                        nextActor,
                        asOperator(
                                action
                        ),
                        flow,
                        createOnCompleteConsumer(stage, flow)
                ), stageName(id)));
            }
        });

        return refs;
    }

    private Operator<Serializable, Serializable> asOperator(final Object action) {
        final Operator<Serializable, Serializable> operator;
        if (action instanceof Operator) {
            //noinspection unchecked
            operator = (Operator) autowireHelper.autowire(action);
        } else if (action instanceof Subscriber) {
            //noinspection unchecked
            final Subscriber<Serializable> subscriber =
                    (Subscriber<Serializable>) autowireHelper.autowire(action);

            operator = asOperator(subscriber);
        } else {
            throw unchecked("Can't resolve action: {}", action.getClass().getInterfaces());
        }
        return operator;
    }

    private Operator<Serializable, Serializable> asOperator(final Subscriber<Serializable> subscriber) {
        return subs -> new Subscriber<Serializable>(subs) {
            @Override
            public void setProducer(final Producer p) {
                subscriber.setProducer(p);
            }

            @Override
            public void onStart() {
                subscriber.onStart();
                subs.onStart();
            }

            @Override
            public void onCompleted() {
                subscriber.onCompleted();
                subs.onCompleted();
            }

            @Override
            public void onError(final Throwable e) {
                subscriber.onError(e);
                subs.onError(e);
            }

            @Override
            public void onNext(final Serializable serializable) {
                subscriber.onNext(serializable);
            }
        };
    }
}
