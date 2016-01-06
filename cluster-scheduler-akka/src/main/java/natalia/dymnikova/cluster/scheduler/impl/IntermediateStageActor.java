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
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Completed;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Data;
import natalia.dymnikova.cluster.scheduler.akka.Flow.IsReady;
import natalia.dymnikova.cluster.scheduler.akka.Flow.More;
import natalia.dymnikova.cluster.scheduler.akka.Flow.OnStart;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorImpl;
import natalia.dymnikova.cluster.scheduler.impl.SubscriberWithMore.HandleException;
import natalia.dymnikova.util.AutowireHelper;
import org.springframework.beans.factory.annotation.Autowired;
import rx.Observable;
import rx.Observable.Operator;
import rx.Producer;
import rx.Subscriber;

import java.io.Serializable;

/**
 * 
 */
@Actor
public class IntermediateStageActor extends ActorLogic {

    @Autowired
    private Codec codec;

    @Autowired
    private SubscribeFactory sFactory;

    @Autowired
    private AutowireHelper autowireHelper;

    final private ActorSelection nextActor;
    final private ActorSelection prevActor;
    private Stage stage;

    private Subscriber<? super Serializable> inSubscriber;

    private SubscriberWithMore outSubscriber;

    private final SetFlow flow;

    public static Props props(final AkkaExtension extension,
                              final ActorSelection prevActor,
                              final ActorSelection nextActor,
                              final SetFlow flow,
                              final Stage stage) {
        return extension.props(
                IntermediateStageActor.class,
                prevActor,
                nextActor,
                flow,
                stage
        );
    }

    public IntermediateStageActor(final ActorAdapter adapter,
                                  final ActorSelection prevActor,
                                  final ActorSelection nextActor,
                                  final SetFlow flow,
                                  final Stage stage) {
        super(adapter);

        this.nextActor = nextActor;
        this.prevActor = prevActor;
        this.stage = stage;

        receive(new ReceiveAdapter(ReceiveBuilder
                .match(Data.class, this::handle)
                .match(Completed.class, this::handle)
                .match(More.class, this::handle)
                .match(HandleException.class, this::handle)
                .match(IsReady.class, this::handle)
                .match(OnStart.class, this::handle)
                .build(),
                t -> {
                    if (!(t instanceof HandleException)) {
                        inSubscriber.onError(t);
                    }
                }
        ));

        this.flow = flow;
    }

    public void handle(final HandleException e) {
        throw e;
    }

    @Override
    public void preStart() throws Exception {
        outSubscriber = new SubscriberWithMore(
                nextActor, parent(), self(), codec, 0
        ) {
            @Override
            public void onStart() {
            }
        };

        inSubscriber = unpackOperator().call(outSubscriber);
    }

    private Operator<Serializable, Serializable> unpackOperator() {
        final Operator<Serializable, Serializable> operator = codec.unpackOperator(
                stage.getOperator().toByteArray()
        );

        if (operator instanceof RemoteOperatorImpl) {
            return new RemoteOperatorImpl<>(autowireHelper.autowire(((RemoteOperatorImpl) operator).delegate));
        } else {
            return autowireHelper.autowire(operator);
        }
    }

    public void handle(final OnStart onStart) {

        outSubscriber.more(onStart.getCount());
        inSubscriber.onStart();

        inSubscriber.setProducer(new Producer() {
            private final Producer onStart = n -> prevActor.tell(OnStart.newBuilder().setCount(n).build(), self());
            private final Producer more = n -> prevActor.tell(More.newBuilder().setCount(n).build(), self());

            private Producer currentState = onStart;

            @Override
            public void request(final long n) {
                currentState.request(n);
                currentState = more;
            }
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
        inSubscriber.onNext(unpack);
    }

    public void handle(final Completed completed) {
        inSubscriber.onCompleted();
    }

    public void handle(final State.Error error) {
        inSubscriber.onError(new RemoteStageException(error.getMessage()));
    }
}
