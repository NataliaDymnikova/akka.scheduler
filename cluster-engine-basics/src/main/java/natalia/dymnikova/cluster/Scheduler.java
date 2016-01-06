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
import akka.actor.Cancellable;
import scala.concurrent.duration.FiniteDuration;

/**
 * 
 */
public interface Scheduler {
    /**
     * <b>Warning</b>
     * <p>
     * If you schedule Runnable instances you should be extra careful to not pass or close over unstable references. In practice this means that you should not call methods on the enclosing Actor from within the Runnable. If you need to schedule an invocation it is better to use the schedule() variant accepting a message and an ActorRef to schedule a message to self (containing the necessary parameters) and then call the method when the message is received.
     */
    Cancellable scheduleOnce(FiniteDuration duration, Runnable runnable);

    /**
     * Schedules a message to be sent once with a delay, i.e. a time period that has
     * to pass before the message is sent.
     */
    Cancellable scheduleOnce(FiniteDuration duration, ActorRef ref, Object message);
}
