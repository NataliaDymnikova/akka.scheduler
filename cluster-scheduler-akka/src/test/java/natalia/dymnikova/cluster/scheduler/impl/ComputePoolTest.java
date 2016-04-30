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
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RunCriteria;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import natalia.dymnikova.test.TestActorProps;
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
import rx.Subscriber;

import static natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.checkFlowMessage;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.setFlowMessage;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
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
public class ComputePoolTest {

    @Mock
    protected AkkaExtension extension;

    @Mock
    private AutowireHelper autowireHelper;

    @InjectMocks
    protected Codec codec = spy(Codec.class);

    private ActorAdapter adapter = spy(ActorAdapter.class);

    @InjectMocks
    private ComputePool computePool = new ComputePool(adapter);

    @Spy
    private TestActorRef sender;

    @Spy
    private TestActorRef self;

    @Spy
    private TestActorRef flowControlActorPropsRef;

    @Mock
    private ApplicationContext context;

    private Props flowControlActorProps = TestActorProps.props();

    @Before
    public void setUp() throws Exception {
        doReturn(sender).when(adapter).sender();
        doReturn(self).when(adapter).self();

        doReturn(mock(BasicChildrenCreator.class)).when(context).getBean(same(BasicChildrenCreator.class), isA(SetFlow.class));

        doReturn(flowControlActorProps).when(extension).props(same(FlowControlActor.class), isA(SetFlow.class), isA(BasicChildrenCreator.class));
        doReturn(flowControlActorPropsRef).when(adapter).actorOf(
                same(flowControlActorProps), any()
        );

        doAnswer(invocation -> invocation.getArguments()[0]).when(autowireHelper).autowire(any());
    }

    @Test
    public void shouldSendError() throws Exception {
        computePool.handle(checkFlowMessage(new TestOperator(false)));
        verify(sender).tell(isA(State.Error.class), eq(self));
    }

    @Test
    public void shouldSendErrorIfGetRunCriteriaThrowsException() throws Exception {
        computePool.handle(checkFlowMessage(new TestOperator(false) {
            @Override
            public RunCriteria getRunCriteria() {
                throw new RuntimeException("Expected");
            }
        }));
        verify(sender).tell(isA(State.Error.class), eq(self));
    }

    @Test
    public void shouldSendErrorIfFailedToAutowireBeans() throws Exception {
        doThrow(new RuntimeException("Expected")).when(autowireHelper).autowire(isA(TestOperator.class));
        computePool.handle(checkFlowMessage(new TestOperator(false)));
        verify(sender).tell(isA(State.Error.class), eq(self));
    }

    @Test
    public void shouldSendErrorIfRunCriteriaThrowsException() throws Exception {
        computePool.handle(checkFlowMessage(new TestOperator(false) {
            @Override
            public RunCriteria getRunCriteria() {
                throw new RuntimeException("Expected");
            }
        }));
        verify(sender).tell(isA(State.Error.class), eq(self));
    }

    @Test
    public void shouldSendOk() throws Exception {
        computePool.handle(checkFlowMessage(new TestOperator(true)));
        verify(sender).tell(isA(State.Ok.class), eq(self));
    }

    @Test
    public void shouldCreateFlowControlActor() throws Exception {
        final SetFlow flow = setFlowMessage(new TestOperator(true));
        computePool.handle(flow);

        verify(adapter).actorOf(flowControlActorProps, flow.getFlowName());
    }

    @Test
    public void shouldCreateDifferentFlowControlActorsForDifferentNamedFlows() throws Exception {
        final SetFlow flow1 = setFlowMessage(new TestOperator(true));
        final SetFlow flow2 = flow1.toBuilder().setFlowName("test-flow-2").build();
        computePool.handle(flow1);
        computePool.handle(flow2);

        verify(adapter).actorOf(flowControlActorProps, flow1.getFlowName());
        verify(adapter).actorOf(flowControlActorProps, flow2.getFlowName());
    }

    private static class TestOperator implements RemoteOperator<String, String> {

        private final boolean criteria;

        public TestOperator(final boolean criteria) {
            this.criteria = criteria;
        }

        @Override
        public RunCriteria getRunCriteria() {
            return () -> criteria;
        }

        @Override
        public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
            return subscriber;
        }
    }

}