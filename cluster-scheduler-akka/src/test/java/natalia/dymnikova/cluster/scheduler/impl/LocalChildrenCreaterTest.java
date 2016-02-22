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

import akka.actor.Props;
import akka.cluster.Cluster;
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.SpringAkkaExtensionId;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.test.TestActorRef;
import natalia.dymnikova.util.AutowireHelper;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.ApplicationContext;
import rx.Producer;

import static akka.actor.AddressFromURIString.parse;
import static java.util.Arrays.asList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Operator;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
import static natalia.dymnikova.test.TestActorProps.props;
import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 *
 */
public class LocalChildrenCreaterTest {
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
    private final SetFlow setFlow = SetFlow.newBuilder()
            .setFlowName("FlowName")
            .addAllStages(asList(
                    Stage.newBuilder()
                            .setAddress(testHost)
                            .setType(Supplier)
                            .setOperator(ByteString.EMPTY).build(),
                    Stage.newBuilder()
                            .setAddress(testHost)
                            .setType(Operator)
                            .setOperator(wrap(codec.pack((RemoteOperator<String, String>) (subscriber) -> subscriber))).build(),
                    Stage.newBuilder()
                            .setAddress(testHost1)
                            .setType(Operator)
                            .setOperator(wrap(codec.pack(new RemoteSubscriberImpl()))).build()
            ))
            .build();

    private final SetFlow setFlowWithLast = SetFlow.newBuilder()
            .setFlowName("FlowName")
            .addAllStages(asList(
                    Stage.newBuilder()
                            .setAddress(testHost)
                            .setType(Operator)
                            .setOperator(ByteString.EMPTY).build(),
                    Stage.newBuilder()
                            .setAddress(testHost)
                            .setType(Operator)
                            .setOperator(wrap(codec.pack((RemoteOperator<String, String>) (subscriber) -> subscriber))).build(),
                    Stage.newBuilder()
                            .setAddress(testHost)
                            .setOperator(wrap(codec.pack(new RemoteSubscriberImpl()))).build()
            ))
            .build();

    @SuppressWarnings("unchecked")
    private LocalSetFlow flow = new LocalSetFlow(
            setFlow,
            tmp -> {
            },
            () -> {
            },
            exc -> {
            }
    );

    @SuppressWarnings("unchecked")
    private LocalSetFlow flowWithLast = new LocalSetFlow(
            setFlowWithLast,
            tmp -> {
            },
            () -> {
            },
            exc -> {
            }
    );

    @Mock
    private FlowControlActor flowControlActor;

    @Spy
    private TestActorRef self;
    private Props stageActorProps = props();

    @InjectMocks
    private LocalChildrenCreater creater;

    @Test
    public void shouldNotCreateChildForLastStage() throws Exception {
        givenCreater(flow);

        creater.apply(flowControlActor);

        verify(flowControlActor, times(3)).actorOf(any(), any());
    }

    @Test
    public void shouldCreateChildForLastStage() throws Exception {
        givenCreater(flowWithLast);

        creater.apply(flowControlActor);

        verify(flowControlActor, times(4)).actorOf(any(), any());
    }

    private void givenCreater(final LocalSetFlow flow) {
        creater = new LocalChildrenCreater(flow);

        MockitoAnnotations.initMocks(this);

        doReturn(parse(testHost)).when(cluster).selfAddress();

        doReturn(stageActorProps).when(extension).props(same(IntermediateStageActor.class), any(), any(), any());
        doReturn(stageActorProps).when(extension).props(same(StartingJobActor.class), any());

        doReturn(self).when(flowControlActor).self();
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