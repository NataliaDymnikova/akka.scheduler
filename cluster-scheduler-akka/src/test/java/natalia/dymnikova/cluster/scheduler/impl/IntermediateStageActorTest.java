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
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteStageException;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.impl.SubscriberWithMore.HandleException;
import natalia.dymnikova.test.TestActorRef;
import natalia.dymnikova.util.AutowireHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Subscriber;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.Serializable;

import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessage;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 
 */
@RunWith(MockitoJUnitRunner.class)
public class IntermediateStageActorTest {
    private final Codec codec = spy(Codec.class);

    private Subscriber<? super Serializable> inSubscriber;

    @Spy
    private final RemoteOperator<Serializable, Serializable> operator = new RemoteOperator<Serializable, Serializable>() {
        @Override
        public Subscriber<? super Serializable> call(final Subscriber<? super Serializable> out) {
            return IntermediateStageActorTest.this.inSubscriber = spy(new Subscriber<Serializable>(out) {
                @Override
                public void onCompleted() {
                    out.onCompleted();
                }

                @Override
                public void onError(final Throwable e) {
                    out.onError(e);
                }

                @Override
                public void onNext(final Serializable serializable) {
                    out.onNext(serializable);
                }
            });
        }
    };

    private final Flow.SetFlow flow = flowMessage((RemoteOperator<Serializable, Serializable>) subscriber -> null).toBuilder().build();

    private final ActorAdapter adapter = mock(ActorAdapter.class);

    private ActorSelection firstStage = mock(ActorSelection.class);

    private ActorSelection thirdStage = mock(ActorSelection.class);

    @Mock
    private AutowireHelper autowireHelper;

    @InjectMocks
    private final IntermediateStageActor stageActor = new IntermediateStageActor(adapter, firstStage, thirdStage, flow, flow.getStages(1));

    @Spy
    private TestActorRef parent;

    @Spy
    private TestActorRef self;

    @Spy
    private TestActorRef sender;

    @Before
    public void setUp() throws Exception {
        doAnswer(i -> i.getArguments()[0]).when(autowireHelper).autowire(any());

        doReturn(parent).when(adapter).parent();
        doReturn(self).when(adapter).self();
        doReturn(sender).when(adapter).sender();

        doReturn(operator).when(codec).unpackOperator(any());

        doReturn(firstStage).when(adapter).actorSelection(remoteStageActorPath(flow, 0));
        doReturn(remoteStageActorPath(flow, 0)).when(firstStage).anchorPath();

        doReturn(thirdStage).when(adapter).actorSelection(remoteStageActorPath(flow, 1));
        doReturn(remoteStageActorPath(flow, 1)).when(thirdStage).anchorPath();

        stageActor.preStart();
    }

    @Test
    public void shouldSendOkWhenIsReadyMessage() throws Exception {
        stageActor.handle(Flow.IsReady.getDefaultInstance());
        verify(sender).tell(Flow.State.Ok.getDefaultInstance(), self);
    }

    @Test
    public void shouldSendOnStartWhenOnStartMessage() throws Exception {
        stageActor.handle(Flow.OnStart.getDefaultInstance());
        verify(firstStage).tell(Flow.OnStart.getDefaultInstance(), self);
    }

    @Test
    public void shouldCallOnStartWhenOnStartMessage() throws Exception {
        stageActor.handle(Flow.OnStart.getDefaultInstance());
        verify(inSubscriber).onStart();
    }

    @Test
    public void shouldCallRequestWhenMore() throws Exception {
        stageActor.handle(Flow.OnStart.newBuilder().setCount(0).build());
        stageActor.handle(Flow.More.newBuilder().setCount(14).build());
        verify(firstStage).tell(Flow.More.newBuilder().setCount(14).build(), self);
    }

    @Test
    public void shouldCallOnNextWhenDataMessageInSubscriber() throws Exception {
        stageActor.handle(Flow.Data.newBuilder().setData(wrap(codec.packObject("test"))).build());
        verify(inSubscriber).onNext("test");
    }

    @Test
    public void shouldTellWhenDataMessageInSubscriber() throws Exception {
        stageActor.handle(Flow.Data.newBuilder().setData(wrap(codec.packObject("test"))).build());
        verify(thirdStage).tell(Flow.Data.newBuilder().setData(wrap(codec.packObject("test"))).build(), self);
    }

    @Test
    public void shouldCallOnCompletedWhenCompletedInSubscriber() throws Exception {
        stageActor.handle(Flow.Completed.getDefaultInstance());
        verify(inSubscriber).onCompleted();
    }

    @Test
    public void shouldSendCompletedToTheNextActorWhenCompleted() throws Exception {
        stageActor.handle(Flow.Completed.getDefaultInstance());
        verify(thirdStage).tell(Flow.Completed.getDefaultInstance(), self);
    }

    @Test
    public void shouldSendHandleExceptionToSelfWhenExceptionInSubscriber() throws Exception {
        inSubscriber.onError(new Exception("Expected"));
        verify(self).tell(isA(HandleException.class), same(self));
    }

    @Test(expected = RuntimeException.class)
    public void shouldSendErrorToParentWhenErrorInHandle() throws Exception {

        final ArgumentCaptor<PartialFunction<Object, BoxedUnit>> rCapture = forClass(PartialFunction.class);
        verify(adapter).receive(rCapture.capture());

        doThrow(new RuntimeException("Expected")).when(inSubscriber).onCompleted();
        try {
            rCapture.getValue().apply(Flow.Completed.getDefaultInstance());
        } finally {
            verify(self).tell(isA(HandleException.class), same(self));
        }
    }

    @Test
    public void shouldCallOnErrorWhenErrorMessage() throws Exception {
        stageActor.handle(Flow.State.Error.getDefaultInstance());
        verify(inSubscriber).onError(isA(RemoteStageException.class));

    }
}