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

import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.util.Timeout;
import natalia.dymnikova.cluster.ActorSystemAdapter;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.CheckFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import scala.collection.JavaConversions;
import scala.concurrent.duration.Duration;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static natalia.dymnikova.cluster.ActorPaths.computePool;
import static natalia.dymnikova.cluster.util.ScalaToJava.toJava;
import static natalia.dymnikova.util.MoreByteStrings.wrap;
import static natalia.dymnikova.util.MoreFutures.allOf;
import static natalia.dymnikova.util.MoreFutures.immediateFailedFuture;

/**
 *
 */
@Lazy
@Component
public class NodeSearcher {

    private static final Logger log = LoggerFactory.getLogger(NodeSearcher.class);

    @Autowired
    private Cluster cluster;

    @Autowired
    private Codec codec;

    @Autowired
    private ActorSystemAdapter adapter;

    @Autowired
    private RolesChecker checker;

    public CompletableFuture<List<Optional<Address>>> search(final Remote operator) {
        final Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
        final CheckFlow msg = CheckFlow.newBuilder()
                .setOperator(wrap(codec.packObject(operator)))
                .build();

        final Collection<Member> members = JavaConversions.asJavaCollection(cluster.readView().members());

        if (members.isEmpty()) {
            return immediateFailedFuture(new IllegalStateException("No cluster members found"));
        }

        @SuppressWarnings("unchecked")
        final CompletableFuture<Optional<Address>>[] futures = members
                .stream()
                .filter(member -> checker.check(operator, member.getRoles()))
                .map(m -> computePool(m.address()))
                .map(actorPath -> toJava(adapter.ask(adapter.actorSelection(actorPath), msg, timeout))
                        .thenApply(o -> {
                            if (o instanceof Flow.State.Ok) {
                                return Optional.of(actorPath.address());
                            } else {
                                return Optional.empty();
                            }
                        })
                        .exceptionally(t -> {
                            log.error(t.getMessage(), t);
                            return Optional.empty();
                        })
                ).toArray(CompletableFuture[]::new);

        return allOf(futures);
    }
}
