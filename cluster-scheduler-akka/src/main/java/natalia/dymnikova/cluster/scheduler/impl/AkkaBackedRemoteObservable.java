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
import akka.actor.Address;
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.ActorSystemAdapter;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteFunction;
import natalia.dymnikova.cluster.scheduler.RemoteObservable;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.RemoteSubscription;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Subscriber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static akka.actor.AddressFromURIString.apply;
import static natalia.dymnikova.cluster.ActorPaths.computePool;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static natalia.dymnikova.util.MoreFutures.allOf;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * 
 */
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
    private ActorSystemAdapter adapter;

    @Autowired
    private SetFlowBuilderFactory setFlowBuilderFactory;

    public AkkaBackedRemoteObservable(final String flowName) {
        this.flowName = flowName;
    }

    public RemoteObservable<T> withSupplierOfObservable(final RemoteSupplier<Observable<T>> supplier) {
        final ByteString bytes = wrap(codec.pack(supplier));
        stages.add(new StageContainer(
                findComputePool(supplier),
                supplier,
                bytes
        ));
        return this;
    }

    public RemoteObservable<T> withSupplier(final RemoteSupplier<T> supplier) {
        final ByteString bytes = wrap(codec.pack(supplier));
        stages.add(new StageContainer(
                findComputePool(supplier),
                supplier,
                bytes
        ));
        return this;
    }

    @Override
    public <Out extends Serializable> RemoteObservable<Out> map(final RemoteOperator<T, Out> operator) {
        final ByteString bytes = wrap(codec.pack(operator));
        stages.add(new StageContainer(
                findComputePool(operator),
                operator,
                bytes
        ));

        @SuppressWarnings("unchecked")
        final RemoteObservable<Out> next = (RemoteObservable<Out>) this;

        return next;
    }

    private CompletableFuture<List<Optional<Address>>> findComputePool(final Remote operator) {
        return nodeSearcher.search(operator);
    }

    @Override
    public <Out extends Serializable> RemoteObservable<Out> map(final RemoteFunction<T, Out> function) {
        return map(new RemoteOperatorImpl<>(function));
    }

    public RemoteSubscription sendSetFlow(final Consumer<Throwable> onError) {
        resolveCandidates().thenAccept(selections -> {
            log.debug("Resolved {} candidates for flow {}", selections.size(), flowName);

            final SetFlow flow = setFlowBuilderFactory.makeFlow(
                    flowName, stages
            );

            flow.getStagesList().stream()
                    .map(s -> s.getAddress())
                    .distinct()
                    .forEach(address -> {
                        adapter.actorSelection(computePool(apply(
                                address
                        ))).tell(flow, null);
                    });
        }).exceptionally(t -> {
            log.error(t.getMessage(), t);

            if (t instanceof CompletionException) {
                onError.accept(t.getCause());
            } else {
                onError.accept(t);
            }
            return null;
        });

        return new RemoteSubscriptionImpl();
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

    @Override
    public RemoteSubscription subscribe(final RemoteSubscriber subscriber, final Consumer<Throwable> onError) {
        final ByteString bytes = wrap(codec.pack(subscriber));
        stages.add(new StageContainer(
                findComputePool(subscriber),
                subscriber,
                bytes
        ));

        sendSetFlow(onError);

        return new RemoteSubscriptionImpl();
    }

    static class RemoteOperatorImpl<I extends Serializable, O extends Serializable> implements RemoteOperator<I, O> {
        final RemoteFunction<I, O> delegate;

        public <Out extends Serializable> RemoteOperatorImpl(final RemoteFunction<I, O> delegate) {
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

    public static class StageContainer {
        public final CompletableFuture<List<Optional<Address>>> candidates;
        public final Remote remote;
        public final ByteString remoteBytes;

        public StageContainer(final CompletableFuture<List<Optional<Address>>> candidates,
                              final Remote remote,
                              final ByteString remoteBytes) {
            this.candidates = candidates;
            this.remote = remote;
            this.remoteBytes = remoteBytes;
        }
    }
}
