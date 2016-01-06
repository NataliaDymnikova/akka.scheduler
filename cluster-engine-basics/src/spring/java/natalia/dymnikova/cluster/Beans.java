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
import akka.cluster.Cluster;
import com.typesafe.config.Config;
import natalia.dymnikova.configuration.ConfiguredEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;
import scala.concurrent.duration.Duration;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 
 */
@Configuration
public class Beans {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ConfiguredEnvironment environment;

    @Bean
    @Lazy
    public ActorSystem actorSystem() {
        final Config config = environment.getConfig();
        SpringActorProducer.initialize(applicationContext);
        actorSystemShutdown();
        return ActorSystem.create(
                config.getString("natalia-dymnikova.actor-system.name"),
                config
        );
    }

    @Bean
    @Lazy
    public ActorSystemShutdown actorSystemShutdown() {
        return new ActorSystemShutdown();
    }

    @Bean
    @Lazy
    public SpringAkkaExtensionId.AkkaExtension springAkkaExtension() {
        return SpringAkkaExtensionId.instance.get(actorSystem());
    }

    @Bean
    @Lazy
    public Cluster cluster() {
        return Cluster.get(actorSystem());
    }

    private static class ActorSystemShutdown {
        @Autowired
        private ApplicationContext applicationContext;

        @EventListener(ContextStoppedEvent.class)
        public void onStop() {
            final ActorSystem actorSystem = applicationContext.getBean(ActorSystem.class);
            actorSystem.shutdown();
            actorSystem.awaitTermination(Duration.create(15, TimeUnit.SECONDS));
        }
    }
}
