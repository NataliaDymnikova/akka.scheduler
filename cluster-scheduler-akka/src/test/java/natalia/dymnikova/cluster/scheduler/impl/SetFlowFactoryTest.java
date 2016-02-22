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

import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.StageContainer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static akka.actor.AddressFromURIString.parse;
import static com.google.protobuf.ByteString.EMPTY;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.Host0;
import static natalia.dymnikova.util.MoreFutures.immediateFuture;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 */
@RunWith(MockitoJUnitRunner.class)
public class SetFlowFactoryTest {

    private final GetAddressStrategyFactory  strategyFactory = mock(GetAddressStrategyFactory.class);

    @InjectMocks
    private final SetFlowFactory factory = new SetFlowFactory();

    @Test
    public void shouldSetTypesForStages() throws Exception {

        doReturn(new FindFirstGetAddressesStrategy()).when(strategyFactory).getAddressStrategy();

        final SetFlow flow = factory.makeFlow("test", singletonList(new StageContainer(
                immediateFuture(singletonList(of(parse(Host0)))), (RemoteSupplier<Object>) Object::new, EMPTY, Supplier
        )));

        assertThat(
                flow.getStages(0).getType(),
                is(Supplier)
        );
    }


}