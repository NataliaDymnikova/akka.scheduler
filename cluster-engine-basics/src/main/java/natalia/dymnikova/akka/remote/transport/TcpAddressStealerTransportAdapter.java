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
import akka.remote.transport.AbstractTransportAdapter;
import akka.remote.transport.AkkaProtocolTransport;
import akka.remote.transport.AssociationHandle;
import akka.remote.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

import java.util.Optional;

import static akka.dispatch.ExecutionContexts.global;
import static java.util.Optional.empty;

/**
 */
public class TcpAddressStealerTransportAdapter extends AbstractTransportAdapter implements Transport.AssociationEventListener {
    private static final Logger log = LoggerFactory.getLogger(TcpAddressStealerTransportAdapter.class);

    private ExtendedActorSystem system;
    private volatile Optional<AssociationEventListener> upstreamListener = empty();

    public TcpAddressStealerTransportAdapter(final Transport wrappedTransport, final ExtendedActorSystem system) {
        super(wrappedTransport, system.dispatcher());
        this.system = system;

        log.debug("Created for transport {} and system {}", wrappedTransport, system);
    }

    @Override
    public int maximumOverhead() {
        return AkkaProtocolTransport.AkkaOverhead();
    }

    @Override
    public Future<AssociationEventListener> interceptListen(final Address tcpAddress, final Future<AssociationEventListener> f) {
        TcpTransportAddressStealer.instance.get(system)
                .stealPromise()
                .success(tcpAddress);

        f.onComplete(new AbstractFunction1<Try<AssociationEventListener>, Object>() {
            @Override
            public Object apply(final Try<AssociationEventListener> listener) {
                final AssociationEventListener value = listener.get();

                upstreamListener = Optional.of(value);
                return value;
            }
        }, global());

        return scala.concurrent.Future$.MODULE$.successful(this);
    }

    @Override
    public void interceptAssociate(final Address remoteAddress, final Promise<AssociationHandle> statusPromise) {
        statusPromise.completeWith(
                wrappedTransport().associate(remoteAddress)
        );
    }

    @Override
    public String addedSchemeIdentifier() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Address augmentScheme(final Address address) {
        return address;
    }

    @Override
    public String augmentScheme(final String originalScheme) {
        return originalScheme;
    }

    @Override
    public Address removeScheme(final Address address) {
        return address;
    }

    @Override
    public String removeScheme(final String scheme) {
        return scheme;
    }

    @Override
    public void notify(final AssociationEvent ev) {
        upstreamListener.ifPresent(l -> l.notify(ev));
    }
}
