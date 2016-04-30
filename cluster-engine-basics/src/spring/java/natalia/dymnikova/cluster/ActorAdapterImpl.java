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

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.pattern.Patterns;
import akka.util.Timeout;
import natalia.dymnikova.cluster.util.ScalaToJava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.PartialFunction;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

import java.io.Serializable;
import java.util.Optional;
import java.util.function.Supplier;

import static scala.collection.JavaConversions.asJavaIterable;

/**
 * 
 */
public class ActorAdapterImpl extends AbstractActor implements ActorAdapter {
    private static final Logger log = LoggerFactory.getLogger(ActorAdapterImpl.class);

    private ActorLogic actorLogic;

    public void setActorLogic(final ActorLogic actorLogic) {
        this.actorLogic = actorLogic;
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return actorLogic.supervisorStrategy();
    }

    @Override
    public void preStart() throws Exception {
        log.debug("Starting {} at {}", actorLogic.getClass().getName(), context().self().path());
        actorLogic.preStart();
    }

    @Override
    public void postStop() throws Exception {
        log.debug("Stopping {} at {}", actorLogic.getClass().getName(), context().self().path());
        actorLogic.postStop();
    }

    @Override
    public void preRestart(final Throwable reason, final Option<Object> message) throws Exception {
        actorLogic.preRestart(reason, message);
    }

    @Override
    public void postRestart(final Throwable reason) throws Exception {
        actorLogic.postRestart(reason);
    }

    @Override
    public void unhandled(final Object message) {
        actorLogic.unhandled(message);
    }

    @Override
    public void forward(final ActorRef actorRef, final Object msg) {
        actorRef.forward(msg, context());
    }

    @Override
    public void forward(ActorSelection selection, Object msg) {
        selection.forward(msg, getContext());
    }

    @Override
    public ActorRef actorOf(final Props props) {
        return context().actorOf(props);
    }

    @Override
    public Optional<ActorRef> child(final String name) {
        return ScalaToJava.toJava(context().child(name));
    }

    @Override
    public Iterable<ActorRef> children() {
        return asJavaIterable(context().children());
    }

    @Override
    public ActorRef actorOf(final Props props, final String name) {
        return context().actorOf(props, name);
    }

    @Override
    public String actorSystemName() {
        return context().system().name();
    }

    @Override
    public SchedulerService scheduler() {
        return new SchedulerServiceImpl(
                context().system(), context().system().scheduler()
        );
    }

    @Override
    public ActorSelection actorSelection(final ActorPath actorPath) {
        return context().actorSelection(actorPath);
    }

    @Override
    public ActorRef parent() {
        return context().parent();
    }

    @Override
    public void stop(final ActorRef actorRef) {
        context().stop(actorRef);
    }

    @Override
    public void reply(final Supplier<Object> supplier) {
        try {
            getContext().sender().tell(supplier.get(), self());
        } catch (final RuntimeException e) {
            getContext().sender().tell(new Status.Failure(e), self());
        }
    }

    @Override
    public ActorRef watch(final ActorRef actorRef) {
        return context().watch(actorRef);
    }

    @Override
    public void unwatch(final ActorRef actorRef) {
        context().unwatch(actorRef);
    }

    @Override
    public void become(final PartialFunction<Object, BoxedUnit> behaviour) {
        context().become(behaviour);
    }

    @Override
    public void unbecome() {
        context().unbecome();
    }

    @Override
    public <T extends Serializable> Future<Object> ask(final ActorSelection selection, final T msg, final Timeout timeout) {
        return Patterns.ask(selection, msg, timeout);
    }

    @Override
    public String toString() {
        return actorLogic != null ? actorLogic.toString() : super.toString();
    }
}
