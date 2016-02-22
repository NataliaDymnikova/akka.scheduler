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

package natalia.dymnikova.akka.remote.transport;

import akka.actor.Address;
import akka.actor.ExtendedActorSystem;
import akka.remote.RemoteActorRefProvider;
import akka.remote.transport.AssociationHandle;
import akka.remote.transport.Transport;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;
import scala.concurrent.Future;
import scala.concurrent.Future$;
import scala.concurrent.Promise;
import scala.runtime.AbstractFunction1;

import static akka.dispatch.ExecutionContexts.global;

/**
 */
public class VirtualNodeTransport implements Transport {
    private static final Logger log = LoggerFactory.getLogger(VirtualNodeTransport.class);

    private static final ConfigRenderOptions renderAsJson = ConfigRenderOptions.concise()
            .setJson(true)
            .setComments(false)
            .setOriginComments(false)
            .setFormatted(false);

    private Address address;

    private ExtendedActorSystem system;
    private Config config;

    final Promise<AssociationEventListener> associationEventListenerPromise = new scala.concurrent.impl.Promise.DefaultPromise<>();;


    public VirtualNodeTransport(final ExtendedActorSystem system, final Config config) {
        this.system = system;
        this.config = config;

        log.debug("Created for system {}: {}", system.name(), config.root().render(renderAsJson));
    }

    @Override
    public String schemeIdentifier() {
        return "virtual";
    }

    @Override
    public boolean isResponsibleFor(final Address address) {
        log.debug("[{}] isResponsibleFor({})", system.name(), address);
        return false;
    }

    @Override
    public int maximumPayloadBytes() {
        return 32 * 1024;
    }

    @Override
    public Future<Tuple2<Address, Promise<AssociationEventListener>>> listen() {
        return TcpTransportAddressStealer.instance.get(system)
                .stealPromise()
                .future()
                .map(new AbstractFunction1<Address, Address>() {
                    @Override
                    public Address apply(final Address tcpAddress) {
                        log.debug("Captured tcp transport address {}", tcpAddress);
                        return Address.apply("virtual", system.name(), tcpAddress.host(), tcpAddress.port());
                    }
                }, global())
                .map(new AbstractFunction1<Address, Tuple2<Address, Promise<AssociationEventListener>>>() {
                    @Override
                    public Tuple2<Address, Promise<AssociationEventListener>> apply(final Address virtualAddress) {
                        address = virtualAddress;

                        log.debug("Returning  {}", virtualAddress);
                        return Tuple2.apply(virtualAddress, associationEventListenerPromise);
                    }
                }, global());
    }

    @Override
    public Future<AssociationHandle> associate(final Address remoteAddress) {
        final Promise<AssociationHandle> promise = new scala.concurrent.impl.Promise.DefaultPromise<>();
        return promise.future();
    }

    @Override
    public Future<Object> shutdown() {
        return Future$.MODULE$.successful(new Object());
    }

    @Override
    public Future<Object> managementCommand(final Object cmd) {
        return Future$.MODULE$.successful(new Object());
    }
}
