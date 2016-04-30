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
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.Pair;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.test.TestActorRef;
import natalia.dymnikova.util.AutowireHelper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static akka.actor.ActorSelection.apply;
import static akka.actor.AddressFromURIString.parse;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.Host1;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.Host2;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.StageActorName0;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.StageActorName1;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessage;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessageWithMergeWithTwoSubStages;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteFlowControlActorPath;
import static natalia.dymnikova.test.MoreMatchers.listWithSize;
import static natalia.dymnikova.test.TestActorProps.props;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 *
 */
public class BasicChildrenCreatorTest {

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

    private SetFlow flow = flowMessage();

    @InjectMocks
    private BasicChildrenCreator childrenCreator;

    @Spy
    private TestActorRef stage;

    @Spy
    private TestActorRef self;


    private Props stageActorProps = props();
    private Props startingActorProps = props();
    private Props mergeActorProps = props();

    public void givenCreator(final SetFlow flow) throws Exception {
        childrenCreator = new BasicChildrenCreator(flow);
        MockitoAnnotations.initMocks(this);

        doReturn(parse(Host1)).when(cluster).selfAddress();

        doReturn(stageActorProps).when(extension).props(same(IntermediateStageActor.class), any(), any(), any(), any(), any());
        doReturn(startingActorProps).when(extension).props(same(StartingJobActor.class), any());

        doReturn(self).when(flowControlActor).self();
        doReturn(apply(new TestActorRef(), "")).when(flowControlActor).actorSelection(any(ActorPath.class));
        doAnswer(i -> i.getArguments()[0]).when(autowireHelper).autowire(any());

    }

    @Test
    public void shouldStartStageActorWhenFlowReceived() throws Exception {
        givenCreator(flow);
        childrenCreator.apply(flowControlActor);

        verify(flowControlActor).actorOf(stageActorProps, StageActorName1);
    }

    @Test
    public void shouldStartSecondStageActorWhenFlowWithOtherNumberIsReceived() throws Exception {
        final SetFlow.Builder builder = this.flow.toBuilder();
        builder.getStageBuilder()
                .setAddress(flow.getStage().getStages(0).getAddress());

        givenCreator(builder.build());

        childrenCreator.apply(flowControlActor);

        verify(flowControlActor).actorOf(stageActorProps, StageActorName0);
    }

    @Test
    public void shouldStartStartingJobActorWhenFirstStage() throws Exception {
        final SetFlow.Builder builder = this.flow.toBuilder();

        builder.getStageBuilder()
                .setAddress(Host1);

        givenCreator(builder.build());

        childrenCreator.apply(flowControlActor);

        verify(flowControlActor).actorOf(startingActorProps, "StartingJobActor");
    }

    @Test
    public void shouldNotStartStartingJobActorWhenNotLastStage() throws Exception {
        givenCreator(flow);

        childrenCreator.apply(flowControlActor);

        verify(flowControlActor, never()).actorOf(stageActorProps);
    }

    @Test
    public void shouldSendCompleteToParentStage() throws Exception {
        givenCreator(flow);

        doReturn(parse(Host2)).when(cluster).selfAddress();
        childrenCreator.apply(flowControlActor);

        final ArgumentCaptor<BiConsumer> captor = ArgumentCaptor.forClass(BiConsumer.class);
        verify(context, atLeastOnce()).getBean(same(OutSubscriberFactory.class), captor.capture());

        final BiConsumer allValues = captor.getValue();
        final ActorSelection next = mock(ActorSelection.class);
        final ActorRef parent = spy(new TestActorRef());
        allValues.accept(Pair.create(next, parent), self);

        verify(parent).tell(eq(Flow.Completed.getDefaultInstance()), same(self));
    }

    @Test
    public void shouldSendCompleteToNextStage() throws Exception {
        givenCreator(flow);

        childrenCreator.apply(flowControlActor);

        final ArgumentCaptor<BiConsumer> captor = ArgumentCaptor.forClass(BiConsumer.class);
        verify(context, atLeastOnce()).getBean(same(OutSubscriberFactory.class), captor.capture());

        final BiConsumer allValues = captor.getValue();
        final ActorSelection next = mock(ActorSelection.class);
        final ActorRef parent = spy(new TestActorRef());
        allValues.accept(Pair.create(Optional.of(next), parent), self);

        verify(next).tell(eq(Flow.Completed.getDefaultInstance()), same(self));
    }

    @Test
    public void shouldCreateMergeActor() throws Exception {
        givenCreator(flowMessageWithMergeWithTwoSubStages());
        childrenCreator.apply(flowControlActor);

        verify(extension).props(eq(MergeStageActor.class), any(), any(), any(), any(), any());
    }

    @Test
    public void shouldCreateMergeActorWithAllPreviousStages() throws Exception {
        givenCreator(flowMessageWithMergeWithTwoSubStages());
        childrenCreator.apply(flowControlActor);

        verify(extension).props(eq(MergeStageActor.class), argThat(listWithSize(2)), any(), any(), any(), any());
    }


    public ActorSelection givenRemoteFlowControlActorSelection(final SetFlow flow, final int stageIndex) {
        final ActorSelection selection1 = mock(ActorSelection.class);
        doReturn(selection1).when(flowControlActor).actorSelection(
                remoteFlowControlActorPath(flow, stageIndex)
        );
        return selection1;
    }


    private List<Flow.Stage> getStageList(final Flow.Stage stage) {
        final List<Flow.Stage> stages = new ArrayList<>();
        stages.add(stage);
        stages.addAll(stage.getStagesList().stream().flatMap(st -> getStageList(st).stream()).collect(toList()));
        return stages;
    }
}