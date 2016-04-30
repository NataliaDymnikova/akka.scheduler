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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import scala.concurrent.duration.FiniteDuration;

import static akka.actor.ActorRef.noSender;

/**
 * 
 */
public class SchedulerServiceImpl implements SchedulerService {
    private ActorSystem system;
    private akka.actor.Scheduler akkaScheduler;

    public SchedulerServiceImpl(final ActorSystem system, final akka.actor.Scheduler akkaScheduler) {
        this.system = system;
        this.akkaScheduler = akkaScheduler;
    }

    @Override
    public Cancellable scheduleOnce(final FiniteDuration duration, final Runnable runnable) {
        return akkaScheduler.scheduleOnce(
                duration, runnable, system.dispatcher()
        );
    }

    @Override
    public Cancellable scheduleOnce(final FiniteDuration duration, final ActorRef ref, final Object message) {
        return akkaScheduler.scheduleOnce(
                duration, ref, message, system.dispatcher(), noSender()
        );
    }
}
