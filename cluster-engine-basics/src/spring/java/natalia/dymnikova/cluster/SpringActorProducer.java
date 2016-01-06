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

import akka.actor.Actor;
import akka.actor.IndirectActorProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import static java.lang.System.arraycopy;

public class SpringActorProducer implements IndirectActorProducer {
    private static final Logger log = LoggerFactory.getLogger(SpringActorProducer.class);

    private final Class<? extends ActorLogic> actorClass;
    private final Object[] arguments;

    private static volatile ApplicationContext applicationContext;

    public static void initialize(final ApplicationContext applicationContext) {
        SpringActorProducer.applicationContext = applicationContext;
    }

    public SpringActorProducer(final Class<? extends ActorLogic> actorClass, final Object... arguments) {
        this.actorClass = actorClass;
        this.arguments = arguments;
    }

    @Override
    public Actor produce() {
        final ActorAdapterImpl actorAdapter = new ActorAdapterImpl();

        final Object[] arguments = new Object[this.arguments.length + 1];
        arguments[0] = actorAdapter;
        arraycopy(this.arguments, 0, arguments, 1, this.arguments.length);

        log.debug("Producing actor with logic {}", actorClass.getName());
        final ActorLogic actorLogic = applicationContext.getBean(actorClass, arguments);

        actorAdapter.setActorLogic(actorLogic);

        return actorAdapter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Actor> actorClass() {
        return ActorAdapterImpl.class;
    }
}
