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

import rx.Observable.Operator;
import rx.Subscriber;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    <Out extends Serializable> RemoteObservable<Out> map(final Operator<Out, T> operator);


    /**
     * Submits steps to the cluster as well as subscriber instance. Node hosting a {@code subscriber} becomes a consumer of the processing results.
     *
     * @param subscriber - a an instance of {@link RemoteSubscriber}
     * @return an instance of {@link CompletableFuture} which will be resolved with
     * an instance of {@link RemoteSubscription} if scheduling was successful
     */
    CompletableFuture<? extends RemoteSubscription> subscribe(final RemoteSubscriber<T> subscriber);

    /**
     * Submits steps to the cluster as well as subscriber instance. Node hosting a {@code subscriber} becomes a consumer of the processing results.
     *
     * @param subscriber - a an instance of {@link RemoteSubscriber}
     * @param address    - and address on a node which should host passed {@code subscriber}
     * @return an instance of {@link CompletableFuture} which will be resolved with
     * an instance of {@link RemoteSubscription} if scheduling was successful
     */
    CompletableFuture<? extends RemoteSubscription> subscribe(final RemoteSubscriber<T> subscriber,
                                                              final InetSocketAddress address);

    /**
     * Submits steps to the cluster as well as subscriber instance. Current node becomes a consumer of the processing results.
     *
     * @return an instance of {@link CompletableFuture} which will be resolved with
     * an instance of {@link RemoteSubscription} if scheduling was successful
     */
    CompletableFuture<? extends RemoteSubscription> subscribe();

    /**
     * Submits steps to the cluster as well as subscriber instance. Current node becomes a consumer of the processing results.
     *
     * @param subscriber - an instance of {@link Subscriber} tho receive results
     * @return an instance of {@link CompletableFuture} which will be resolved with
     * an instance of {@link RemoteSubscription} if scheduling was successful
     */
    CompletableFuture<? extends RemoteSubscription> subscribe(final Subscriber<? super T> subscriber);

}
