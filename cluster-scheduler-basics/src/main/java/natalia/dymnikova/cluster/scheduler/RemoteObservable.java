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

package natalia.dymnikova.cluster.scheduler;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Mimics {@link rx.Observable} api but for serializable only types
 */
public interface RemoteObservable<T extends Serializable> {

    <Out extends Serializable> RemoteObservable<Out> map(final RemoteOperator<T, Out> operator);

    <Out extends Serializable> RemoteObservable<Out> map(final RemoteOperator<T, Out> operator,
                                                         final InetSocketAddress address);

    <Out extends Serializable> RemoteObservable<Out> map(final RemoteFunction<T, Out> function);

    <Out extends Serializable> RemoteObservable<Out> map(final RemoteFunction<T, Out> function,
                                                         final InetSocketAddress address);

    /**
     * Submits steps to the cluster as well as subscriber instance. Node hosting a {@code subscriber} becomes a consumer of the processing results.
     *
     * @param subscriber - a an instance of {@link RemoteSubscriber}
     * @return an instance of {@link RemoteSubscription}
     */
    CompletableFuture<? extends RemoteSubscription> subscribe(final RemoteSubscriber<T> subscriber);

    CompletableFuture<? extends RemoteSubscription> subscribe(final RemoteSubscriber<T> subscriber,
                                                              final InetSocketAddress address);

    CompletableFuture<? extends RemoteSubscription> subscribe(final Consumer<T> onNext,
                                                              final Runnable onComplete,
                                                              final Consumer<Throwable> onError);

    CompletableFuture<? extends RemoteSubscription> subscribe();
}
