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

import akka.cluster.Cluster;
import natalia.dymnikova.cluster.scheduler.Member;
import natalia.dymnikova.cluster.scheduler.RemoteMergeOperator;
import natalia.dymnikova.cluster.scheduler.RemoteObservable;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Observable.OnSubscribe;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

import static com.google.protobuf.ByteString.EMPTY;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Merge;
import static scala.collection.JavaConversions.asJavaCollection;

/**
 *
 */
@Lazy
@Component
public class AkkaBackedScheduler implements Scheduler {

    private static final AkkaBackedSchedulerThreadCtx ThreadCtx = new AkkaBackedSchedulerThreadCtx();

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Cluster cluster;

    @Override
    public <T extends Serializable> RemoteObservable<T> create(final OnSubscribe<T> onSubscribe) {
        @SuppressWarnings("unchecked")
        final AkkaBackedRemoteObservable<T> bean = context.getBean(
                AkkaBackedRemoteObservable.class,
                makeFlowName(""),
                ThreadCtx.getCurrentFlowName()
        );

        return bean.withOnSubscribe(onSubscribe);
    }

    @Override
    public <T extends Serializable> RemoteObservable<T> createObservable(final RemoteSupplier<Observable<T>> supplier) {
        @SuppressWarnings("unchecked")
        final AkkaBackedRemoteObservable<T> bean = context.getBean(
                AkkaBackedRemoteObservable.class,
                makeFlowName(supplier.getClass()),
                ThreadCtx.getCurrentFlowName()
        );

        return bean.withSupplierOfObservable(supplier);
    }

    @Override
    public <T extends Serializable> RemoteObservable<T> createObservable(final RemoteSupplier<Observable<T>> supplier, final InetSocketAddress address) {

        @SuppressWarnings("unchecked")
        final AkkaBackedRemoteObservable<T> bean = context.getBean(
                AkkaBackedRemoteObservable.class,
                makeFlowName(supplier.getClass()),
                ThreadCtx.getCurrentFlowName()
        );

        return bean.withSupplierOfObservable(supplier, address);
    }

    private <T extends Serializable> String makeFlowName(final Class<?> supplierClass) {
        String descriptor = supplierClass.getSimpleName();
        if (descriptor.endsWith("Supplier")) {
            descriptor = descriptor.substring(0, descriptor.length() - "Supplier".length());
        }

        return makeFlowName(descriptor);
    }

    private String makeFlowName(String descriptor) {
        final UUID uuid = UUID.randomUUID();
        return format("%s%016x%016x", descriptor, uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    @Override
    public <T extends Serializable> RemoteObservable<T> create(final RemoteSupplier<T> supplier) {
        @SuppressWarnings("unchecked")
        final AkkaBackedRemoteObservable<T> bean = context.getBean(
                AkkaBackedRemoteObservable.class,
                makeFlowName(supplier.getClass()),
                ThreadCtx.getCurrentFlowName()
        );

        return bean.withSupplier(supplier);
    }

    @Override
    public <T extends Serializable> RemoteObservable<T> create(final RemoteSupplier<T> supplier, final InetSocketAddress address) {
        @SuppressWarnings("unchecked")
        final AkkaBackedRemoteObservable<T> bean = context.getBean(
                AkkaBackedRemoteObservable.class,
                makeFlowName(supplier.getClass()),
                ThreadCtx.getCurrentFlowName()
        );

        return bean.withSupplier(supplier, address);
    }

    @Override
    public List<Member> getMembers() {
        return asJavaCollection(cluster.readView().members()).stream()
                .map(AkkaMember::new)
                .collect(toList());
    }

    @Override
    public List<Member> getMembersWithRoles(final String... roles) {
        return asJavaCollection(cluster.readView().members()).stream()
                .filter(member -> member.getRoles().containsAll(asList(roles)))
                .map(AkkaMember::new)
                .collect(toList());
    }

    @Override
    public <T extends Serializable> RemoteObservable<T> merge(final RemoteMergeOperator<T> merge,
                                                              final Observable<RemoteObservable<T>> observables) {
        @SuppressWarnings("unchecked")
        final AkkaBackedRemoteObservable<T> bean = context.getBean(
                AkkaBackedRemoteObservable.class,
                makeFlowName(merge.getClass()),
                ThreadCtx.getCurrentFlowName()
        );

        return bean.withMerge(merge, observables);
    }
}
