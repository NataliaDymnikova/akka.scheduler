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
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.ActorPaths;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithSubscriber;
import rx.Producer;

import java.io.Serializable;

import static akka.actor.ActorPaths.fromString;
import static java.util.Arrays.asList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.CheckFlow;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Operator;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Supplier;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.stageName;
import static natalia.dymnikova.util.MoreByteStrings.wrap;

/**
 *
 */
public class MessagesHelper {

    public static final String FlowName = "test-flow";

    public static final String StageActorName0 = stageName(0);
    public static final String StageActorName1 = stageName(1);
    public static final String StageActorName2 = stageName(2);

    private static final Codec codec = new Codec();

    public static final String Host0 = "akka://system@host0:0";
    public static final String Host1 = "akka://system@host1:0";
    public static final String Host2 = "akka://system@host2:0";

    public static SetFlow flowMessage() {
        return flowMessage((RemoteOperator<String, String>) subscriber -> subscriber);
    }

    public static SetFlow flowMessage(final RemoteOperator<? extends Serializable,? extends Serializable> operator) {
        return flowMessage(operator, new RemoteOperatorWithSubscriber<>(new RemoteSubscriberImpl()));
    }

    public static SetFlow flowMessage(final RemoteSubscriber subscriber) {
        return flowMessage(s -> s, new RemoteOperatorWithSubscriber<>(new RemoteSubscriberImpl()));
    }

    public static SetFlow flowMessage(final RemoteOperator<?,?> operator, final RemoteOperator<?, ?> subscriber) {
        return SetFlow.newBuilder()
                .setFlowName(FlowName)
                .addAllStages(asList(
                        Stage.newBuilder()
                                .setAddress(Host0)
                                .setOperator(ByteString.EMPTY)
                                .setType(Supplier).build(),
                        Stage.newBuilder()
                                .setAddress(Host1)
                                .setOperator(wrap(codec.pack(operator)))
                                .setType(Operator).build(),
                        Stage.newBuilder()
                                .setAddress(Host2)
                                .setOperator(wrap(codec.pack(subscriber)))
                                .setType(Operator).build()
                ))
                .build();
    }

    public static CheckFlow checkFlowMessage(final RemoteOperator operator) {
        return CheckFlow.newBuilder()
                .setOperator(wrap(codec.pack(operator)))
                .build();
    }

    public static SetFlow setFlowMessage(final RemoteOperator operator) {
        return flowMessage(operator);
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
}
