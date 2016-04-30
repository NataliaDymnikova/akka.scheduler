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

import akka.actor.ActorPath;
import akka.actor.ActorSelection;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.pf.ReceiveBuilder;
import natalia.dymnikova.cluster.Actor;
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.ActorLogic;
import natalia.dymnikova.cluster.SpringAkkaExtensionId;
import natalia.dymnikova.cluster.scheduler.RemoteMergeOperator;
import natalia.dymnikova.cluster.scheduler.RemoteStageException;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.impl.SubscriberWithMore.HandleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import rx.Observable;
import rx.Subscriber;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static akka.actor.ActorPaths.fromString;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;

/**
 *
 */
@Actor
public class MergeStageActor extends ActorLogic {
    private static final Logger log = LoggerFactory.getLogger(MergeStageActor.class);

    private static final AkkaBackedSchedulerThreadCtx ThreadCtx = new AkkaBackedSchedulerThreadCtx();

    @Autowired
    private Codec codec;

    @Autowired
    private Cluster cluster;

    private List<ActorPath> prevActor;
    private final ActorSelection nextActor;
    private final Map<ActorPath, Subscriber<? super Serializable>> subscribers;

    private final RemoteMergeOperator<Serializable> mergeOperator;

    private final String flowName;

    private final OutSubscriberFactory outSubscriberFactory;

    private SubscriberWithMore outSubscriber;

    public static Props props(final SpringAkkaExtensionId.AkkaExtension extension,
                              final List<ActorPath> prevActor,
                              final ActorSelection nextActor,
                              final RemoteMergeOperator<Serializable> mergeOperator,
                              final Flow.SetFlow flow,
                              final OutSubscriberFactory outSubscriberFactory) {
        return extension.props(
                MergeStageActor.class,
                prevActor,
                nextActor,
                mergeOperator,
                flow,
                outSubscriberFactory
        );
    }

    public MergeStageActor(final ActorAdapter adapter,
                           final List<ActorPath> prevActor,
                           final ActorSelection nextActor,
                           final RemoteMergeOperator<Serializable> mergeOperator,
                           final Flow.SetFlow flow,
                           final OutSubscriberFactory outSubscriberFactory) {
        super(adapter);
        this.prevActor = prevActor;
        this.nextActor = nextActor;
        this.mergeOperator = mergeOperator;
        this.flowName = flow.getFlowName();
        this.outSubscriberFactory = outSubscriberFactory;

        subscribers = new HashMap<>();

        receive(new ReceiveAdapter(ReceiveBuilder
                .match(Flow.IsReady.class, this::handle)
                .match(Flow.OnStart.class, this::handle)
                .match(Flow.Data.class, this::handle)
                .match(Flow.Completed.class, this::handle)
                .match(Flow.More.class, this::handle)
                .match(HandleException.class, this::handle)
                .match(Flow.State.Error.class, this::handle)
                .build(),
                t -> {
                    if (!(t instanceof HandleException)) {
                        outSubscriber.onError(t);
                    }
                }
        ));
    }

    public void handle(final HandleException e) {
        throw e;
    }

    public void handle(final Flow.OnStart onStart) {
        outSubscriber = outSubscriberFactory.getOutSubscriber(of(nextActor), parent(), self(), 0);

        final List<Observable<? extends Serializable>> listObservables = prevActor.stream().map(path ->
                Observable.<Serializable>create(s -> {
                    s.setProducer(new ProducerForStartAndMore(actorSelection(path), self()));
                    log.debug("Path: {}", path);
                    subscribers.put(path, s);
                })
        ).collect(toList());

        ThreadCtx.withFlow(flowName, () -> mergeOperator.call(listObservables)
                .subscribe(outSubscriber));

        outSubscriber.more(onStart.getCount());
    }

    public void handle(final Flow.IsReady isReady) {
        sender().tell(Flow.State.Ok.getDefaultInstance(), self());
    }

    public void handle(final Flow.More more) {
        outSubscriber.more(more.getCount());
    }

    public void handle(final Flow.Data p) {
        final Serializable unpack = codec.unpack(p.getData().newInput());
        ThreadCtx.withFlow(flowName, () -> {
            final ActorPath senderPath = getSenderPath();
            log.debug("Sender: {}", senderPath);
            subscribers.get(senderPath).onNext(unpack);
        });
    }

    private ActorPath getSenderPath() {
        final ActorPath path = sender().path();
        final Address address = path.address();
        if ("akka".equals(address.protocol())) {
            return fromString(path.toStringWithAddress(cluster.selfAddress()));
        } else {
            return path;
        }
    }

    public void handle(final Flow.Completed completed) {
        ThreadCtx.withFlow(flowName, () -> subscribers.get(getSenderPath()).onCompleted());
    }

    public void handle(final Flow.State.Error error) {
        ThreadCtx.withFlow(flowName, () -> subscribers.get(getSenderPath()).onError(new RemoteStageException(error.getMessage())));
    }

}



