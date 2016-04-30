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
import akka.actor.Address;
import natalia.dymnikova.cluster.ActorPaths;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;

import static akka.actor.ActorPaths.fromString;
import static akka.actor.AddressFromURIString.apply;
import static java.lang.String.format;
import static natalia.dymnikova.cluster.scheduler.impl.FlowHelper.getStage;

/**
 * 
 */
public class NamingSchema {

    public static ActorPath remoteFlowControlActorPath(final SetFlow flow, final int stageIndex) {
        return remoteFlowControlActorPath(flow, getStage(flow, stageIndex).getAddress());
    }

    public static ActorPath remoteFlowControlActorPath(final SetFlow flow, final String addressStr) {
        final Address address = apply(addressStr);

        return fromString(
                ActorPaths.computePool(address) + "/" + flow.getFlowName()
        );
    }

    public static ActorPath remoteStageActorPath(final SetFlow flow, final int stageIndex) {
        return fromString(
                remoteFlowControlActorPath(flow, stageIndex) + "/" + stageName(stageIndex)
        );
    }

    public static String stageName(final int index) {
        return format("%03d", index);
    }
}
