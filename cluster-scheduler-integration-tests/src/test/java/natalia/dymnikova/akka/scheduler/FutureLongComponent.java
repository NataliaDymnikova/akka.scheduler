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

package natalia.dymnikova.akka.scheduler;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 */
@Component
public class FutureLongComponent {

    private final CompletableFuture<CountAndSumm> future = new CompletableFuture<>();

    private long summ = 0;
    private long count = 0;

    public void add(long toAdd) {
        summ += toAdd; count ++;
    }

    public void complete() {
        future.complete(new CountAndSumm(count, summ));
    }

    public CountAndSumm get(final long timeout, final TimeUnit unit) throws Exception {
        try {
            return future.get(timeout, unit);
        } catch (final TimeoutException e) {
            return new CountAndSumm(count, summ);
        }
    }

    public void error(final Throwable e) {
        future.completeExceptionally(e);
    }

    public static class CountAndSumm {
        public final long count;
        public final long summ;

        public CountAndSumm(final long count, final long summ) {
            this.count = count;
            this.summ = summ;
        }
    }
}
