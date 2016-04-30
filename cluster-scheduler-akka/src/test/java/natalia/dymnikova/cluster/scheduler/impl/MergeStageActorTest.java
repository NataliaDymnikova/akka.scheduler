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
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.scheduler.RemoteMergeOperator;
import natalia.dymnikova.cluster.scheduler.RemoteStageException;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Completed;
import natalia.dymnikova.cluster.scheduler.impl.SubscriberWithMore.HandleException;
import natalia.dymnikova.test.TestActorRef;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static natalia.dymnikova.test.MoreMatchers.listWithSize;
import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class MergeStageActorTest {
    private final Codec codec = spy(Codec.class);

    @Mock
    private Cluster cluster;

    @Spy
    private TestActorRef self;

    @Spy
    private final SubscriberWithMore subscriberWithMore = new SubscriberWithMore() {
        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(final Throwable e) {
        }

        @Override
        public void onNext(final Serializable serializable) {
        }

        @Override
        public void onStart() {
        }
    };

    private final ActorAdapter adapter = mock(ActorAdapter.class);

    private final ActorPath firstPath = mock(ActorPath.class);
    private final ActorPath secondPath = mock(ActorPath.class);

    @Spy
    private TestActorRef firstSender = new TestActorRef(firstPath);

    @Spy
    private TestActorRef secondSender = new TestActorRef(secondPath);

    private ActorSelection firstSelection = mock(ActorSelection.class);
    private ActorSelection secondSelection = mock(ActorSelection.class);
    private ActorSelection nextStage = mock(ActorSelection.class);

    private OutSubscriberFactory outSubscriberFactory = mock(OutSubscriberFactory.class);

    private final TestMergeOperator mergeOperator = spy(new TestMergeOperator());

    private final List<ActorPath> prevStages = asList(firstPath, secondPath);

    @InjectMocks
    private final MergeStageActor mergeActor = new MergeStageActor(
            adapter,
            prevStages,
            nextStage,
            mergeOperator,
            Flow.SetFlow.getDefaultInstance(),
            outSubscriberFactory
    );

    @Before
    public void setUp() throws Exception {
        doReturn(self).when(adapter).self();
        doReturn(secondSender).when(adapter).sender();

        doReturn(firstSelection).when(adapter).actorSelection(firstPath);
        doReturn(secondSelection).when(adapter).actorSelection(secondPath);

        doReturn(Address.apply("akka.tcp", "log", "host1", 1)).when(firstPath).address();
        doReturn(Address.apply("akka.tcp", "log", "host2", 2)).when(secondPath).address();
        doReturn(Address.apply("akka.tcp", "log", "host2", 3)).when(cluster).selfAddress();

        doReturn(subscriberWithMore).when(outSubscriberFactory).getOutSubscriber(any(), any(), any(), anyLong());
        mergeActor.handle(Flow.OnStart.getDefaultInstance());
    }

    @Test
    public void shouldCallMergeOperator() throws Exception {
        verify(mergeOperator).call(argThat(listWithSize(prevStages.size())));
    }

    @Test
    public void shouldSendOkWhenIsReadyMessage() throws Exception {
        mergeActor.handle(Flow.IsReady.getDefaultInstance());
        verify(secondSender).tell(Flow.State.Ok.getDefaultInstance(), self);
    }

    @Test
    public void shouldCallOnStartOnAllSubscribers() throws Exception {
        mergeActor.handle(Flow.OnStart.getDefaultInstance());

        verify(mergeOperator.subscribers.get(0)).onStart();
        verify(mergeOperator.subscribers.get(1)).onStart();
    }

    @Test
    public void shouldPutDataToObservableWhichCorrespondsToSecondSender() throws Exception {
        doReturn(secondSender).when(adapter).sender();

        mergeActor.handle(Flow.Data.newBuilder().setData(wrap(codec.packObject("test"))).build());
        verify(mergeOperator.subscribers.get(1)).onNext(any(Serializable.class));
    }

    @Test
    public void shouldPutDataToObservableWhichCorrespondsToFirstSender() throws Exception {
        doReturn(firstSender).when(adapter).sender();

        mergeActor.handle(Flow.Data.newBuilder().setData(wrap(codec.packObject("test"))).build());
        verify(mergeOperator.subscribers.get(0)).onNext(any(Serializable.class));
    }

    @Test
    public void shouldCallOnCompleteOnObservableWhichCorrespondsToFirstSender() throws Exception {
        doReturn(firstSender).when(adapter).sender();

        mergeActor.handle(Completed.getDefaultInstance());
        verify(mergeOperator.subscribers.get(0)).onCompleted();
    }

    @Test
    public void shouldCallMoreWhenGetMore() throws Exception {
        mergeActor.handle(Flow.More.newBuilder().setCount(5).build());
        verify(subscriberWithMore).more(5);
    }

    @Test
    public void shouldCallOnNextWhenOnNextCalledOnMergeOperator() throws Exception {
        mergeOperator.outObservable.onNext("test");
        verify(subscriberWithMore).onNext("test");
    }

    @Test
    public void shouldCallOnCompletedWhenMergeOperatorCompleted() throws Exception {
        mergeOperator.outObservable.onCompleted();
        verify(subscriberWithMore).onCompleted();
    }

    @Test
    public void shouldCallOnErrorWhenErrorOnMergeOperator() throws Exception {
        final Exception error = new Exception("error");
        mergeOperator.outObservable.onError(error);
        verify(subscriberWithMore).onError(error);
    }

    @Test
    public void shouldCallOErrorOnObservableWhichCorrespondsToSecondSender() throws Exception {
        doReturn(secondSender).when(adapter).sender();

        mergeActor.handle(Flow.State.Error.newBuilder().setMessage("error").build());
        verify(mergeOperator.subscribers.get(1)).onError(isA(RemoteStageException.class));
    }

    @Test(expected = HandleException.class)
    public void shouldThrowExceptionWhenHandleExceptionReceived() throws Exception {
        final HandleException expected = new HandleException(new Exception("Expected"));
        mergeActor.handle(expected);
    }

    private static class TestMergeOperator implements RemoteMergeOperator<Serializable> {
        private final PublishSubject<Serializable> outObservable = PublishSubject.create();
        private final List<TestSubscriber> subscribers = new ArrayList<>();

        @Override
        public Observable<Serializable> call(final List<Observable<? extends Serializable>> observables) {
            observables.stream().forEach(obs -> {
                final TestSubscriber<Serializable> subscriber = spy(new TestSubscriber<>());
                obs.subscribe(subscriber);
                subscribers.add(subscriber);
            });

            return outObservable;
        }
    }
}