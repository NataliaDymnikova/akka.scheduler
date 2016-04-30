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

import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

/**
 */
public class AkkaBackedSchedulerThreadCtx {

    private static final ThreadLocal<String> currentFlowName = new ThreadLocal<>();

    public Optional<String> getCurrentFlowName() {
        return ofNullable(currentFlowName.get());
    }

    public <T> T withFlow(final String flowName, final Supplier<T> supplier) {
        currentFlowName.set(flowName);
        try {
            return supplier.get();
        } finally {
            currentFlowName.remove();
        }
    }

    public void withFlow(final String flowName, final Runnable runnable) {
        currentFlowName.set(flowName);
        try {
            runnable.run();
        } finally {
            currentFlowName.remove();
        }
    }
}
