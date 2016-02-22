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
import akka.japi.Pair;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import rx.Subscriber;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.BiConsumer;

import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.stageName;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 *
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class LocalChildrenCreater extends ChildrenCreator {
    private static final Logger log = LoggerFactory.getLogger(LocalChildrenCreater.class);


    private LocalSetFlow flow;

    public LocalChildrenCreater(final LocalSetFlow flow) {
        super(excludeLastStage(flow.getFlow()));
        this.flow = flow;

        log.debug("Create LocalChildrenCreator");
    }

    private static SetFlow excludeLastStage(final SetFlow flow) {
        return flow;
    }

    @Override
    public List<ActorRef> apply(final FlowControlActor flowControlActor) {
        final List<ActorRef> children = super.apply(flowControlActor);
        final ActorRef actorRef = allocateLocalFlowActor(flowControlActor);
        if (actorRef != null) {
            log.debug("Create last LocalChildrenCreator");
            children.add(actorRef);
        }
        return children;
    }

    private ActorRef allocateLocalFlowActor(final FlowControlActor flowControlActor) {
        final int number = flow.getFlow().getStagesCount() - 1;
        final String selfAddress = cluster.selfAddress().toString();

        if (!selfAddress.equals(flow.getFlow().getStages(number).getAddress())) {
            return null;
        }

        if (flow.getFlow().getStagesCount() == 1) {
            flowControlActor.actorOf(extension.props(StartingJobActor.class, flow));
        }

        final ActorRef actorRef = flowControlActor.actorOf(IntermediateStageActor.props(
                extension,
                flowControlActor.actorSelection(remoteStageActorPath(flow.getFlow(), number - 1)),
                null,
                subscriber -> new Subscriber<Serializable>() {
                    @Override
                    public void onCompleted() {
                        flow.getOnComplete().run();
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(final Throwable e) {
                        flow.getOnError().accept(e);
                    }

                    @Override
                    public void onNext(final Serializable serializable) {
                        flow.getOnNext().accept(serializable);
                    }
                },
                context.getBean(
                        OutSubscriberFactory.class,
                        (BiConsumer<Pair<ActorSelection, ActorRef>, ActorRef>) (pair, self) ->
                                pair.second().tell(Flow.Completed.getDefaultInstance(), self))
        ), stageName(number));
        return actorRef;
    }
}
