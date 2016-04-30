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
import akka.actor.Address;
import akka.cluster.Cluster;
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteFunction;
import natalia.dymnikova.cluster.scheduler.RemoteMergeOperator;
import natalia.dymnikova.cluster.scheduler.RemoteObservable;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.RemoteSubscription;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow.StageType;
import natalia.dymnikova.util.MoreFutures;
import natalia.dymnikova.util.MoreSubscribers;
import natalia.dymnikova.util.MoreSubscribers.SubscriberToCompletableFutureAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observable.Operator;
import rx.Subscriber;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.google.protobuf.ByteString.EMPTY;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Local;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Merge;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Operator;
import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static natalia.dymnikova.util.MoreFutures.allOf;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 *
 */
@Lazy
@Component
@Scope(SCOPE_PROTOTYPE)
public class AkkaBackedRemoteObservable<T extends Serializable> implements RemoteObservable<T> {

    private CompletableFuture<StageContainer> lastStage = completedFuture(null);

    private static final Logger log = LoggerFactory.getLogger(AkkaBackedRemoteObservable.class);

    final private String flowName;

    private Optional<String> parentFlowName;

    @Autowired
    private CreateAndSendSetFlow creatorSetFlow;

    @Autowired
    private NodeSearcher nodeSearcher;

    @Autowired
    private Codec codec;

    @Autowired
    protected Cluster cluster;

    @Autowired
    private ConverterAddresses converterAddresses;

    public AkkaBackedRemoteObservable(final String flowName, final Optional<String> parentFlowName) {
        this.flowName = flowName;
        this.parentFlowName = parentFlowName;
        log.trace("Allocated new flow {}, parent {}", flowName, parentFlowName);
    }

    public RemoteObservable<T> withOnSubscribe(final OnSubscribe<T> onSubscribe) {
        lastStage = completedFuture(new StageContainer(
                wrapAddress(selfAddress()),
                new OnSubscribeWrapper<>(onSubscribe),
                EMPTY,
                Local,
                emptyList()
        ));
        return this;
    }

    public RemoteObservable<T> withSupplierOfObservable(final RemoteSupplier<Observable<T>> supplier) {
        return withSupplierOfObservable(supplier, null);
    }

    public RemoteObservable<T> withSupplierOfObservable(final RemoteSupplier<Observable<T>> supplier,
                                                        final InetSocketAddress address) {
        final ByteString bytes = wrap(codec.pack(supplier));
        lastStage = findComputePool(supplier, ofNullable(address)).thenApply(l -> new StageContainer(
                l,
                supplier,
                bytes,
                StageType.Supplier,
                emptyList()
        ));
        return this;
    }

    public RemoteObservable<T> withSupplier(final RemoteSupplier<T> supplier) {
        return withSupplier(supplier, null);
    }

    public RemoteObservable<T> withSupplier(final RemoteSupplier<T> supplier,
                                            final InetSocketAddress address) {
        final ByteString bytes = wrap(codec.pack(supplier));
        lastStage = findComputePool(supplier, ofNullable(address)).thenApply(l -> new StageContainer(
                l,
                supplier,
                bytes,
                StageType.Supplier,
                emptyList()
        ));
        return this;
    }

    public RemoteObservable<T> withMerge(final RemoteMergeOperator<T> merge,
                                         final Observable<RemoteObservable<T>> observables) {

        final SubscriberToCompletableFutureAdapter<CompletableFuture<StageContainer>> subscriber = new SubscriberToCompletableFutureAdapter<>();

        //noinspection unchecked
        observables.map(obs -> (AkkaBackedRemoteObservable) obs)
                .map(obs -> (CompletableFuture<StageContainer>) obs.lastStage)
                .subscribe(subscriber);

        lastStage = subscriber.future.thenCompose(c -> allOf(c.toArray(new CompletableFuture[c.size()]))).thenApply(c -> new StageContainer(
                wrapAddress(selfAddress()),
                merge,
                wrap(codec.pack(merge)),
                Merge,
                (List<StageContainer>) (Object) c
        ));

        return this;
    }

    @Override
    public <Out extends Serializable> RemoteObservable<Out> map(final RemoteOperator<T, Out> operator) {
        return map(operator, null);
    }

    @Override
    public <Out extends Serializable> RemoteObservable<Out> map(final RemoteOperator<T, Out> operator,
                                                                final InetSocketAddress address) {
        final ByteString bytes = wrap(codec.pack(operator));
        lastStage = lastStage.thenCombine(findComputePool(operator, ofNullable(address)), (child, l) -> new StageContainer(
                l,
                operator,
                bytes,
                Operator,
                singletonList(child)
        ));

        @SuppressWarnings("unchecked")
        final RemoteObservable<Out> next = (RemoteObservable<Out>) this;

        return next;
    }

    @Override
    public <Out extends Serializable> RemoteObservable<Out> map(final RemoteFunction<T, Out> function,
                                                                final InetSocketAddress address) {
        return map(new RemoteOperatorWithFunction<>(function), address);
    }

    @Override
    public <Out extends Serializable> RemoteObservable<Out> map(final RemoteFunction<T, Out> function) {
        return map(new RemoteOperatorWithFunction<>(function));
    }

    @Override
    public <Out extends Serializable> RemoteObservable<Out> map(final Operator<Out, T> operator) {
        final Address address = cluster.selfAddress();

        lastStage = lastStage.thenApply(child -> new StageContainer(
                wrapAddress(createUnresolved(address.host().get(), (int) address.port().get())),
                operator,
                EMPTY,
                Local,
                singletonList(child)
        ));

        @SuppressWarnings("unchecked")
        final RemoteObservable<Out> next = (RemoteObservable<Out>) this;

        return next;
    }

    @Override
    public CompletableFuture<? extends RemoteSubscription> subscribe(final RemoteSubscriber<T> subscriber) {
        return subscribe(subscriber, null);
    }

    @Override
    public CompletableFuture<? extends RemoteSubscription> subscribe(final RemoteSubscriber<T> subscriber,
                                                                     final InetSocketAddress address) {
        final RemoteOperator operator = new RemoteOperatorWithSubscriber<>(subscriber);
        final ByteString bytes = wrap(codec.pack(operator));
        lastStage = lastStage.thenCombine(findComputePool(operator, ofNullable(address)), (child, l) -> new StageContainer(
                l,
                operator,
                bytes,
                Operator,
                singletonList(child)
        ));

        return subscribe();
    }

    @Override
    public CompletableFuture<? extends RemoteSubscription> subscribe(final Subscriber<? super T> s) {
        lastStage = lastStage.thenApply(child -> new StageContainer(
                wrapAddress(selfAddress()),
                s,
                EMPTY,
                Local,
                singletonList(child)
        ));

        return subscribe();
    }

    @Override
    public CompletableFuture<? extends RemoteSubscription> subscribe() {
        return lastStage.thenApply(lStage ->
                creatorSetFlow.sendSetFlow(lStage, flowName, parentFlowName)
        );
    }

    private InetSocketAddress selfAddress() {
        final Address address = cluster.selfAddress();
        return createUnresolved(address.host().get(), (int) address.port().get());
    }

    private CompletableFuture<List<Optional<Address>>> wrapAddressInFuture(final InetSocketAddress address) {
        return completedFuture(wrapAddress(address));
    }

    private List<Optional<Address>> wrapAddress(InetSocketAddress address) {
        return singletonList(of(converterAddresses.toAkkaAddress(address)));
    }

    private CompletableFuture<List<Optional<Address>>> findComputePool(final Remote operator,
                                                                       final Optional<InetSocketAddress> inetSocketAddress) {
        return inetSocketAddress.map(this::wrapAddressInFuture).orElseGet(() ->
                nodeSearcher.search(operator)
        );
    }

    public static abstract class RemoteOperatorImpl<I extends Serializable, O extends Serializable> implements RemoteOperator<I, O>, SelfAware {
        public final Remote delegate;

        protected RemoteOperatorImpl(final Remote delegate) {
            this.delegate = delegate;
        }

        @Override
        public void setSelf(final ActorRef self) {
            if (delegate instanceof SelfAware) {
                ((SelfAware) delegate).setSelf(self);
            }
        }
    }

    static class RemoteOperatorWithFunction<I extends Serializable, O extends Serializable> extends RemoteOperatorImpl<I, O> {
        protected final RemoteFunction<I, O> delegate;

        public RemoteOperatorWithFunction(final RemoteFunction<I, O> delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public Subscriber<? super I> call(final Subscriber<? super O> child) {
            return new Subscriber<I>(child) {

                @Override
                public void onCompleted() {
                    child.onCompleted();
                }

                @Override
                public void onError(final Throwable e) {
                    child.onError(e);
                }

                @Override
                public void onNext(final I i) {
                    try {
                        child.onNext(delegate.apply(i));
                    } catch (final Throwable t) {
                        child.onError(t);
                        throw t;
                    }
                }
            };
        }
    }

    static class RemoteOperatorWithSubscriber<I extends Serializable> extends RemoteOperatorImpl<I, Serializable> {
        protected final RemoteSubscriber<I> delegate;

        protected RemoteOperatorWithSubscriber(final RemoteSubscriber<I> delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public Subscriber<? super I> call(final Subscriber<? super Serializable> child) {
            return new Subscriber<I>(child) {

                @Override
                public void onCompleted() {
                    delegate.onCompleted();
                    child.onCompleted();
                }

                @Override
                public void onError(final Throwable e) {
                    delegate.onError(e);
                }

                @Override
                public void onNext(final I i) {
                    delegate.onNext(i);
                }
            };
        }
    }

    static class OnSubscribeWrapper<T extends Serializable> implements Supplier<Observable<T>> {
        protected final OnSubscribe<T> onSubscribe;

        public OnSubscribeWrapper(final OnSubscribe<T> onSubscribe) {
            this.onSubscribe = onSubscribe;
        }

        @Override
        public Observable<T> get() {
            return Observable.create(onSubscribe);
        }
    }

    public static class StageContainer {
        public final List<Optional<Address>> candidates;
        public final ByteString remoteBytes;
        public final StageType stageType;
        public final Object action;
        public final List<StageContainer> previous;
        public int id;

        public StageContainer(final List<Optional<Address>> candidates,
                              final Object action,
                              final ByteString remoteBytes,
                              final StageType stageType,
                              final List<StageContainer> previous) {
            this.candidates = candidates;
            this.action = action;
            this.remoteBytes = remoteBytes;
            this.stageType = stageType;
            this.previous = previous;
        }

        public List<StageContainer> getStages() {
            final List<StageContainer> stageContainers = new ArrayList<>();
            previous.forEach(stageContainer -> stageContainers.addAll(stageContainer.getStages()));
            stageContainers.add(0, this);
            return stageContainers;
        }
    }

}
