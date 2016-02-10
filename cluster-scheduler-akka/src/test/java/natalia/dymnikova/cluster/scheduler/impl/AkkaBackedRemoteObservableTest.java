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
import akka.actor.Address;
import akka.cluster.Cluster;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.RemoteSubscription;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import rx.Observable;
import rx.Producer;
import rx.Subscriber;

import java.applet.AppletContext;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static akka.actor.AddressFromURIString.apply;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static natalia.dymnikova.cluster.ActorPaths.computePool;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Operator;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
import static natalia.dymnikova.util.MoreFutures.immediateFuture;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static rx.Observable.just;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class AkkaBackedRemoteObservableTest {

    @Mock
    private NodeSearcher searcher;

    @Mock
    private Cluster cluster;

    @Mock
    private Codec codec;

    @Mock
    private SetFlowDestinationFactory flowDestinationFactory;

    @Mock
    private GetAddressStrategyFactory addressStrategyFactory;

    @InjectMocks
    private SetFlowFactory setFlowFactory = spy(new SetFlowFactory());

    @Spy
    private ConverterAddresses converterAddresses = new ConverterAddresses();

    private Map<ActorSelection, Address> flowDestinations = new HashMap<>();

    private final RemoteSupplier<Observable<String>> stage0 = new RemoteSupplier<Observable<String>>() {
        @Override
        public Observable<String> get() {
            return just("a", "b", "c");
        }
    };

    private final RemoteOperator<String, String> stage1 = new TestRemoteOperatorImpl();

    private final RemoteSubscriber stage9 = new RemoteSubscriberImpl();

    @InjectMocks
    private AkkaBackedRemoteObservable<String> observable = new AkkaBackedRemoteObservable<>("flow");
    private final ActorSelection selection0 = mock(ActorSelection.class, "selection0");
    private final ActorPath path0 = computePool(apply("akka.tcp://system@host0:0"));

    private final ActorSelection selection1 = mock(ActorSelection.class, "selection1");
    private final ActorPath path1 = computePool(apply("akka.tcp://system@host1:0"));

    private final ActorSelection selection9 = mock(ActorSelection.class, "selection9");
    private final ActorPath path9 = computePool(apply("akka.tcp://system@host2:0"));

    @Before
    public void setUp() throws Exception {
        setupSelection(selection0, path0, RemoteSupplier.class);
        setupSelection(selection1, path1, RemoteOperator.class);
        setupSelection(selection9, path9, RemoteSubscriber.class);

        doReturn(path9.address()).when(cluster).selfAddress();
        doReturn(new byte[0]).when(codec).pack(isA(Remote.class));

        doReturn(new FindFirstGetAddressesStrategy()).when(addressStrategyFactory).getAddressStrategy();
    }

    public void setupSelection(final ActorSelection s, final ActorPath p, final Class<? extends Remote> c) {
        doReturn(p.toString()).when(s).pathString();
        doReturn(p).when(s).anchorPath();
        doReturn(immediateFuture(singletonList(
                of(p.address())
        ))).when(searcher).search(isA(c));

        flowDestinations.put(s, s.anchorPath().address());
        doAnswer(i -> new ArrayList<>(flowDestinations.entrySet())).when(flowDestinationFactory).buildDestinations(isA(SetFlow.class));
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
        observable.subscribe(stage9);

        verify(searcher, times(1)).search(isA(RemoteOperatorWithSubscriber.class));
    }

    @Test
    public void shouldSendSetFlowToComputePoolActorsForRemoteSubscriber() throws Exception {
        observable.subscribe(stage9);

        verify(selection9, times(1)).tell(isA(SetFlow.class), any());
    }

    @Test
    public void shouldCallOnErrorWhenFailsToFindCandidateForRemoteSubscriber() throws Exception {
        doReturn(immediateFuture(asList(
                empty(), empty(), empty()
        ))).when(searcher).search(any());

        @SuppressWarnings("unchecked")
        final Consumer<Throwable> throwableConsumer = mock(Consumer.class);
        CompletableFuture<? extends RemoteSubscription> subscribe = observable.subscribe(stage9);

        assertThat(
                subscribe.isCompletedExceptionally(),
                is(true)
        );
    }

    @Test
    public void shouldSendFlowOnlyOncePerAddress() throws Exception {
        setupSelection(selection0, path0, RemoteSupplier.class);
        setupSelection(selection0, path0, RemoteOperator.class);
        setupSelection(selection1, path1, RemoteSubscriber.class);

        observable
                .withSupplierOfObservable(stage0)
                .map(stage1)
                .subscribe(stage9);

        verify(selection0, times(1)).tell(isA(SetFlow.class), any());
        verify(selection1, times(1)).tell(isA(SetFlow.class), any());
    }

    @Test
    public void flowStagesShouldHaveTypeSet() throws Exception {
        observable
                .withSupplierOfObservable(stage0)
                .map(stage1)
                .subscribe(stage9);

        final ArgumentCaptor<SetFlow> captor = ArgumentCaptor.forClass(SetFlow.class);
        verify(selection0, times(1)).tell(captor.capture(), any());

        final SetFlow setFlow = captor.getValue();
        assertThat(setFlow.getStages(0).getType(), is(Supplier));
        assertThat(setFlow.getStages(1).getType(), is(Operator));
        assertThat(setFlow.getStages(2).getType(), is(Operator));
    }

    @Test
    public void shouldSendSetFlowToAddress() throws Exception {
        observable.subscribe(stage9, createUnresolved("0.0.0.0", 1));

        final ArgumentCaptor<SetFlow> captor = ArgumentCaptor.forClass(SetFlow.class);
        verify(selection9, times(1)).tell(captor.capture(), any());

        final SetFlow setFlow = captor.getValue();
        assertThat(setFlow.getStages(0).getAddress(), is("akka.tcp://" + converterAddresses.actorSystemName + "@0.0.0.0:1"));
    }

    @Test
    public void shouldSendSetFlowToOperatorAddress() throws Exception {
        observable.map(stage1, createUnresolved("0.0.0.0", 1));
        observable.subscribe(stage9);

        final ArgumentCaptor<SetFlow> captor = ArgumentCaptor.forClass(SetFlow.class);
        verify(selection1, times(1)).tell(captor.capture(), any());

        final SetFlow setFlow = captor.getValue();
        assertThat(setFlow.getStages(0).getAddress(), is("akka.tcp://" + converterAddresses.actorSystemName + "@0.0.0.0:1"));
    }

    @Test
    public void shouldSendSetFlowToSupplierAddress() throws Exception {
        observable.withSupplierOfObservable(stage0, createUnresolved("0.0.0.0", 1));
        observable.subscribe(stage9);

        final ArgumentCaptor<SetFlow> captor = ArgumentCaptor.forClass(SetFlow.class);
        verify(selection0, times(1)).tell(captor.capture(), any());

        final SetFlow setFlow = captor.getValue();
        assertThat(setFlow.getStages(0).getAddress(), is("akka.tcp://" + converterAddresses.actorSystemName + "@0.0.0.0:1"));
    }

    @Test
    public void shouldSendFlowWithoutSubscriber() throws Exception {
        observable
                .withSupplierOfObservable(stage0)
                .map(stage1)
                .subscribe();

        verify(selection0).tell(isA(SetFlow.class), any());
        verify(selection1).tell(isA(SetFlow.class), any());
    }

    @Test
    public void shouldSendFullSetFlowWhenCallbacks() throws Exception {
        observable
                .withSupplierOfObservable(stage0)
                .map(stage1)
                .subscribe(str -> {
                }, () -> {
                }, e -> {
                });

        final ArgumentCaptor<SetFlow> captor = ArgumentCaptor.forClass(SetFlow.class);
        verify(selection1).tell(captor.capture(), any());

        assertThat(
                captor.getValue().getStagesCount(),
                is(3)
        );
    }

    @Test
    public void shouldSendLocalSetFlow() throws Exception {
        doReturn(singletonList(new AbstractMap.SimpleEntry<>(selection9, selection9.anchorPath().address()))).when(flowDestinationFactory).buildDestinations(any());
        converterAddresses.actorSystemName = "system";

        observable
                .withSupplierOfObservable(stage0)
                .map(stage1)
                .subscribe(str -> {
                }, () -> {
                }, e -> {
                });

        verify(selection9).tell(isA(LocalSetFlow.class), any());
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

    private static class TestRemoteOperatorImpl implements RemoteOperator<String, String> {
        @Override
        public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
            return subscriber;
        }
    }
}