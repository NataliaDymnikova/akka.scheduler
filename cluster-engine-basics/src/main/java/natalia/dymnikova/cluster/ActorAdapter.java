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
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.util.Timeout;
import scala.PartialFunction;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

import java.io.Serializable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * wraps akka actor and allows to create testable logic
 */
public interface ActorAdapter {
    void receive(final PartialFunction<Object, BoxedUnit> receive);

    void forward(final ActorRef actorRef, final Object msg);

    void forward(final ActorSelection selection, final Object msg);

    ActorRef self();

    ActorRef sender();

    ActorRef parent();

    void stop(ActorRef self);

    void reply(Supplier<Object> supplier);

    ActorRef watch(ActorRef actorRef);

    void unwatch(ActorRef actorRef);

    void become(PartialFunction<Object, BoxedUnit> behaviour);

    void unbecome();

    ActorSelection actorSelection(ActorPath actorPath);

    <T extends Serializable> Future<Object> ask(ActorSelection selection, T msg, Timeout timeout);

    ActorRef actorOf(final Props props);

    Optional<ActorRef> child(String name);

    Iterable<ActorRef> children();

    ActorRef actorOf(final Props props, final String name);

    String actorSystemName();

    SchedulerService scheduler();
}
