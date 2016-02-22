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
import akka.japi.Pair;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 *
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class OutSubscriberFactory {

    @Autowired
    private Codec codec;

    private final BiConsumer<Pair<ActorSelection, ActorRef>, ActorRef> onCompleted;

    public OutSubscriberFactory(final BiConsumer<Pair<ActorSelection, ActorRef>, ActorRef> onCompleted) {
        this.onCompleted = onCompleted;
    }

    public SubscriberWithMore getOutSubscriber(final ActorSelection nextActor,
                                               final ActorRef parent,
                                               final ActorRef self,
                                               final long onStartCount) {
        return new SubscriberWithMore() {
            @Override
            public void onStart() {
                request(onStartCount);
            }

            @Override
            public void onCompleted() {
                onCompleted.accept(Pair.apply(nextActor, parent), self);
            }

            @Override
            public void onError(final Throwable e) {
                self.tell(new HandleException(e), self);
            }

            @Override
            public void onNext(final Serializable serializable) {
                nextActor.tell(Flow.Data.newBuilder().setData(wrap(codec.packObject(serializable))).build(), self);
            }
        };
    }
}
