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

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.Pair;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorImpl;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithFunction;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithSubscriber;
import natalia.dymnikova.util.AutowireHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import rx.Observable.Operator;

import java.io.Serializable;
import java.lang.annotation.Inherited;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.stageName;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 *
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class BasicChildrenCreator extends ChildrenCreator {

    public BasicChildrenCreator(final SetFlow flow) {
        super(flow);
    }
}
