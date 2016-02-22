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

import akka.actor.AbstractExtensionId;
import akka.actor.Address;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import scala.concurrent.Promise;

public class TcpTransportAddressStealer extends AbstractExtensionId<TcpTransportAddressStealer.TcpTransportAddressStealerExtension> {

    public static TcpTransportAddressStealer instance = new TcpTransportAddressStealer();

    @Override
    public TcpTransportAddressStealerExtension createExtension(final ExtendedActorSystem system) {
        return new TcpTransportAddressStealerExtension();
    }

    public static class TcpTransportAddressStealerExtension implements Extension {
        private Promise<Address> promise = new scala.concurrent.impl.Promise.DefaultPromise<>();

        public Promise<Address> stealPromise() {
            return promise;
        }
    }
}
