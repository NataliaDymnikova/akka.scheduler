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

import rx.Observable;
import rx.Observable.OnSubscribe;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;

import static rx.Observable.from;

/**
 *
 */
public interface Scheduler {
    default <T extends Serializable> RemoteObservable<T> empty() {
        // TODO
        return null;
    }

    <T extends Serializable> RemoteObservable<T> create(final OnSubscribe<T> onSubscribe);


    /**
     * Creates {@code RemoteObservable} with an {@code Observable} returned by provided {@code supplier} as a source of values
     *
     * @param supplier - a supplier of a value which will be passed to a subscriber of the observable.
     * @param <T>
     * @return an instance of {@code RemoteObservable}
     */
    <T extends Serializable> RemoteObservable<T> createObservable(final RemoteSupplier<Observable<T>> supplier);

    <T extends Serializable> RemoteObservable<T> createObservable(final RemoteSupplier<Observable<T>> supplier,
                                                                  final InetSocketAddress address);

    /**
     * Creates {@code RemoteObservable} with single value returned by provided {@code supplier}
     *
     * @param supplier - a supplier of a value which will be passed to a subscriber of the observable.
     * @param <T>
     * @return an instance of {@code RemoteObservable}
     */
    <T extends Serializable> RemoteObservable<T> create(final RemoteSupplier<T> supplier);

    <T extends Serializable> RemoteObservable<T> create(final RemoteSupplier<T> supplier,
                                                        final InetSocketAddress address);

    List<Member> getMembers();

    List<Member> getMembersWithRoles(final String... roles);

    <T extends Serializable> RemoteObservable<T> merge(final RemoteMergeOperator<T> merge,
                                                       final Observable<RemoteObservable<T>> observables);

    default <T extends Serializable> RemoteObservable<T> merge(final RemoteMergeOperator<T> merge,
                                                               final List<RemoteObservable<T>> observables) {
        return merge(merge, from(observables));
    }

}
