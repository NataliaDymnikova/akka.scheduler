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
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import scala.concurrent.Future;

import java.io.Serializable;

/**
 * 
 */
@Component
public class ActorSystemAdapterImpl implements ActorSystemAdapter {
    @Autowired
    private ActorSystem actorSystem;

    public ActorSelection actorSelection(final ActorPath actorPath) {
        return actorSystem.actorSelection(actorPath);
    }

    public <T extends Serializable> Future<Object> ask(final ActorSelection selection, final T msg, final Timeout timeout) {
        return akka.pattern.Patterns.ask(selection, msg, timeout);
    }

    @Override
    public ActorRef actorOf(final Props props) {
        return actorSystem.actorOf(props);
    }

    @Override
    public ActorRef actorOf(final Props props, final String name) {
        return actorSystem.actorOf(props, name);
    }

    @Override
    public String actorSystemName() {
        return actorSystem.name();
    }

    @Override
    public SchedulerService scheduler() {
        return new SchedulerServiceImpl(
                actorSystem, actorSystem.scheduler()
        );
    }
}
