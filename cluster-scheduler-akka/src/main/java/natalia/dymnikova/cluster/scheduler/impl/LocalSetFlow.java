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

import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import rx.Observable.Operator;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 *
 */
public class LocalSetFlow<T> implements Serializable {

    private final SetFlow flow;
    private final Consumer<T> onNext;
    private final Runnable onComplete;
    private final Consumer<Throwable> onError;

    public LocalSetFlow(final SetFlow flow,
                        final Consumer<T> onNext,
                        final Runnable onComplete,
                        final Consumer<Throwable> onError) {
        this.flow = flow;
        this.onNext = onNext;
        this.onComplete = onComplete;
        this.onError = onError;
    }

    public SetFlow getFlow() {
        return flow;
    }

    public Runnable getOnComplete() {
        return onComplete;
    }

    public Consumer<Throwable> getOnError() {
        return onError;
    }

    public Consumer<T> getOnNext() {
        return onNext;
    }
}
