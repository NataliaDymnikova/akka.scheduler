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
import natalia.dymnikova.cluster.scheduler.RemoteStageException;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.impl.SubscriberWithMore.HandleException;
import natalia.dymnikova.util.AutowireHelper;
import org.springframework.beans.factory.annotation.Autowired;
import rx.Observable;
import rx.Subscription;

import java.io.Serializable;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.IsReady;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.More;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.OnStart;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import static natalia.dymnikova.util.MoreByteStrings.wrap;

/**
 *
 */
@Actor
public class StartStageActor extends ActorLogic {

    private static final AkkaBackedSchedulerThreadCtx ThreadCtx = new AkkaBackedSchedulerThreadCtx();

    @Autowired
    private Codec codec;

    @Autowired
    private AutowireHelper autowireHelper;

    private final ActorSelection nextActor;
    private String flowName;

    private SubscriberWithMore outSubscriber;
    private Optional<Subscription> subscription = empty();

    private final Supplier<?> observableSupplier;

    public static Props props(final SpringAkkaExtensionId.AkkaExtension extension,
                              final ActorSelection nextActor,
                              final SetFlow flow,
                              final Supplier<?> observableSupplier) {
        return extension.props(
                StartStageActor.class,
                nextActor,
                flow,
                observableSupplier
        );
    }

    public StartStageActor(final ActorAdapter adapter,
                           final ActorSelection nextActor,
                           final SetFlow flow,
                           final Supplier<?> observableSupplier) {
        super(adapter);

        this.nextActor = nextActor;
        this.observableSupplier = observableSupplier;
        this.flowName = flow.getFlowName();

        receive(ReceiveBuilder
                .match(More.class, this::handle)
                .match(IsReady.class, this::handle)
                .match(HandleException.class, this::handle)
                .match(OnStart.class, this::handle)
                .match(State.Error.class, this::handle)
                .build());

    }

    private void handle(final HandleException e) {
        throw e;
    }

    @Override
    public void postStop() {
        subscription.ifPresent(Subscription::unsubscribe);
    }

    public void handle(final OnStart onStart) {
        final Observable<Serializable> inObservable;
        final Object o = ThreadCtx.withFlow(flowName, observableSupplier::get);

        if (o instanceof Observable) {
            inObservable = (Observable<Serializable>) o;
        } else {
            inObservable = Observable.just((Serializable) o);
        }

        outSubscriber = new SubscriberWithMore() {
            @Override
            public void onStart() {
                request(onStart.getCount());
            }

            @Override
            public void onCompleted() {
                nextActor.tell(Flow.Completed.getDefaultInstance(), self());
            }

            @Override
            public void onError(final Throwable e) {
                self().tell(new HandleException(e), self());
            }

            @Override
            public void onNext(final Serializable serializable) {
                nextActor.tell(Flow.Data.newBuilder().setData(wrap(codec.packObject(serializable))).build(), self());
            }
        };

        subscription = ThreadCtx.withFlow(flowName, () -> of(inObservable.subscribe(outSubscriber)));
    }

    public void handle(final More more) {
        checkNotNull(outSubscriber, "More Received before OnStart!");
        outSubscriber.more(more.getCount());
    }

    public void handle(final IsReady ready) {
        sender().tell(State.Ok.getDefaultInstance(), self());
    }

    public void handle(final State.Error error) {
        if (outSubscriber == null) {
            ThreadCtx.withFlow(flowName, () -> self().tell(new HandleException(new RemoteStageException(error.getMessage())), self()));
        } else {
            ThreadCtx.withFlow(flowName, () -> outSubscriber.onError(new RemoteStageException(error.getMessage())));
        }
    }

}
