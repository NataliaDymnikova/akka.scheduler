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

import akka.actor.ActorPath;
import akka.actor.ActorSelection;
import natalia.dymnikova.cluster.ActorSystemAdapter;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.Producer;
import rx.Subscriber;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

import static akka.actor.AddressFromURIString.apply;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static natalia.dymnikova.cluster.ActorPaths.computePool;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessage;
import static natalia.dymnikova.util.MoreFutures.immediateFuture;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static rx.Observable.just;

/**
 * 
 */
@RunWith(MockitoJUnitRunner.class)
public class AkkaBackedRemoteObservableTest {

    @Mock
    private NodeSearcher searcher;

    @Mock
    private Codec codec;

    @Mock
    private ActorSystemAdapter adapter;

    @Mock
    private SetFlowFactory setFlowFactory;

    private final RemoteSupplier<Observable<String>> stage0 = new RemoteSupplier<Observable<String>>() {
        @Override
        public Observable<String> get() {
            return just("a", "b", "c");
        }
    };

    private final RemoteOperator<String, String> stage1 = (RemoteOperator<String, String>) new RemoteOperator<String, String>() {
        @Override
        public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
            return subscriber;
        }
    };

    private final RemoteSubscriber stage9 = new RemoteSubscriberImpl();

    @InjectMocks
    private AkkaBackedRemoteObservable<String> observable = new AkkaBackedRemoteObservable<>("flow");

    private final ActorSelection selection0 = mock(ActorSelection.class, "selection0");
    private final ActorPath path0 = computePool(apply("akka://system@host0:0"));

    private final ActorSelection selection1 = mock(ActorSelection.class, "selection1");
    private final ActorPath path1 = computePool(apply("akka://system@host1:0"));

    private final ActorSelection selection9 = mock(ActorSelection.class, "selection9");
    private final ActorPath path9 = computePool(apply("akka://system@host2:0"));

    @Before
    public void setUp() throws Exception {
        setupSelection(selection0, path0, RemoteSupplier.class);
        setupSelection(selection1, path1, RemoteOperator.class);
        setupSelection(selection9, path9, RemoteSubscriber.class);

        doReturn(flowMessage()).when(setFlowFactory).makeFlow(anyString(), any());

        doReturn(new byte[0]).when(codec).pack(isA(Remote.class));
    }

    public void setupSelection(final ActorSelection s, final ActorPath p, final Class<? extends Remote> c) {
        doReturn(p.toString()).when(s).pathString();
        doReturn(p).when(s).anchorPath();
        doReturn(immediateFuture(singletonList(
                of(p.address())
        ))).when(searcher).search(isA(c));

        doReturn(s).when(adapter).actorSelection(p);
    }

    @Test
    public void shouldSendCheckToComputePoolActorsForSupplier() throws Exception {
        observable.withSupplierOfObservable(stage0);
        verify(searcher, times(1)).search(same(stage0));
    }

    @Test
    public void shouldSendCheckToComputePoolActors() throws Exception {
        observable.map(stage1);

        verify(searcher, times(1)).search(same(stage1));
    }

    @Test
    public void shouldSendCheckToComputePoolActorsForRemoteSubscriber() throws Exception {
        observable.subscribe(stage9, t -> System.out.println(t.getMessage()));

        verify(searcher, times(1)).search(same(stage9));
    }

    @Test
    public void shouldSendSetFlowToComputePoolActorsForRemoteSubscriber() throws Exception {
        observable.subscribe(stage9, t -> System.out.println(t.getMessage()));

        verify(selection9, times(1)).tell(isA(Flow.SetFlow.class), any());
    }

    @Test
    public void shouldCallOnErrorWhenFailsToFindCandidateForRemoteSubscriber() throws Exception {
        doReturn(immediateFuture(asList(
                empty(), empty(), empty()
        ))).when(searcher).search(any());

        doThrow(new NoSuchElementException("No candidate for stage...")).when(setFlowFactory).makeFlow(anyString(), any());

        @SuppressWarnings("unchecked")
        final Consumer<Throwable> throwableConsumer = mock(Consumer.class);
        observable.subscribe(stage9, throwableConsumer);

        verify(throwableConsumer).accept(any());
    }

    @Test
    public void shouldSendFlowOnlyOncePerAddress() throws Exception {
        setupSelection(selection0, path0, RemoteSupplier.class);
        setupSelection(selection0, path0, RemoteOperator.class);
        setupSelection(selection1, path1, RemoteSubscriber.class);

        observable
                .withSupplierOfObservable(stage0)
                .map(stage1)
                .subscribe(stage9, t -> System.out.println(t.getMessage()));

        verify(selection0, times(1)).tell(isA(Flow.SetFlow.class), any());
        verify(selection1, times(1)).tell(isA(Flow.SetFlow.class), any());
    }

    private static class RemoteSubscriberImpl implements RemoteSubscriber {
        @Override
        public void onStart(final Producer producer) {
        }

        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(final Throwable e) {
        }

        @Override
        public void onNext(final Object o) {
        }
    }
}