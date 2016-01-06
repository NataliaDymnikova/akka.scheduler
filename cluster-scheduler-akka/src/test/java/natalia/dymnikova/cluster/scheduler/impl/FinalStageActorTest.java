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
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.Scheduler;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.StagesUnready;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.impl.FinalStageActor.CheckIfReadyResponses;
import natalia.dymnikova.test.TestActorRef;
import natalia.dymnikova.util.AutowireHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Producer;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.InputStream;
import java.io.Serializable;

import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessage;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 
 */
@RunWith(MockitoJUnitRunner.class)
public class FinalStageActorTest {

    private final Codec codec = spy(Codec.class);

    private final RemoteSubscriber<Serializable> subscriber = mock(RemoteSubscriber.class);

    private final Flow.SetFlow flow = flowMessage(subscriber).toBuilder().build();

    private final ActorAdapter adapter = mock(ActorAdapter.class);

    @InjectMocks
    private final FinalStageActor stageActor = new FinalStageActor(adapter, flow);

    @Spy
    private TestActorRef parent;

    @Spy
    private TestActorRef self;

    @Mock
    private ActorSelection firstStage;

    @Mock
    private ActorSelection secondStage;

    @Mock
    private Scheduler scheduler;

    @Captor
    private ArgumentCaptor<Producer> producerCaptor;

    @Mock
    private AutowireHelper autowireHelper;

    private ActorRef sender0;
    private ActorRef sender1;

    @Before
    public void setUp() throws Exception {
        doAnswer(i -> i.getArguments()[0]).when(autowireHelper).autowire(any());

        doReturn(parent).when(adapter).parent();
        doReturn(self).when(adapter).self();
        doReturn(scheduler).when(adapter).scheduler();

        doReturn(subscriber).when(codec).unpackSubscriber(any());

        doReturn(firstStage).when(adapter).actorSelection(remoteStageActorPath(flow, 0));
        doReturn(remoteStageActorPath(flow, 0).toStringWithoutAddress()).when(firstStage).pathString();

        doReturn(secondStage).when(adapter).actorSelection(remoteStageActorPath(flow, 1));
        doReturn(remoteStageActorPath(flow, 1).toStringWithoutAddress()).when(secondStage).pathString();

        sender0 = new TestActorRef(remoteStageActorPath(flow, 0));
        sender1 = new TestActorRef(remoteStageActorPath(flow, 1));

        stageActor.preStart();
    }

    @Test
    public void shouldSendIsReadyMessageToAllStages() throws Exception {
        verify(firstStage).tell(isA(Flow.IsReady.class), same(self));
        verify(secondStage).tell(isA(Flow.IsReady.class), same(self));
    }

    @Test
    public void shouldSendOnStartWhenAllStagesTellOk() throws Exception {
        sendOkFromAllStages();

        verify(secondStage).tell(isA(Flow.OnStart.class), same(self));
    }

    @Test
    public void shouldScheduleCheckIfAllIsReadyResponsesReceived() throws Exception {
        verify(scheduler).scheduleOnce(
                any(), same(self), isA(CheckIfReadyResponses.class)
        );
    }

    @Test
    public void shouldSendIsReadyMessagesWhenCheckIfReadyResponses() throws Exception {
        reset(firstStage);
        reset(secondStage);

        stageActor.handle(new CheckIfReadyResponses(0));
        verify(firstStage).tell(isA(Flow.IsReady.class), same(self));
        verify(secondStage).tell(isA(Flow.IsReady.class), same(self));
    }

    @Test
    public void shouldNotSendIsReadyMessagesToStagesWhichIsOkWhenCheckIfReadyResponses() throws Exception {
        doReturn(sender0).when(adapter).sender();
        stageActor.handle(Flow.State.Ok.getDefaultInstance());

        reset(firstStage);
        reset(secondStage);

        stageActor.handle(new CheckIfReadyResponses(0));
        verify(firstStage, never()).tell(isA(Flow.IsReady.class), same(self));
        verify(secondStage).tell(isA(Flow.IsReady.class), same(self));
    }

    @Test
    public void shouldAgainScheduleCheckIfAllIsReadyResponsesReceivedWhenNotAllStagesAreOk() throws Exception {
        reset(scheduler);
        stageActor.handle(new CheckIfReadyResponses(0));
        verify(scheduler).scheduleOnce(
                any(), same(self), isA(CheckIfReadyResponses.class)
        );
    }

    @Test
    public void shouldNotAgainScheduleCheckIfAllIsReadyResponsesReceivedWhenAllStagesAreOk() throws Exception {
        sendOkFromAllStages();

        reset(scheduler);
        stageActor.handle(new CheckIfReadyResponses(0));
        verify(scheduler, never()).scheduleOnce(
                any(), same(self), isA(CheckIfReadyResponses.class)
        );
    }

    @Test(expected = StagesUnready.class)
    public void shouldCallOnErrorInSubscriberWhenNotAllStagesAreOk() throws Exception {
        try {
            stageActor.handle(new CheckIfReadyResponses(stageActor.countIterations));
        } finally {
            verify(subscriber).onError(isA(StagesUnready.class));
        }
    }

    @Test(expected = StagesUnready.class)
    public void shouldThrowStagesUnreadyExceptionWhenNotAllStagesAreOk() throws Exception {
        stageActor.handle(new CheckIfReadyResponses(stageActor.countIterations));
    }

    @Test
    public void shouldNotCallOnErrorInSubscriberWhenAllStagesAreOkAfterLastIteration() throws Exception {
        sendOkFromAllStages();

        stageActor.handle(new CheckIfReadyResponses(stageActor.countIterations));

        verify(subscriber, never()).onError(isA(StagesUnready.class));
    }

    @Test
    public void shouldCallOnStartInSubscriberWhenAllStagesAreOk() throws Exception {
        sendOkFromAllStages();

        verify(subscriber).onStart(isA(Producer.class));
    }

    @Test
    public void shouldSendMoreWhenProducerRequest() throws Exception {
        sendOkFromAllStages();

        reset(secondStage);
        verify(subscriber).onStart(producerCaptor.capture());
        producerCaptor.getValue().request(10L);

        verify(secondStage).tell(Flow.More.newBuilder().setCount(10L).build(), self);
    }

    @Test
    public void shouldCallOnCompletedWhenCompletedMessage() throws Exception {
        sendOkFromAllStages();

        stageActor.handle(Flow.Completed.newBuilder().build());
        verify(subscriber).onCompleted();
    }

    @Test
    public void shouldSendCompletedToParentWhenCompletedMessage() throws Exception {
        sendOkFromAllStages();

        stageActor.handle(Flow.Completed.newBuilder().build());
        verify(parent).tell(Flow.Completed.newBuilder().build(), self);
    }

    @Test
    public void shouldCallOnNextWhenDataMessage() throws Exception {
        sendOkFromAllStages();

        doReturn("test").when(codec).unpack(any(InputStream.class));
        stageActor.handle(Flow.Data.newBuilder().setData(ByteString.EMPTY).build());
        verify(subscriber).onNext("test");
    }

    @Test
    public void shouldCallOnErrorWhenErrorInHandle() throws Exception {
        sendOkFromAllStages();

        final ArgumentCaptor<PartialFunction<Object, BoxedUnit>> rCapture = forClass(PartialFunction.class);
        verify(adapter).receive(rCapture.capture());

        final RuntimeException expected = new RuntimeException("Expected");
        doThrow(expected).when(subscriber).onCompleted();

        try {
            rCapture.getValue().apply(Flow.Completed.getDefaultInstance());
        } catch (final Exception e) {
            assertSame(expected, e);
        }

        verify(subscriber).onError(expected);
    }

    @Test(expected = ExpectedExceptions.class)
    public void shouldNotCaptureErrorWhenErrorInHandle() throws Exception {
        sendOkFromAllStages();

        final ArgumentCaptor<PartialFunction<Object, BoxedUnit>> rCapture = forClass(PartialFunction.class);
        verify(adapter).receive(rCapture.capture());

        final RuntimeException expected = new ExpectedExceptions();
        doThrow(expected).when(subscriber).onCompleted();

        rCapture.getValue().apply(Flow.Completed.getDefaultInstance());
    }

    @Test
    public void shouldUnpackSubscriber() throws Exception {
        verify(codec).unpackSubscriber(flow.getStages(flow.getStagesCount() - 1).getOperator().toByteArray());
    }

    public void sendOkFromAllStages() {
        doReturn(sender0).when(adapter).sender();
        stageActor.handle(Flow.State.Ok.getDefaultInstance());
        doReturn(sender1).when(adapter).sender();
        stageActor.handle(Flow.State.Ok.getDefaultInstance());
    }

    private static final class ExpectedExceptions extends RuntimeException {
    }

}