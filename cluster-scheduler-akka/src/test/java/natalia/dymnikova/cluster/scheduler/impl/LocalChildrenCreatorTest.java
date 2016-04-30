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
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.SpringAkkaExtensionId;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.OnSubscribeWrapper;
import natalia.dymnikova.test.TestActorRef;
import natalia.dymnikova.util.AutowireHelper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.ApplicationContext;
import rx.Observable;
import rx.Producer;
import rx.Subscriber;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.function.BiConsumer;

import static akka.actor.ActorSelection.apply;
import static akka.actor.AddressFromURIString.parse;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Local;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Operator;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
import static natalia.dymnikova.test.TestActorProps.props;
import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 *
 */
public class LocalChildrenCreatorTest {
    @Mock
    private SpringAkkaExtensionId.AkkaExtension extension;

    @Mock
    private ApplicationContext context;

    @Mock
    private Cluster cluster;

    @Mock
    private AutowireHelper helper;

    @InjectMocks
    private Codec codec = spy(new Codec());

    private final String testHost = "akka://system@host0:0";
    private final String testHost1 = "akka://system@host0:1";
    private final SetFlow setFlowWithLocal = SetFlow.newBuilder()
            .setFlowName("FlowName")
            .setStage(
                    Stage.newBuilder()
                            .setAddress(testHost)
                            .setType(Local)
                            .setOperator(wrap(codec.pack(new RemoteSubscriberImpl())))
                            .setId(0)
                            .addStages(
                                    Stage.newBuilder()
                                            .setAddress(testHost)
                                            .setType(Operator)
                                            .setOperator(wrap(codec.pack((RemoteOperator<String, String>) (subscriber) -> subscriber)))
                                            .setId(1)
                                            .addStages(
                                                    Stage.newBuilder()
                                                            .setAddress(testHost1)
                                                            .setType(Supplier)
                                                            .setOperator(ByteString.EMPTY)
                                                            .setId(2)
                                            )))
            .build();

    private final SetFlow setFlowWithoutLocal = SetFlow.newBuilder()
            .setFlowName("FlowName")
            .setStage(
                    Stage.newBuilder()
                            .setAddress(testHost1)
                            .setType(Operator)
                            .setOperator(wrap(codec.pack(new RemoteSubscriberImpl())))
                            .setId(0)
                            .addStages(
                                    Stage.newBuilder()
                                            .setAddress(testHost)
                                            .setType(Operator)
                                            .setOperator(wrap(codec.pack((RemoteOperator<String, String>) (subscriber) -> subscriber)))
                                            .setId(1)
                                            .addStages(
                                                    Stage.newBuilder()
                                                            .setAddress(testHost1)
                                                            .setType(Operator)
                                                            .setOperator(ByteString.EMPTY)
                                                            .setId(2)
                                            )))
            .build();

    private LocalSetFlow flowWithLastLocal = new LocalSetFlow(
            setFlowWithLocal,
            singletonList(new SimpleEntry<>(0, mock(Subscriber.class)))
    );

    private LocalSetFlow flowWithLocalInTheMiddle = new LocalSetFlow(
            setFlowWithLocal,
            singletonList(new SimpleEntry<>(1, mock(Subscriber.class)))
    );

    private final OnSubscribeWrapper<String> localSupplier = new OnSubscribeWrapper<>(s ->
            Observable.<String>empty().subscribe(s)
    );

    private LocalSetFlow flowWithFirstLocal = new LocalSetFlow(
            setFlowWithLocal,
            singletonList(new SimpleEntry<>(1, localSupplier))
    );

    private LocalSetFlow flowWithoutLocal = new LocalSetFlow(
            setFlowWithoutLocal,
            emptyList()
    );

    @Mock
    private FlowControlActor flowControlActor;

    @Spy
    private TestActorRef self;

    private Props startStageProps = props();
    private Props stageActorProps = props();
    private Props startingActorProps = props();

    @InjectMocks
    private LocalChildrenCreator creator;

    @Test
    public void shouldCreateChildForLocalStage() throws Exception {
        givenCreator(flowWithLastLocal);

        creator.apply(flowControlActor);

        verify(flowControlActor, times(3)).actorOf(any(), any());
    }

    @Test
    public void shouldCreateChildForLocalSupplier() throws Exception {
        givenCreator(flowWithFirstLocal);

        creator.apply(flowControlActor);

        verify(flowControlActor, times(1)).actorOf(same(startStageProps), any());
    }

    @Test
    public void shouldPassLocalSupplierToStartStageActor() throws Exception {
        givenCreator(flowWithFirstLocal);

        creator.apply(flowControlActor);

        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(extension).props(same(StartStageActor.class), captor.capture());

        assertThat(
                captor.getAllValues(), hasItem(instanceOf(OnSubscribeWrapper.class))
        );
    }

    @Test
    public void shouldNotCreateChildForNotLocalStage() throws Exception {
        givenCreator(flowWithoutLocal);

        creator.apply(flowControlActor);

        verify(flowControlActor, times(1)).actorOf(any(), any());
    }

    @Test
    public void shouldCreateStartingJobActorWhenFirstStageIsLocal() throws Exception {
        givenCreator(flowWithLastLocal);

        creator.apply(flowControlActor);

        verify(flowControlActor).actorOf(startingActorProps, "StartingJobActor");
    }

    @Test
    public void shouldSendCompleteToParentStage() throws Exception {
        givenCreator(flowWithLastLocal);
        creator.apply(flowControlActor);

        final ArgumentCaptor<BiConsumer> captor = forClass(BiConsumer.class);
        verify(context, atLeastOnce()).getBean(same(OutSubscriberFactory.class), captor.capture());

        final List<BiConsumer> allValues = captor.getAllValues();
        final ActorSelection next = mock(ActorSelection.class);
        final ActorRef parent = spy(new TestActorRef());
        allValues.get(1).accept(Pair.create(next, parent), self);

        verify(parent).tell(eq(Flow.Completed.getDefaultInstance()), same(self));
    }

    @Test
    public void shouldSendCompleteToNextStage() throws Exception {
        givenCreator(flowWithLocalInTheMiddle);
        creator.apply(flowControlActor);

        final ArgumentCaptor<BiConsumer> captor = forClass(BiConsumer.class);
        verify(context, atLeastOnce()).getBean(same(OutSubscriberFactory.class), captor.capture());

        final List<BiConsumer> allValues = captor.getAllValues();
        final ActorSelection next = mock(ActorSelection.class);
        final ActorRef parent = spy(new TestActorRef());
        allValues.get(1).accept(Pair.create(of(next), parent), self);

        verify(next).tell(eq(Flow.Completed.getDefaultInstance()), same(self));
    }

    @Test
    public void shouldCallOnErrorOnSubscriberWhenError() throws Exception {
        givenCreator(flowWithLocalInTheMiddle);
        reset(extension);

        creator.apply(flowControlActor);

        final ArgumentCaptor<Object> captor = forClass(Object.class);
        verify(extension, times(2)).props(
                same(IntermediateStageActor.class),
                captor.capture()
        );

        final List<Object> list = captor.getAllValues().stream().filter(o -> o instanceof Observable.Operator).collect(toList());
        final Observable.Operator operator = (Observable.Operator) list.get(list.size() - 1);

        final Subscriber subscriber = mock(Subscriber.class);
        final Exception expected = new Exception("Expected");
        ((Subscriber) operator.call(subscriber)).onError(expected);
        verify(subscriber).onError(expected);
    }

    @Test
    public void shouldCallOnCompleteOnSubscriberWhenCompleted() throws Exception {
        givenCreator(flowWithLocalInTheMiddle);
        reset(extension);

        creator.apply(flowControlActor);

        final ArgumentCaptor<Object> captor = forClass(Object.class);
        verify(extension, times(2)).props(
                same(IntermediateStageActor.class),
                captor.capture()
        );

        final List<Object> list = captor.getAllValues().stream().filter(o -> o instanceof Observable.Operator).collect(toList());
        final Observable.Operator operator = (Observable.Operator) list.get(list.size() - 1);

        final Subscriber subscriber = mock(Subscriber.class);
        ((Subscriber) operator.call(subscriber)).onCompleted();
        verify(subscriber).onCompleted();
    }

    private void givenCreator(final LocalSetFlow flow) {
        creator = new LocalChildrenCreator(flow);

        MockitoAnnotations.initMocks(this);

        doReturn(parse(testHost)).when(cluster).selfAddress();

        doReturn(startStageProps).when(extension).props(same(StartStageActor.class), anyVararg());
        doReturn(startingActorProps).when(extension).props(same(StartingJobActor.class), anyVararg());
        doReturn(stageActorProps).when(extension).props(same(IntermediateStageActor.class), anyVararg());
        doReturn(stageActorProps).when(extension).props(same(StartingJobActor.class), anyVararg());

        doReturn(self).when(flowControlActor).self();
        doAnswer(i -> i.getArguments()[0]).when(helper).autowire(any());
        doReturn(apply(new TestActorRef(), "")).when(flowControlActor).actorSelection(any(ActorPath.class));
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