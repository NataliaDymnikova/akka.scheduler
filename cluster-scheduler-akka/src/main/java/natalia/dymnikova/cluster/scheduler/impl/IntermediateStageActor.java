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
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.cluster.scheduler.RemoteStageException;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Completed;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Data;
import natalia.dymnikova.cluster.scheduler.akka.Flow.IsReady;
import natalia.dymnikova.cluster.scheduler.akka.Flow.More;
import natalia.dymnikova.cluster.scheduler.akka.Flow.OnStart;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import natalia.dymnikova.cluster.scheduler.impl.SubscriberWithMore.HandleException;
import org.springframework.beans.factory.annotation.Autowired;
import rx.Observable.Operator;
import rx.Subscriber;

import java.io.Serializable;
import java.util.Optional;

/**
 *
 */
@Actor
public class IntermediateStageActor extends ActorLogic {

    private static final AkkaBackedSchedulerThreadCtx ThreadCtx = new AkkaBackedSchedulerThreadCtx();

    @Autowired
    private Codec codec;

    final private Optional<ActorSelection> nextActor;
    final private ActorSelection prevActor;

    private final Operator<Serializable, Serializable> operator;

    private final String flowName;

    private final OutSubscriberFactory outSubscriberFactory;

    private Subscriber<? super Serializable> inSubscriber;

    private SubscriberWithMore outSubscriber;

    public static Props props(final AkkaExtension extension,
                              final ActorSelection prevActor,
                              final Optional<ActorSelection> nextActor,
                              final Operator<Serializable, Serializable> operator,
                              final SetFlow flow,
                              final OutSubscriberFactory outSubscriberFactory) {
        return extension.props(
                IntermediateStageActor.class,
                prevActor,
                nextActor,
                operator,
                flow,
                outSubscriberFactory
        );
    }

    public IntermediateStageActor(final ActorAdapter adapter,
                                  final ActorSelection prevActor,
                                  final Optional<ActorSelection> nextActor,
                                  final Operator<Serializable, Serializable> operator,
                                  final SetFlow flow,
                                  final OutSubscriberFactory outSubscriberFactory) {
        super(adapter);

        this.nextActor = nextActor;
        this.prevActor = prevActor;
        this.operator = operator;
        this.flowName = flow.getFlowName();
        this.outSubscriberFactory = outSubscriberFactory;

        receive(new ReceiveAdapter(ReceiveBuilder
                .match(IsReady.class, this::handle)
                .match(OnStart.class, this::handle)
                .match(Data.class, this::handle)
                .match(Completed.class, this::handle)
                .match(More.class, this::handle)
                .match(HandleException.class, this::handle)
                .match(State.Error.class, this::handle)
                .build(),
                t -> {
                    if (!(t instanceof HandleException)) {
                        inSubscriber.onError(t);
                    }
                }
        ));
    }

    public void handle(final HandleException e) {
        throw e;
    }

    @Override
    public void preStart() throws Exception {
        outSubscriber = outSubscriberFactory.getOutSubscriber(nextActor, parent(), self(), 0);

        if (operator instanceof SelfAware) {
            ((SelfAware) operator).setSelf(self());
        }

        inSubscriber = ThreadCtx.withFlow(flowName, () -> operator.call(outSubscriber));
    }

    public void handle(final OnStart onStart) {
        outSubscriber.more(onStart.getCount());

        ThreadCtx.withFlow(flowName, () -> {
            inSubscriber.onStart();

            inSubscriber.setProducer(new ProducerForStartAndMore(prevActor, self()));
        });
    }

    public void handle(final IsReady isReady) {
        sender().tell(State.Ok.getDefaultInstance(), self());
    }

    public void handle(final More more) {
        outSubscriber.more(more.getCount());
    }

    public void handle(final Data p) {
        final Serializable unpack = codec.unpack(p.getData().newInput());
        ThreadCtx.withFlow(flowName, () -> inSubscriber.onNext(unpack));
    }

    public void handle(final Completed completed) {
        ThreadCtx.withFlow(flowName, () -> inSubscriber.onCompleted());
    }

    public void handle(final State.Error error) {
        ThreadCtx.withFlow(flowName, () -> inSubscriber.onError(new RemoteStageException(error.getMessage())));
    }
}
