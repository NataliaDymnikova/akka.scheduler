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

import akka.actor.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.util.streamex.StreamEx;

/**
 * 
 */
@Component
public class AutostartActorsBeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(AutostartActorsBeanPostProcessor.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ActorSystem actorSystem;

    @Autowired
    private SpringAkkaExtensionId.AkkaExtension extension;

    @EventListener(ContextStartedEvent.class)
    public void onStart() {
        StreamEx.<String>of(applicationContext.getBeanNamesForAnnotation(
                AutostartActor.class
        )).map(applicationContext::getType)
                .filter(ActorLogic.class::isAssignableFrom)
                .mapToEntry(type -> (Class<? extends ActorLogic>) type, type -> type.getAnnotation(AutostartActor.class))
                .peek(e -> log.debug("Discovered autostart actor {}", e.getKey().getName()))
                .mapToValue((type, annotation) -> annotation.value())
                .mapToValue((type, path) -> actorSystem.actorOf(extension.props(type), path))
                .forKeyValue((type, ref) -> log.info("Started actor {} of type {}", ref.path(), type.getName()));
    }
}
