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
import akka.actor.Address;
import akka.cluster.Cluster;
import natalia.dymnikova.cluster.ActorPaths;
import natalia.dymnikova.cluster.ActorSystemAdapter;
import natalia.dymnikova.cluster.SpringAkkaExtensionId;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State.Error;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State.Ok;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import scala.concurrent.Future$;

import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.test.AkkaMembersHelper.givenMembers;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * 
 */
@RunWith(MockitoJUnitRunner.class)
public class NodeSearcherTest {

    @Mock
    protected SpringAkkaExtensionId.AkkaExtension extension;

    @Mock
    private Cluster cluster;

    @Mock
    private Codec codec;

    @Mock
    private ActorSystemAdapter adapter;

    @Mock
    private RolesChecker checker;

    @InjectMocks
    private final NodeSearcher searcher = new NodeSearcher();
    private ActorSelection selection3 = mock(ActorSelection.class, "selection3");

    private final Address address1 = Address.apply("akka", "system", "host1", 1000);
    private final Address address2 = Address.apply("akka", "system", "host2", 1000);
    private final Address address3 = Address.apply("akka", "system", "host3", 1000);

    @Before
    public void setUp() throws Exception {
        givenMembers(cluster, mb -> mb
                .member(m -> m
                        .withAddress(address1)
                        .withRole("compute")

                )
                .member(m -> m
                        .withAddress(address2)
                        .withRole("compute")
                )
                .member(m -> m
                        .withAddress(address3)
                        .withRole("compute")
                )
        );

        doReturn(future(Ok.newBuilder().build())).when(adapter).ask(
                any(),
                isA(Flow.CheckFlow.class),
                any()
        );

        doReturn(mock(ActorSelection.class, "selection1")).when(adapter).actorSelection(ActorPaths.computePool(address1));
        doReturn(mock(ActorSelection.class, "selection2")).when(adapter).actorSelection(ActorPaths.computePool(address2));
        doReturn(selection3).when(adapter).actorSelection(ActorPaths.computePool(address3));

        doReturn(true).when(checker).check(any(), any());
    }

    public scala.concurrent.Future<Object> future(Object result) {
        return Future$.MODULE$.successful(result);
    }

    public scala.concurrent.Future<Object> failedFuture(Throwable t) {
        return Future$.MODULE$.failed(t);
    }

    @Test
    public void shouldReturnListOfComputePoolsWhichReadyToExecuteOperator() throws Exception {
        final RemoteOperator<String, String> operator = (RemoteOperator<String, String>) subscriber -> subscriber;
        Assert.assertThat(
                searcher.search(operator).get(1, TimeUnit.SECONDS).stream()
                        .map(o -> o.map(Address::toString).orElse(""))
                        .collect(toList()),
                containsInAnyOrder(address1.toString(), address2.toString(), address3.toString())
        );
    }

    @Test
    public void shouldReturnListOfComputePoolsWithOneEmptyWhenError() throws Exception {
        final RemoteOperator<String, String> operator = (RemoteOperator<String, String>) subscriber -> subscriber;

        doReturn(future(Error.newBuilder().build())).when(adapter).ask(
                same(selection3),
                isA(Flow.CheckFlow.class),
                any()
        );

        Assert.assertThat(
                searcher.search(operator).get(1, TimeUnit.SECONDS).stream()
                        .map(o -> o.map(Address::toString).orElse(""))
                        .collect(toList()),
                containsInAnyOrder(address1.toString(), address2.toString(), "")
        );
    }

    @Test
    public void shouldReturnListOfComputePoolsWithOneEmptyWhenException() throws Exception {
        final RemoteOperator<String, String> operator = (RemoteOperator<String, String>) subscriber -> subscriber;

        doReturn(failedFuture(new Exception("Expected"))).when(adapter).ask(
                same(selection3),
                isA(Flow.CheckFlow.class),
                any()
        );

        Assert.assertThat(
                searcher.search(operator).get(1, TimeUnit.SECONDS).stream()
                        .map(o -> o.map(Address::toString).orElse(""))
                        .collect(toList()),
                containsInAnyOrder(address1.toString(), address2.toString(), "")
        );
    }

    @Test
    public void shouldReturnEmptyCollectionForFalseChecker() throws Exception {
        doReturn(false).when(checker).check(any(), any());

        final RemoteOperator<String, String> operator = (RemoteOperator<String, String>) subscriber -> subscriber;
        Assert.assertThat(
                searcher.search(operator).get(1, TimeUnit.SECONDS).stream()
                        .map(o -> o.map(Address::toString).orElse(""))
                        .collect(toList()),
                emptyCollectionOf(String.class)
        );
    }

}