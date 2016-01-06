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

package natalia.dymnikova.cluster;

import akka.actor.ActorPath;
import akka.actor.Address;

/**
 * 
 */
public class ActorPaths {
    public static final String COMPUTE_POOL = "compute-pool";

    public static ActorPath agent(final Address address) {
        return akka.actor.ActorPaths.fromString(
            address + "/user/agent"
        );
    }

    public static ActorPath parser(final Address address) {
        return akka.actor.ActorPaths.fromString(
            address + "/user/parser"
        );
    }

    public static ActorPath fileStore(final Address address) {
        return akka.actor.ActorPaths.fromString(
                address + "/user/file-store"
        );
    }

    public static ActorPath indexer(final Address address) {
        return akka.actor.ActorPaths.fromString(
                address + "/user/indexer"
        );
    }

    public static ActorPath computePool(final Address address) {
        return akka.actor.ActorPaths.fromString(
                address + "/user/" + COMPUTE_POOL
        );
    }
}
