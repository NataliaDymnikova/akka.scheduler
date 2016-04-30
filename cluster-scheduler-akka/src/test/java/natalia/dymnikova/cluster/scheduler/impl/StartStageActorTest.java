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
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.OnStart;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.test.TestActorRef;
import natalia.dymnikova.util.AutowireHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class StartStageActorTest {

    private final Codec codec = spy(Codec.class);

    @SuppressWarnings("Convert2Lambda")
    private final TestRemoteSupplier stage = new TestRemoteSupplier();

    private final ActorSelection nextActor = mock(ActorSelection.class);

    private final ActorAdapter adapter = mock(ActorAdapter.class);

    @Mock
    private AutowireHelper autowireHelper;

    @InjectMocks
    private final StartStageActor stageActor = new StartStageActor(
            adapter,
            nextActor,
            SetFlow.getDefaultInstance(),
            stage
    );

    @Spy
    private TestActorRef parent;

    @Spy
    private TestActorRef self;

    @Before
    public void setUp() throws Exception {
        doAnswer(i -> i.getArguments()[0]).when(autowireHelper).autowire(any());

        TestRemoteSupplier.observable = null;

        doReturn(parent).when(adapter).parent();

        doReturn(parent).when(adapter).sender();
        doReturn(self).when(adapter).self();
    }

    @Test
    public void shouldReplyOkWhenReadyMessage() throws Exception {
        stageActor.handle(Flow.IsReady.getDefaultInstance());
        verify(parent).tell(isA(Flow.State.Ok.class), eq(self));
    }

    @Test
    public void shouldSubscribeWhenOnStartMessage() throws Exception {
        final Action0 action = mock(Action0.class);
        TestRemoteSupplier.observable = Observable.just("test")
                .doOnSubscribe(action);

        stageActor.handle(OnStart.newBuilder().setCount(0).build());
        verify(action).call();
    }

    @Test
    public void shouldNotSendMessagesWhenObservableHasNotStartedProdusingMessages() throws Exception {
        final PublishSubject<Object> subject = PublishSubject.create();
        TestRemoteSupplier.observable = subject;

        stageActor.handle(OnStart.newBuilder().setCount(0).build());

        verify(nextActor, never()).tell(any(), any());
    }

    @Test
    public void shouldDispatchMessageToNextActor() throws Exception {
        final PublishSubject<Object> subject = PublishSubject.create();
        TestRemoteSupplier.observable = subject;

        stageActor.handle(OnStart.newBuilder().setCount(1).build());

        subject.onNext("firstMessage");

        verify(nextActor).tell(eq(Flow.Data.newBuilder().setData(wrap(codec.packObject("firstMessage"))).build()), any());
    }

    @Test
    public void shouldCallRequestOnObservableWhenMoreMessage() throws Exception {
        final Action1 onRequest = mock(Action1.class);

        TestRemoteSupplier.observable = Observable.just("test")
                .doOnRequest(onRequest);

        stageActor.handle(OnStart.newBuilder().setCount(0).build());

        reset(onRequest);
        stageActor.handle(Flow.More.newBuilder().setCount(1).build());

        verify(onRequest).call(1L);
    }

    @Test
    public void shouldSendCompletedMessageWhenObservableOnCompleted() throws Exception {
        final PublishSubject<Object> subject = PublishSubject.create();
        TestRemoteSupplier.observable = subject;

        stageActor.handle(OnStart.newBuilder().setCount(1).build());

        subject.onCompleted();
        verify(nextActor).tell(eq(Flow.Completed.getDefaultInstance()), any());
    }

    @Test
    public void shouldSendErrorMessageWhenObservableOnError() throws Exception {
        final PublishSubject<Object> subject = PublishSubject.create();
        TestRemoteSupplier.observable = subject;

        stageActor.handle(OnStart.newBuilder().setCount(1).build());

        subject.onError(new Throwable("error"));
        verify(self).tell(isA(SubscriberWithMore.HandleException.class), same(self));
    }

    @Test
    public void shouldThrowExceptionWhenGetErrorMessage() throws Exception {
        final PublishSubject<Object> subject = PublishSubject.create();
        TestRemoteSupplier.observable = subject;

        stageActor.handle(OnStart.newBuilder().setCount(0).build());

        stageActor.handle(Flow.State.Error.newBuilder().setMessage("Expected").build());
        verify(self).tell(isA(Exception.class), eq(self));
    }

    @Test
    public void shouldSendErrorMessageToSelfWhenSubscriberIsNull() throws Exception {
        final PublishSubject<Object> subject = PublishSubject.create();
        TestRemoteSupplier.observable = subject;
        stageActor.handle(Flow.State.Error.newBuilder().setMessage("Expected").build());
        verify(self).tell(isA(Exception.class), eq(self));
    }

    private static class TestRemoteSupplier implements RemoteSupplier<Observable<String>> {
        static Observable observable;

        @Override
        public Observable<String> get() {
            assertNotNull(observable);
            return observable;
        }
    }
}