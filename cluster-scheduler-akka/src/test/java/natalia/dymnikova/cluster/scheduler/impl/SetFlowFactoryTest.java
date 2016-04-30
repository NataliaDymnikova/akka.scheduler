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

import akka.actor.Address;
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.akka.Flow.StageType;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.StageContainer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static akka.actor.AddressFromURIString.parse;
import static com.google.protobuf.ByteString.EMPTY;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Merge;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Operator;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.Host0;
import static natalia.dymnikova.util.MoreFutures.immediateFuture;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 */
@RunWith(MockitoJUnitRunner.class)
public class SetFlowFactoryTest {

    private final GetAddressStrategyFactory strategyFactory = mock(GetAddressStrategyFactory.class);

    @Mock
    private FlowMerger flowMerger;

    @InjectMocks
    private final SetFlowFactory factory = new SetFlowFactory();

    @Before
    public void setUp() throws Exception {
        doReturn(new FindFirstGetAddressesStrategy()).when(strategyFactory).getAddressStrategy();
        //noinspection unchecked
        doAnswer(invocation -> ((List<Stage>) invocation.getArguments()[0]).get(0)).when(flowMerger).createMergeStages(any(), any(), any());
    }

    @Test
    public void shouldSetTypesForStages() throws Exception {
        final SetFlow flow = factory.makeFlow("test", empty(), singletonList(new StageContainer(
                singletonList(of(parse(Host0))), (RemoteSupplier<Object>) Object::new, EMPTY, Supplier, emptyList()
        )));

        assertThat(
                flow.getStage().getType(),
                is(Supplier)
        );
    }

    @Test
    public void shouldCreateFlowWithMerge() throws Exception {
        final List<StageContainer> stages = makeListStageContainers();
        factory.makeFlow("test", empty(), stages);

        verify(flowMerger).createMergeStages(any(), any(), any());
    }

    @Test
    public void shouldSetId() throws Exception {
        final StageContainer stage = new StageContainer(
                singletonList(of(parse(Host0))), (RemoteSupplier<Object>) Object::new, EMPTY, Supplier, emptyList()
        );
        stage.id = 0;
        final SetFlow flow = factory.makeFlow("test", empty(), singletonList(stage));

        assertThat(
                flow.getStage().getId(),
                is(stage.id)
        );
    }

    private List<StageContainer> makeListStageContainers() {
        final StageContainer stage1 = getStageContainer("host1", 1, Operator, emptyList());
        stage1.id = 2;
        final StageContainer mergeStage = getStageContainer("host3", 3, Merge, singletonList(stage1));
        mergeStage.id = 1;
        final StageContainer lastStage = getStageContainer("host4", 4, Operator, singletonList(mergeStage));
        lastStage.id = 0;
        return asList(
                lastStage, mergeStage, stage1
        );
    }

    private StageContainer getStageContainer(final String host, final int port, final StageType type, final List<StageContainer> previous) {
        return  new StageContainer(
                singletonList(of(Address.apply("akka", "natalia-dymnikova", host, port))),
                null,
                ByteString.EMPTY,
                type,
                previous
        );
    }
}