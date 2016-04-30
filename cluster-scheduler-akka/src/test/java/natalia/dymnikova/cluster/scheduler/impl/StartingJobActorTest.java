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
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.SchedulerService;
import natalia.dymnikova.cluster.scheduler.StagesUnready;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.impl.StartingJobActor.CheckIfReadyResponses;
import natalia.dymnikova.test.TestActorRef;
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

import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessage;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class StartingJobActorTest {

    private final Flow.SetFlow flow = flowMessage().toBuilder().build();

    private final ActorAdapter adapter = mock(ActorAdapter.class);

    @InjectMocks
    private final StartingJobActor startingJobActor = new StartingJobActor(adapter, flow);

    @Spy
    private TestActorRef self;

    @Mock
    private ActorSelection firstStage;

    @Mock
    private ActorSelection secondStage;

    @Mock
    private ActorSelection thirdStage;

    @Mock
    private SchedulerService scheduler;

    @Captor
    private ArgumentCaptor<Producer> producerCaptor;

    private ActorRef sender0;
    private ActorRef sender1;
    private ActorRef sender2;

    @Before
    public void setUp() throws Exception {

        doReturn(self).when(adapter).self();
        doReturn(scheduler).when(adapter).scheduler();

        doReturn(firstStage).when(adapter).actorSelection(remoteStageActorPath(flow, 0));
        doReturn(remoteStageActorPath(flow, 0).toStringWithoutAddress()).when(firstStage).pathString();

        doReturn(secondStage).when(adapter).actorSelection(remoteStageActorPath(flow, 1));
        doReturn(remoteStageActorPath(flow, 1).toStringWithoutAddress()).when(secondStage).pathString();

        doReturn(thirdStage).when(adapter).actorSelection(remoteStageActorPath(flow, 2));
        doReturn(remoteStageActorPath(flow, 2).toStringWithoutAddress()).when(thirdStage).pathString();

        sender0 = new TestActorRef(remoteStageActorPath(flow, 0));
        sender1 = new TestActorRef(remoteStageActorPath(flow, 1));
        sender2 = new TestActorRef(remoteStageActorPath(flow, 2));

        startingJobActor.preStart();
    }


    @Test
    public void shouldScheduleCheckIfAllIsReadyResponsesReceived() throws Exception {
        verify(scheduler).scheduleOnce(
                any(), same(self), isA(CheckIfReadyResponses.class)
        );
    }

    @Test
    public void shouldSendIsReadyMessageToAllStages() throws Exception {
        verify(firstStage).tell(isA(Flow.IsReady.class), same(self));
        verify(secondStage).tell(isA(Flow.IsReady.class), same(self));
        verify(thirdStage).tell(isA(Flow.IsReady.class), same(self));
    }

    @Test
    public void shouldSendIsReadyMessagesWhenCheckIfReadyResponses() throws Exception {
        reset(firstStage);
        reset(secondStage);
        reset(thirdStage);

        startingJobActor.handle(new CheckIfReadyResponses(0));
        verify(firstStage).tell(isA(Flow.IsReady.class), same(self));
        verify(secondStage).tell(isA(Flow.IsReady.class), same(self));
        verify(thirdStage).tell(isA(Flow.IsReady.class), same(self));
    }

    @Test
    public void shouldNotSendIsReadyMessagesToStagesWhichIsOkWhenCheckIfReadyResponses() throws Exception {
        doReturn(sender0).when(adapter).sender();
        startingJobActor.handle(Flow.State.Ok.getDefaultInstance());

        reset(firstStage);
        reset(secondStage);
        reset(thirdStage);

        startingJobActor.handle(new CheckIfReadyResponses(0));
        verify(firstStage, never()).tell(isA(Flow.IsReady.class), same(self));
        verify(secondStage).tell(isA(Flow.IsReady.class), same(self));
        verify(thirdStage).tell(isA(Flow.IsReady.class), same(self));
    }

    @Test
    public void shouldAgainScheduleCheckIfAllIsReadyResponsesReceivedWhenNotAllStagesAreOk() throws Exception {
        reset(scheduler);
        startingJobActor.handle(new CheckIfReadyResponses(0));
        verify(scheduler).scheduleOnce(
                any(), same(self), isA(CheckIfReadyResponses.class)
        );
    }

    @Test
    public void shouldNotAgainScheduleCheckIfAllIsReadyResponsesReceivedWhenAllStagesAreOk() throws Exception {
        sendOkFromAllStages();

        reset(scheduler);
        startingJobActor.handle(new CheckIfReadyResponses(0));
        verify(scheduler, never()).scheduleOnce(
                any(), same(self), isA(CheckIfReadyResponses.class)
        );
    }

    @Test(expected = StagesUnready.class)
    public void shouldThrowStagesUnreadyExceptionWhenNotAllStagesAreOk() throws Exception {
        startingJobActor.handle(new CheckIfReadyResponses(startingJobActor.countIterations));
    }

    @Test
    public void shouldNotThrowExceptionWhenAllStagesAreOkAfterLastIteration() throws Exception {
        sendOkFromAllStages();

        startingJobActor.handle(new CheckIfReadyResponses(startingJobActor.countIterations));
    }

    public void sendOkFromAllStages() {
        doReturn(sender0).when(adapter).sender();
        startingJobActor.handle(Flow.State.Ok.getDefaultInstance());
        doReturn(sender1).when(adapter).sender();
        startingJobActor.handle(Flow.State.Ok.getDefaultInstance());
        doReturn(sender2).when(adapter).sender();
        startingJobActor.handle(Flow.State.Ok.getDefaultInstance());
    }

}