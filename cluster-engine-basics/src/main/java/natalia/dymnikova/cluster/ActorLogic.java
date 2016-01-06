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

import akka.actor.*;
import akka.util.Timeout;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.util.MoreLogging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import scala.Function1;
import scala.Option;
import scala.PartialFunction;
import scala.concurrent.Future;
import scala.runtime.AbstractPartialFunction;
import scala.runtime.BoxedUnit;

import java.io.Serializable;
import java.util.function.Supplier;

import static com.google.protobuf.TextFormat.shortDebugString;
import static natalia.dymnikova.util.MoreLogging.lazyDebugString;

/**
 * Base class to encapsulate actor logic. Will be used to stuff an instance of {@link AbstractActor} with behaviour.
 * <p>
 * This class is needed to isolate application logic from an actor implementation because actors derived from
 * {@link AbstractActor} are not testable with simple unit test
 */
public class ActorLogic implements ActorAdapter {
    private static final Logger log = LoggerFactory.getLogger(ActorLogic.class);

    @Autowired
    protected AkkaExtension extension;

    private ActorAdapter adapter;

    public ActorLogic(final ActorAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void receive(final PartialFunction<Object, BoxedUnit> receive) {
        adapter.receive(new AbstractPartialFunction<Object, BoxedUnit>() {
            @Override
            public BoxedUnit apply(final Object x) {
                if (log.isDebugEnabled()) {
                    log.debug("Received message {} from {} to {}", logMessage(x), sender(), self());
                }

                try {
                    return receive.apply(x);
                } catch (final Throwable e) {
                    log.error(
                            "Error {}: '{}' while processing message {} from {} to {}",
                            e.getClass().getName(),
                            e.getMessage(),
                            logMessage(x),
                            sender(),
                            self(),
                            e
                    );

                    throw e;
                }
            }

            private Object logMessage(Object x) {
                final Object msg;
                if (x instanceof Message) {
                    msg = x.getClass().getName() + ": " + "{" + shortDebugString((Message) x) + "}";
                } else {
                    msg = x;
                }
                return msg;
            }

            @Override
            public boolean isDefinedAt(final Object x) {
                return receive.isDefinedAt(x);
            }
        });

    }

    @Override
    public void forward(final ActorRef actorRef, final Object msg) {
        adapter.forward(actorRef, msg);
    }

    @Override
    public ActorRef actorOf(final Props props) {
        return adapter.actorOf(props);
    }

    @Override
    public ActorRef actorOf(final Props props, final String name) {
        return adapter.actorOf(props, name);
    }

    @Override
    public String actorSystemName() {
        return adapter.actorSystemName();
    }

    @Override
    public Scheduler scheduler() {
        return adapter.scheduler();
    }

    @Override
    public ActorSelection actorSelection(final ActorPath actorPath) {
        return adapter.actorSelection(actorPath);
    }

    @Override
    public ActorRef self() {
        return adapter.self();
    }

    @Override
    public ActorRef sender() {
        return adapter.sender();
    }

    @Override
    public ActorRef parent() {
        return adapter.parent();
    }

    @Override
    public void stop(final ActorRef ref) {
        adapter.stop(ref);
    }

    @Override
    public void reply(final Supplier<Object> supplier) {
        adapter.reply(supplier);
    }

    @Override
    public void watch(final ActorRef actorRef) {
        adapter.watch(actorRef);
    }

    @Override
    public void unwatch(final ActorRef actorRef) {
        adapter.unwatch(actorRef);
    }

    @Override
    public void become(final PartialFunction<Object, BoxedUnit> behaviour) {
        adapter.become(behaviour);
    }

    @Override
    public void unbecome() {
        adapter.unbecome();
    }

    @Override
    public <T extends Serializable> Future<Object> ask(final ActorSelection selection, final T msg, final Timeout timeout) {
        return adapter.ask(selection, msg, timeout);
    }

    // callbacks

    public void preStart() throws Exception {
    }

    public SupervisorStrategy supervisorStrategy() {
        return SupervisorStrategy.defaultStrategy();
    }

    public void postStop() {
    }

    public void preRestart(final Throwable reason, final Option<Object> message) {
    }

    public void postRestart(final Throwable reason) {
    }

    public void unhandled(final Object message) {
    }
}