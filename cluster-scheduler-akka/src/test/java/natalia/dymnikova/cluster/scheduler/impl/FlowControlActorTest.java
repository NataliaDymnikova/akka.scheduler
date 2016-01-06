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
import akka.actor.Props;
import akka.actor.SupervisorStrategy.Directive;
import akka.cluster.Cluster;
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import natalia.dymnikova.test.TestActorRef;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import scala.PartialFunction;

import static akka.actor.AddressFromURIString.parse;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.Host1;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.StageActorName0;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.StageActorName1;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.StageActorName2;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessage;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteFlowControlActorPath;
import static natalia.dymnikova.test.TestActorProps.props;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * 
 */
@RunWith(MockitoJUnitRunner.class)
public class FlowControlActorTest {

    @Mock
    private AkkaExtension extension;

    @Mock
    private Cluster cluster;

    private ActorAdapter adapter = spy(ActorAdapter.class);

    private SetFlow flow = flowMessage();

    private ActorSelection selection0 = givenRemoteFlowControlActorSelection(flow, 0);
    private ActorSelection selection1 = givenRemoteFlowControlActorSelection(flow, 1);
    private ActorSelection selection2 = givenRemoteFlowControlActorSelection(flow, 2);

    @InjectMocks
    private FlowControlActor flowControlActor = new FlowControlActor(adapter, flow);

    @Spy
    private TestActorRef stage;

    @Spy
    private TestActorRef self;

    private Props stageActorProps = props();

    @Before
    public void setUp() throws Exception {
        doReturn(parse(Host1)).when(cluster).selfAddress();

        doReturn(stageActorProps).when(extension).props(same(IntermediateStageActor.class), any(), any(), any(), any());
        doReturn(stageActorProps).when(extension).props(same(FinalStageActor.class), any());

        doReturn(stage).when(adapter).actorOf(stageActorProps, StageActorName0);
        doReturn(stage).when(adapter).actorOf(stageActorProps, StageActorName1);
        doReturn(stage).when(adapter).actorOf(stageActorProps, StageActorName2);

        doReturn(selection0).when(adapter).actorSelection(remoteFlowControlActorPath(flow, 0));
        doReturn(selection1).when(adapter).actorSelection(remoteFlowControlActorPath(flow, 1));
        doReturn(selection2).when(adapter).actorSelection(remoteFlowControlActorPath(flow, 2));

        doReturn(self).when(adapter).self();
    }

    @Test
    public void shouldStartStageActorWhenFlowReceived() throws Exception {
        flowControlActor.preStart();

        verify(adapter).actorOf(stageActorProps, StageActorName1);
    }

    @Test
    public void shouldStartSecondStageActorWhenFlowWithOtherNumberIsReceived() throws Exception {
        final SetFlow.Builder builder = this.flow.toBuilder();
        builder.getStagesBuilderList()
                .get(this.flow.getStagesCount() - 1)
                .setAddress(flow.getStages(this.flow.getStagesCount() - 2).getAddress());

        flowControlActor.flow = builder.build();

        flowControlActor.preStart();

        verify(adapter).actorOf(stageActorProps, StageActorName2);
    }

    @Test
    public void shouldSendErrorToAllPeersWhenStageIsTerminated() throws Exception {
        flowControlActor.preStart();

        final PartialFunction<Throwable, Directive> decider = flowControlActor.supervisorStrategy().decider();
        decider.apply(new TestDeadActorAbnormalTermination("Expected"));

        verify(selection0).tell(isA(State.Error.class), same(self));
        verify(selection2).tell(isA(State.Error.class), same(self));
    }

    @Test
    public void shouldSendErrorToAPeersWhenStageIsTerminated() throws Exception {
        final SetFlow.Builder builder = this.flow.toBuilder();
        builder.getStagesBuilderList()
                .get(this.flow.getStagesCount() - 1)
                .setAddress(flow.getStages(this.flow.getStagesCount() - 2).getAddress());

        flowControlActor.flow = builder.build();

        flowControlActor.preStart();

        final PartialFunction<Throwable, Directive> decider = flowControlActor.supervisorStrategy().decider();
        decider.apply(new TestDeadActorAbnormalTermination("Expected"));

        verify(selection0).tell(isA(State.Error.class), same(self));
        verify(selection1, never()).tell(isA(State.Error.class), same(self));
        verify(selection2, never()).tell(isA(State.Error.class), same(self));
    }

    @Test
    public void shouldNotSendErrorToSelf() throws Exception {
        flowControlActor.preStart();

        final PartialFunction<Throwable, Directive> decider = flowControlActor.supervisorStrategy().decider();
        decider.apply(new TestDeadActorAbnormalTermination("Expected"));

        verifyZeroInteractions(selection1);
    }

    @Test
    public void shouldNotTerminateSelfWhenErrorCame() throws Exception {
        flowControlActor.preStart();

        flowControlActor.handle(State.Error.newBuilder()
                .setMessage("Expected")
                .build());

        verify(adapter, never()).stop(self);
    }


    public ActorSelection givenRemoteFlowControlActorSelection(final SetFlow flow, final int stageIndex) {
        final ActorSelection selection1 = mock(ActorSelection.class);
        doReturn(selection1).when(adapter).actorSelection(
                remoteFlowControlActorPath(flow, stageIndex)
        );
        return selection1;
    }

    private static class TestDeadActorAbnormalTermination extends RuntimeException {
        public TestDeadActorAbnormalTermination(final String expected) {
            super(expected);
        }
    }


}
