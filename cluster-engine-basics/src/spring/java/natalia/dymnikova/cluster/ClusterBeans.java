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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;
import scala.concurrent.duration.Duration;

import java.net.InetAddress;
import java.net.SocketException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;
import static natalia.dymnikova.util.MoreNetwork.findAddress;

/**
 *
 */
@Configuration
public class ClusterBeans {
    private static final Logger log = LoggerFactory.getLogger(ClusterBeans.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ConfiguredEnvironment environment;

    @Bean
    @Lazy
    public ActorSystem actorSystem() throws Exception {
        final Config config = getActorSystemConfig();

        SpringActorProducer.initialize(applicationContext);
        actorSystemShutdown();
        return ActorSystem.create(
                config.getString("natalia-dymnikova.actor-system.name"),
                config
        );
    }

    private Config getActorSystemConfig() throws Exception {
        final Config config = environment.getConfig();
        return detectConfiguredAddress(config).map(addr -> config.withValue(
                "akka.remote.netty.tcp.hostname",
                fromAnyRef(addr.getHostAddress())
        )).orElse(config);
    }

    private Optional<InetAddress> detectConfiguredAddress(final Config config) throws SocketException {
        final String iFaceName = config.getString("akka.remote.netty.tcp.hostname");
        final boolean preferIPv4 = config.hasPath("natalia-dymnikova.actor-system.bind.prefer-ipv4") &&
                config.getBoolean("natalia-dymnikova.actor-system.bind.prefer-ipv4");

        return findAddress(iFaceName, preferIPv4);
    }

    @Bean
    @Lazy
    public ActorSystemShutdown actorSystemShutdown() {
        return new ActorSystemShutdown();
    }

    @Bean
    @Lazy
    public SpringAkkaExtensionId.AkkaExtension springAkkaExtension() throws Exception {
        return SpringAkkaExtensionId.instance.get(actorSystem());
    }

    @Bean
    @Lazy
    public Cluster cluster() throws Exception {
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
