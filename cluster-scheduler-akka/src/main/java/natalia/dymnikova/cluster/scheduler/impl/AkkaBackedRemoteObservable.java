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
import akka.actor.Address;
import akka.cluster.Cluster;
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.ActorPaths;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteFunction;
import natalia.dymnikova.cluster.scheduler.RemoteObservable;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.RemoteSubscription;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow.StageType;
import natalia.dymnikova.configuration.ConfigValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Subscriber;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static akka.actor.AddressFromURIString.apply;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.ActorPaths.computePool;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.NotSet;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Operator;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
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

    private static final Logger log = LoggerFactory.getLogger(AkkaBackedRemoteObservable.class);

    final private List<StageContainer> stages = new ArrayList<>(3);

    final private String flowName;

    @Autowired
    private NodeSearcher nodeSearcher;

    @Autowired
    private Codec codec;

    @Autowired
    protected Cluster cluster;

    @Autowired
    private SetFlowFactory setFlowFactory;

    @Autowired
    private SetFlowDestinationFactory setFlowDestinationFactory;

    @Autowired
    private ConverterAddresses converterAddresses;

    public AkkaBackedRemoteObservable(final String flowName) {
        this.flowName = flowName;
    }

    public RemoteObservable<T> withSupplierOfObservable(final RemoteSupplier<Observable<T>> supplier) {
        return withSupplierOfObservable(supplier, null);
    }

    public RemoteObservable<T> withSupplierOfObservable(final RemoteSupplier<Observable<T>> supplier,
                                                        final InetSocketAddress address) {
        final ByteString bytes = wrap(codec.pack(supplier));
        stages.add(new StageContainer(
                findComputePool(supplier, ofNullable(address)),
                supplier,
                bytes,
                Supplier
        ));
        return this;
    }

    public RemoteObservable<T> withSupplier(final RemoteSupplier<T> supplier) {
        return withSupplier(supplier, null);
    }

    public RemoteObservable<T> withSupplier(final RemoteSupplier<T> supplier,
                                            final InetSocketAddress address) {
        final ByteString bytes = wrap(codec.pack(supplier));
        stages.add(new StageContainer(
                findComputePool(supplier, ofNullable(address)),
                supplier,
                bytes,
                Supplier
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
        stages.add(new StageContainer(
                findComputePool(operator, ofNullable(address)),
                operator,
                bytes,
                Operator
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
    public CompletableFuture<? extends RemoteSubscription> subscribe(final RemoteSubscriber<T> subscriber) {
        return subscribe(subscriber, null);
    }

    @Override
    public CompletableFuture<RemoteSubscription> subscribe(final RemoteSubscriber<T> subscriber,
                                                           final InetSocketAddress address) {
        final RemoteOperator operator = new RemoteOperatorWithSubscriber<>(subscriber);
        final ByteString bytes = wrap(codec.pack(operator));
        stages.add(new StageContainer(
                findComputePool(operator, ofNullable(address)),
                operator,
                bytes,
                Operator
        ));

        return sendSetFlow((s, flow) -> s.tell(flow, null));
    }

    @Override
    public CompletableFuture<? extends RemoteSubscription> subscribe(
            final Consumer<T> onNext,
            final Runnable onComplete,
            final Consumer<Throwable> onError
    ) {

        final Address address = cluster.selfAddress();
        final RemoteOperator<Serializable, Serializable> operator = (RemoteOperator<Serializable, Serializable>) s -> s;

        stages.add(new StageContainer(
                findComputePool(
                        operator,
                        of(createUnresolved(address.host().get(), (int) address.port().get()))
                ),
                operator,
                ByteString.EMPTY,
                NotSet
        ));
        return sendSetFlow((selection, flow) ->
                selection.tell(new LocalSetFlow<>(flow, onNext, onComplete, onError), null)
        );
    }

    @Override
    public CompletableFuture<? extends RemoteSubscription> subscribe() {
        return sendSetFlow((s, flow) -> s.tell(flow, null));
    }

    private CompletableFuture<List<Optional<Address>>> findComputePool(final Remote operator,
                                                                       final Optional<InetSocketAddress> inetSocketAddress) {
        return inetSocketAddress.map(address ->
                completedFuture(singletonList(of(converterAddresses.toAkkaAddress(address))))
        ).orElseGet(() ->
                nodeSearcher.search(operator)
        );
    }

    public CompletableFuture<RemoteSubscription> sendSetFlow(final BiConsumer<ActorSelection, SetFlow> doWithLast) {
        final CompletableFuture<SetFlow> voidCompletableFuture = resolveCandidates()
                .thenApply(selections -> {
                    log.debug("Resolved {} candidates for flow {}", selections.size(), flowName);

                    final SetFlow flow = setFlowFactory.makeFlow(
                            flowName, stages
                    );

                    final List<Entry<ActorSelection, Address>> actorSelections = setFlowDestinationFactory.buildDestinations(flow);
                    final String lastAddress = flow.getStages(flow.getStagesCount() - 1).getAddress();

                    actorSelections.forEach(s -> {
                        log.warn("Last address: {}, Current address: {}", lastAddress, s.getValue());
                        if(!lastAddress.equals(s.getValue().toString())) {
                            s.getKey().tell(flow, null);
                        } else {
                            doWithLast.accept(s.getKey(), flow);
                        }
                    });
                    return flow;
                });


        final CompletableFuture<RemoteSubscription> remoteSubscriptionFuture = voidCompletableFuture
                .thenApply(flow ->
                        new RemoteSubscriptionImpl(flow.getStagesList().stream()
                                .map(stage -> stage.getAddress()).collect(toList())
                        ));

        return remoteSubscriptionFuture;
    }

    public CompletableFuture<List<List<Optional<ActorSelection>>>> resolveCandidates() {
        log.debug("Resolving {} candidates for flow {}", stages.size(), flowName);

        @SuppressWarnings("unchecked")
        final CompletableFuture<List<Optional<ActorSelection>>>[] objects = stages
                .stream()
                .map(s -> s.candidates)
                .toArray(CompletableFuture[]::new);

        return allOf(objects);
    }

    static abstract class RemoteOperatorImpl<I extends Serializable, O extends Serializable> implements RemoteOperator<I, O>, SelfAware {
        final Remote delegate;

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

    public static class StageContainer {
        public final CompletableFuture<List<Optional<Address>>> candidates;
        public final Remote remote;
        public final ByteString remoteBytes;
        public final StageType stageType;

        public StageContainer(final CompletableFuture<List<Optional<Address>>> candidates,
                              final Remote remote,
                              final ByteString remoteBytes,
                              final StageType stageType) {
            this.candidates = candidates;
            this.remote = remote;
            this.remoteBytes = remoteBytes;
            this.stageType = stageType;
        }
    }
}
