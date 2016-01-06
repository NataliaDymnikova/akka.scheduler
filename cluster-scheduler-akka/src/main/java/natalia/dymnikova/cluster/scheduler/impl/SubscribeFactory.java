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

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Completed;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Data;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import natalia.dymnikova.util.AutowireHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import natalia.dymnikova.configuration.ConfigValue;
import rx.Subscriber;

import java.io.Serializable;

import static natalia.dymnikova.util.MoreByteStrings.wrap;

/**
 * 
 */
@Component
public class SubscribeFactory {

    @Autowired
    private Codec codec;

    @Autowired
    private AutowireHelper helper;

    public Subscriber<Serializable> getSubscriber(
            final boolean isLast,
            final ActorRef self,
            final ActorSelection prevActor,
            final ActorSelection nextActor,
            final ActorRef parent
    ) {
        if (isLast) {
            return helper.autowire(new Subscriber<Serializable>() {

                @ConfigValue("natalia-dymnikova.scheduler.count")
                private int count = 1;

                private int currCount;

                @Override
                public void onCompleted() {
                    parent.tell(Completed.getDefaultInstance(), self);
                }

                @Override
                public void onError(final Throwable e) {
                    parent.tell(State.Error.newBuilder().setMessage(e.getMessage()).build(), self);
                }

                @Override
                public void onNext(final Serializable o) {
                    currCount++;
                    if (currCount >= count) {
                        prevActor.tell(Flow.More.newBuilder().setCount(count).build(), self);
                        currCount = 0;
                    }
                }
            });
        } else {
            return new Subscriber<Serializable>() {
                @Override
                public void onCompleted() {
                    nextActor.tell(Completed.getDefaultInstance(), self);
                }

                @Override
                public void onError(final Throwable e) {
                    parent.tell(State.Error.newBuilder().setMessage(e.getMessage()).build(), self);
                }

                @Override
                public void onNext(final Serializable o) {
                    nextActor.tell(Data.newBuilder().setData(wrap(codec.packObject(o))), self);
                }
            };
        }
    }
}
