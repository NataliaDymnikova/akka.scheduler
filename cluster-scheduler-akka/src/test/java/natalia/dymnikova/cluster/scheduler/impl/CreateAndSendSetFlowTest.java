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
import akka.actor.ActorSelection;
import akka.actor.Address;
import akka.cluster.Cluster;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteMergeOperator;
import natalia.dymnikova.cluster.scheduler.RemoteMergeOperatorImpl;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.StageContainer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.Producer;
import rx.Subscriber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

import static akka.actor.AddressFromURIString.apply;
import static com.google.protobuf.ByteString.EMPTY;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.ActorPaths.computePool;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Local;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Merge;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Operator;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static rx.Observable.just;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class CreateAndSendSetFlowTest {

    @Mock
    private GetAddressStrategyFactory addressStrategyFactory;

    @Mock
    private SetFlowDestinationFactory flowDestinationFactory;

    @Mock
    private FlowMerger flowMerger;

    @InjectMocks
    private SetFlowFactory setFlowFactory = spy(new SetFlowFactory());

    @InjectMocks
    private CreateAndSendSetFlow creatorSetFlowTest = new CreateAndSendSetFlow();

    @Mock
    private Cluster cluster;

    @Spy
    private ConverterAddresses converterAddresses = new ConverterAddresses();

    private Map<ActorSelection, Address> flowDestinations = new HashMap<>();
    private final RemoteSupplier<Observable<String>> stage0 = new RemoteSupplier<Observable<String>>() {
        @Override
        public Observable<String> get() {
            return just("a", "b", "c");
        }
    };

    private final RemoteOperator<String, String> stage1 = new TestRemoteOperatorImpl();

    private final RemoteSubscriber stage9 = new RemoteSubscriberImpl();

    private final ActorSelection selection0 = mock(ActorSelection.class, "selection0");
    private final ActorPath path0 = computePool(apply("akka.tcp://system@host0:0"));

    private final ActorSelection selection1 = mock(ActorSelection.class, "selection1");
    private final ActorPath path1 = computePool(apply("akka.tcp://system@host1:0"));

    private final ActorSelection selection9 = mock(ActorSelection.class, "selection9");
    private final ActorPath path9 = computePool(apply("akka.tcp://system@host2:0"));
    private List<StageContainer> stages = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        setupSelection(selection0, path0, RemoteSupplier.class);
        setupSelection(selection1, path1, RemoteOperator.class);
        setupSelection(selection9, path9, RemoteSubscriber.class);

        doReturn(path9.address()).when(cluster).selfAddress();
        doReturn(new FindFirstGetAddressesStrategy()).when(addressStrategyFactory).getAddressStrategy();
        doAnswer(i -> new ArrayList<>(flowDestinations.entrySet())).when(flowDestinationFactory).buildDestinations(isA(Flow.SetFlow.class));
    }

    public void setupSelection(final ActorSelection s, final ActorPath p, final Class<? extends Remote> c) {
        doReturn(p.toString()).when(s).pathString();
        doReturn(p).when(s).anchorPath();

        flowDestinations.put(s, s.anchorPath().address());
    }

    @Test
    public void shouldSendSetFlowToComputePoolActorsForRemoteSubscriber() throws Exception {
        setupSelection(selection9, path0, RemoteSubscriber.class);
        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage1, stage9), "flow", empty());

        verify(selection9, times(1)).tell(isA(Flow.SetFlow.class), any());
    }

    @Test(expected = NoSuchElementException.class)
    public void shouldCallOnErrorWhenFailsToFindCandidateForRemoteSubscriber() throws Exception {
        creatorSetFlowTest.sendSetFlow(createStagesError(), "flow", empty());
    }

    @Test
    public void shouldSendSetFlowToSupplierAddress() throws Exception {
        creatorSetFlowTest.sendSetFlow(createStages(stage0, converterAddresses.toAkkaAddress(createUnresolved("0.0.0.0", 1)), stage9), "flow", empty());

        final ArgumentCaptor<Flow.SetFlow> captor = ArgumentCaptor.forClass(Flow.SetFlow.class);
        verify(selection0, times(1)).tell(captor.capture(), any());

        final Flow.SetFlow setFlow = captor.getValue();
        assertThat(getStageList(setFlow.getStage()).get(1).getAddress(), is("akka.tcp://" + converterAddresses.actorSystemName + "@0.0.0.0:1"));
    }

    @Test
    public void shouldSendFlowWithoutSubscriber() throws Exception {
        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage1, stage9), "flow", empty());

        verify(selection0).tell(isA(Flow.SetFlow.class), any());
        verify(selection1).tell(isA(Flow.SetFlow.class), any());
    }

    @Test
    public void shouldSendFullSetFlowWhenCallbacks() throws Exception {
        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage1, mock(Subscriber.class)), "flow", empty());

        final ArgumentCaptor<Flow.SetFlow> captor = ArgumentCaptor.forClass(Flow.SetFlow.class);
        verify(selection1).tell(captor.capture(), any());

        assertThat(
                getStageList(captor.getValue().getStage()).size(),
                is(3)
        );
    }

    @Test
    public void shouldSendFlowOnlyOncePerAddress() throws Exception {
        setupSelection(selection0, path0, RemoteSupplier.class);
        setupSelection(selection0, path0, RemoteOperator.class);
        setupSelection(selection1, path1, RemoteSubscriber.class);

        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage1, stage9), "flow", empty());

        verify(selection0, times(1)).tell(isA(Flow.SetFlow.class), any());
        verify(selection1, times(1)).tell(isA(Flow.SetFlow.class), any());
    }

    @Test
    public void flowStagesShouldHaveTypeSet() throws Exception {
        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage1, stage9), "flow", empty());

        final ArgumentCaptor<Flow.SetFlow> captor = ArgumentCaptor.forClass(Flow.SetFlow.class);
        verify(selection0, times(1)).tell(captor.capture(), any());

        final Flow.SetFlow setFlow = captor.getValue();
        assertThat(getStageList(setFlow.getStage()).get(1).getType(), is(Operator));
        assertThat(getStageList(setFlow.getStage()).get(0).getType(), is(Operator));
    }

    @Test
    public void shouldSendSetFlowToAddress() throws Exception {
        setupSelection(selection9, path0, RemoteSubscriber.class);
        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage9, converterAddresses.toAkkaAddress(createUnresolved("0.0.0.0", 1))), "flow", empty());

        final ArgumentCaptor<Flow.SetFlow> captor = ArgumentCaptor.forClass(Flow.SetFlow.class);
        verify(selection9, times(1)).tell(captor.capture(), any());

        final Flow.SetFlow setFlow = captor.getValue();
        assertThat(getStageList(setFlow.getStage()).get(0).getAddress(), is("akka.tcp://" + converterAddresses.actorSystemName + "@0.0.0.0:1"));
    }

    @Test
    public void shouldSendSetFlowToOperatorAddress() throws Exception {
        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage1, converterAddresses.toAkkaAddress(createUnresolved("0.0.0.0", 1)), stage9), "flow", empty());

        final ArgumentCaptor<Flow.SetFlow> captor = ArgumentCaptor.forClass(Flow.SetFlow.class);
        verify(selection1, times(1)).tell(captor.capture(), any());

        final Flow.SetFlow setFlow = captor.getValue();
        assertThat(getStageList(setFlow.getStage()).get(1).getAddress(), is("akka.tcp://" + converterAddresses.actorSystemName + "@0.0.0.0:1"));
    }

    @Test
    public void shouldSendLocalSetFlow() throws Exception {
        converterAddresses.actorSystemName = "system";
        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage1, mock(Subscriber.class)), "flow", empty());

        verify(selection9).tell(isA(LocalSetFlow.class), any());
    }

    @Test
    public void shouldSendLocalIntermidiateStage() throws Exception {
        converterAddresses.actorSystemName = "system";
        creatorSetFlowTest.sendSetFlow(createStages(stage0, stage1, stage9), "flow", empty());

        verify(selection9).tell(isA(LocalSetFlow.class), any());
    }

    @Test
    public void shouldSendMergedSetFlow() throws Exception {
        doReturn(mergedOperator()).when(flowMerger).createMergeStages(anyList(), any(StageContainer.class), any(Optional.class));

        creatorSetFlowTest.sendSetFlow(createStagesWithMerge(stage0, stage1, stage9, new RemoteMergeOperatorImpl<>()), "flow", empty());

        verify(selection0).tell(isA(Flow.SetFlow.class), any());
    }

    @Test
    public void shouldSendMergedSetFlowWithLocalStages() throws Exception {
        doReturn(mergedOperatorWithLocalSupplier()).when(flowMerger).createMergeStages(anyList(), any(StageContainer.class), any(Optional.class));

        final Subscriber subscriber = mock(Subscriber.class);
        creatorSetFlowTest.sendSetFlow(createStagesWithMergeAndLocal(stage0, stage1, subscriber, new RemoteMergeOperatorImpl<>()), "flow", empty());

        verify(selection9).tell(isA(LocalSetFlow.class), any());
    }

    @Test
    public void shouldAddUniqueIdToStageContainers() throws Exception {
        final StageContainer stages = createStages(stage0, stage1, stage9);
        creatorSetFlowTest.sendSetFlow(stages, "flow", empty());

        assertThat(
                stages.getStages().stream().map(s -> s.id).distinct().count(),
                is((long) stages.getStages().size())
        );
    }

    private StageContainer createStagesWithMergeAndLocal(final Supplier<Observable<String>> supplier,
                                                         final RemoteOperator<String, String> operator,
                                                         final Subscriber subscriber,
                                                         final RemoteMergeOperatorImpl<Serializable> merge) {
        return local("akka.tcp://system@host2:0", subscriber,
                merge("akka.tcp://system@host2:0", merge,
                        operator("akka.tcp://system@host1:0", operator,
                                local("akka.tcp://system@host0:0", supplier)
                        ),
                        operator("akka.tcp://system@host1:0", operator,
                                local("akka.tcp://system@host0:0", supplier)
                        )
                )
        );
    }

    private StageContainer local(final String address, final Supplier subscriber, final StageContainer... stages) {
        return new StageContainer(address(address), subscriber, EMPTY, Local, asList(stages));
    }

    private StageContainer local(final String address, final Subscriber subscriber, final StageContainer... stages) {
        return new StageContainer(address(address), subscriber, EMPTY, Local, asList(stages));
    }

    private Stage mergedOperator() {
        final Stage.Builder stageOperator = Stage.newBuilder()
                .setType(Operator)
                .addStages(Stage.newBuilder()
                        .setType(Supplier)
                );

        final Stage.Builder stageMerge = Stage.newBuilder()
                .setType(Merge)
                .addStages(stageOperator);

        return Stage.newBuilder()
                .setType(Merge)
                .addStages(stageMerge)
                .addStages(stageMerge)
                .build();
    }

    private Stage mergedOperatorWithLocalSupplier() {
        final Stage.Builder stageOperator = Stage.newBuilder()
                .setType(Operator)
                .addStages(Stage.newBuilder()
                        .setType(Local)
                );

        final Stage.Builder stageMerge = Stage.newBuilder()
                .setType(Merge)
                .addStages(stageOperator);

        return Stage.newBuilder()
                .setType(Merge)
                .addStages(stageMerge)
                .addStages(stageMerge)
                .build();
    }

    private StageContainer createStagesWithMerge(final RemoteSupplier<Observable<String>> supplier,
                                                 final RemoteOperator<String, String> operator,
                                                 final RemoteSubscriber subscriber,
                                                 final RemoteMergeOperator merge) {
        return operator("akka.tcp://system@host2:0", subscriber,
                merge("akka.tcp://system@host2:0", merge,
                        operator("akka.tcp://system@host1:0", operator,
                                supplier("akka.tcp://system@host0:0", supplier)
                        ),
                        operator("akka.tcp://system@host1:0", operator,
                                supplier("akka.tcp://system@host0:0", supplier)
                        )
                )
        );
    }

    private StageContainer merge(final String address, final RemoteMergeOperator merge, final StageContainer... stageContainers) {
        return new StageContainer(address(address), merge, EMPTY, Merge, asList(stageContainers));
    }

    private StageContainer createStages(final RemoteSupplier supplier, final RemoteOperator operator, final RemoteSubscriber subscriber) {
        return operator("akka.tcp://system@host2:0", subscriber,
                operator("akka.tcp://system@host1:0", operator,
                        supplier("akka.tcp://system@host0:0", supplier)
                )
        );
    }

    private StageContainer supplier(final String address, final Object subscriber, final StageContainer... children) {
        return supplier(address(address), subscriber, children);
    }

    private StageContainer supplier(final List<Optional<Address>> candidates,
                                    final Object subscriber,
                                    final StageContainer... children) {
        return new StageContainer(candidates, subscriber, EMPTY, Supplier, asList(children));
    }

    private StageContainer operator(final List<Optional<Address>> candidates,
                                    final Object subscriber,
                                    final StageContainer... children) {
        return new StageContainer(candidates, subscriber, EMPTY, Operator, asList(children));
    }

    private StageContainer operator(final String address, final Object subscriber, final StageContainer... children) {
        List<Optional<Address>> candidates = address(address);
        return operator(candidates, subscriber, children);
    }

    private List<Optional<Address>> address(final String address) {
        return singletonList(of(apply(address)));
    }

    private StageContainer createStages(final RemoteSupplier supplier, final Address address, final RemoteSubscriber subscriber) {
        return operator("akka.tcp://system@host2:0", subscriber,
                supplier(address.toString(), supplier)
        );
    }

    private StageContainer createStages(final RemoteSupplier supplier, final RemoteOperator operator, final Subscriber subscriber) {
        return operator("akka.tcp://system@host2:0", subscriber,
                operator("akka.tcp://system@host1:0", operator,
                        supplier("akka.tcp://system@host0:0", supplier)
                )
        );
    }

    private StageContainer createStagesError() {
        return operator(emptyList(), stage9,
                supplier(emptyList(), null)
        );
    }

    private StageContainer createStages(final RemoteSupplier<Observable<String>> supplier, final RemoteSubscriber subscriber, final Address address) {
        return operator(address.toString(), subscriber,
                supplier("akka.tcp://system@host0:0", supplier)
        );
    }

    private StageContainer createStages(final RemoteSupplier<Observable<String>> supplier, final RemoteOperator<String, String> operator, final Address address, final RemoteSubscriber subscriber) {
        return operator("akka.tcp://system@host2:0", subscriber,
                operator(address.toString(), operator,
                        supplier("akka.tcp://system@host0:0", supplier)
                )
        );
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

    private static class TestRemoteOperatorImpl implements RemoteOperator<String, String> {
        @Override
        public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
            return subscriber;
        }
    }

    private List<Stage> getStageList(final Stage stage) {
        final List<Stage> stages = new ArrayList<>();
        stages.add(stage);
        stages.addAll(stage.getStagesList().stream().flatMap(st -> getStageList(st).stream()).collect(toList()));
        return stages;
    }
}