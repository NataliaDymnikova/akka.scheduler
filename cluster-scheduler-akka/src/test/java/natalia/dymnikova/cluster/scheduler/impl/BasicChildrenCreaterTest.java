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
import akka.cluster.Cluster;
import natalia.dymnikova.cluster.SpringAkkaExtensionId;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.test.TestActorRef;
import natalia.dymnikova.util.AutowireHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import static akka.actor.AddressFromURIString.parse;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.Host1;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.StageActorName1;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.StageActorName2;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessage;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteFlowControlActorPath;
import static natalia.dymnikova.test.TestActorProps.props;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class BasicChildrenCreaterTest {

    private final AutowireHelper autowireHelper = mock(AutowireHelper.class);

    @InjectMocks
    private final Codec codec = spy(Codec.class);

    @Mock
    private AkkaExtension extension;

    @Mock
    private ApplicationContext context;

    @Mock
    private Cluster cluster;

    private FlowControlActor flowControlActor = mock(FlowControlActor.class);

    private Flow.SetFlow flow = flowMessage();

    @InjectMocks
    private BasicChildrenCreater childrenCreater = new BasicChildrenCreater(flow);

    @Spy
    private TestActorRef stage;

    @Spy
    private TestActorRef self;

    private Props stageActorProps = props();
    private Props startingActorProps = props();

    @Before
    public void setUp() throws Exception {
        doReturn(parse(Host1)).when(cluster).selfAddress();

        doReturn(stageActorProps).when(extension).props(same(IntermediateStageActor.class), any(), any(), any(), any());
        doReturn(startingActorProps).when(extension).props(same(StartingJobActor.class), any());

        doReturn(self).when(flowControlActor).self();
        doAnswer(i -> i.getArguments()[0]).when(autowireHelper).autowire(any());
    }

    @Test
    public void shouldStartStageActorWhenFlowReceived() throws Exception {
        childrenCreater.apply(flowControlActor);

        verify(flowControlActor).actorOf(stageActorProps, StageActorName1);
    }

    @Test
    public void shouldStartSecondStageActorWhenFlowWithOtherNumberIsReceived() throws Exception {
        final Flow.SetFlow.Builder builder = this.flow.toBuilder();
        builder.getStagesBuilderList()
                .get(this.flow.getStagesCount() - 1)
                .setAddress(flow.getStages(this.flow.getStagesCount() - 2).getAddress());

        childrenCreater.flow = builder.build();

        childrenCreater.apply(flowControlActor);

        verify(flowControlActor).actorOf(stageActorProps, StageActorName2);
    }

    @Test
    public void shouldStartStartingJobActorWhenFirstStage() throws Exception {
        final Flow.SetFlow.Builder builder = this.flow.toBuilder();
        builder.getStagesBuilderList()
                .get(0)
                .setAddress(Host1);

        childrenCreater.flow = builder.build();

        childrenCreater.apply(flowControlActor);

        verify(flowControlActor).actorOf(startingActorProps, "StartingJobActor");
    }

    @Test
    public void shouldNotStartStartingJobActorWhenNotLastStage() throws Exception {
        childrenCreater.apply(flowControlActor);

        verify(flowControlActor, never()).actorOf(stageActorProps);
    }

    public ActorSelection givenRemoteFlowControlActorSelection(final Flow.SetFlow flow, final int stageIndex) {
        final ActorSelection selection1 = mock(ActorSelection.class);
        doReturn(selection1).when(flowControlActor).actorSelection(
                remoteFlowControlActorPath(flow, stageIndex)
        );
        return selection1;
    }

}