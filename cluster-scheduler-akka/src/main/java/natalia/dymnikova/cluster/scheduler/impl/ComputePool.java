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

import akka.japi.pf.ReceiveBuilder;
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.Actor;
import natalia.dymnikova.cluster.ActorAdapter;
import natalia.dymnikova.cluster.ActorLogic;
import natalia.dymnikova.cluster.AutostartActor;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.cluster.scheduler.akka.Flow.CheckFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.State;
import natalia.dymnikova.util.AutowireHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;

import static natalia.dymnikova.cluster.ActorPaths.COMPUTE_POOL;
import static natalia.dymnikova.util.MoreLogging.lazyDebugString;

/**
 * An actor which represents a pool of compute resources/slots which can be used to execute any random computations
 * TODO introduce a limit on free slots.
 * <p>
 */
@Actor
@Profile("compute")
@AutostartActor(COMPUTE_POOL)
public class ComputePool extends ActorLogic {
    private static final Logger log = LoggerFactory.getLogger(ComputePool.class);

    @Autowired
    private AkkaExtension extension;

    @Autowired
    private Codec codec;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AutowireHelper autowireHelper;

    public ComputePool(final ActorAdapter adapter) {
        super(adapter);

        receive(ReceiveBuilder
                .match(CheckFlow.class, this::handle)
                .match(SetFlow.class, this::handle)
                .match(LocalSetFlow.class, this::handle)
                .build()
        );
    }

    public void handle(final CheckFlow checkFlow) {
        log.trace("Handling CheckFlow: {}", lazyDebugString(checkFlow));

        try {
            if (!check(checkFlow.getOperator())) {
                sender().tell(State.Error.getDefaultInstance(), self());
            } else {
                sender().tell(State.Ok.getDefaultInstance(), self());
            }
        } catch (final RuntimeException e) {
            log.error(e.getMessage(), e);
            sender().tell(State.Error.getDefaultInstance(), self());
        }
    }

    public void handle(final SetFlow flow) {
        log.trace("Handling SetFlow: {}", lazyDebugString(flow));
        log.debug("Create BasicChildrenCreator");

        actorOf(extension.props(
                FlowControlActor.class,
                flow,
                context.getBean(BasicChildrenCreator.class, flow)
        ), flow.getFlowName());
    }

    private void handle(final LocalSetFlow flow) {
        log.trace("Handling LocalSetFlow: {}", flow);
        log.debug("Create LocalChildrenCreator");
        actorOf(extension.props(
                FlowControlActor.class,
                flow.getFlow(),
                context.getBean(LocalChildrenCreator.class, flow)
        ), flow.getFlow().getFlowName());
    }

    private boolean check(final ByteString operator) {
        return autowireHelper.autowire(codec.unpackRemote(operator)).getRunCriteria().check();
    }

}
