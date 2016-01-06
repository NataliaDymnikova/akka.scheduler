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

package natalia.dymnikova.test;

import org.junit.Assert;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * 
 */
public class Wait {

    public static void waitFor(final Predicate<Void> condition) throws InterruptedException {
        waitFor(condition, SECONDS, 10);
    }

    public static void waitFor(final Predicate<Void> condition, TimeUnit timeUnit, long timeout) throws InterruptedException {
        waitFor(condition, timeUnit, timeout, MILLISECONDS, 100);
    }

    public static void waitFor(final Predicate<Void> condition,
                               final TimeUnit timeoutUnit,
                               final long timeout,
                               final long interval) throws InterruptedException {
        waitFor(condition, timeoutUnit, timeout, timeoutUnit, interval);
    }

    public static void waitFor(final Predicate<Void> condition,
                               final TimeUnit timeoutUnit,
                               final long timeout,
                               final TimeUnit intervalUnit,
                               final long interval) throws InterruptedException {
        for (final long endTime = currentTimeMillis() + timeoutUnit.toMillis(timeout); currentTimeMillis() < endTime; ) {
            if (condition.test(null)) {
                return;
            }
            Thread.sleep(intervalUnit.toMillis(interval));
        }
        Assert.assertThat(
            condition.test(null),
            is(true)
        );
    }

    public static <T> T waitFor(final Supplier<T> supplier,
                                final Predicate<T> condition,
                                final TimeUnit timeoutUnit,
                                final long timeout,
                                final long interval) throws InterruptedException {
        return waitFor(supplier, condition, timeoutUnit, timeout, timeoutUnit, interval);
    }

    public static <T> T waitFor(final Supplier<T> supplier,
                                final Predicate<T> condition,
                                final TimeUnit timeoutUnit,
                                final long timeout,
                                final TimeUnit intervalUnit,
                                final long interval) throws InterruptedException {
        for (final long endTime = currentTimeMillis() + timeoutUnit.toMillis(timeout); currentTimeMillis() < endTime; ) {
            final T value = supplier.get();
            if (condition.test(value)) {
                return value;
            }
            Thread.sleep(intervalUnit.toMillis(interval));
        }

        final T value = supplier.get();
        Assert.assertThat(
            value,
            is(notNullValue())
        );

        if (condition.test(value)) {
            return value;
        } else {
            Assert.fail(String.valueOf(value));
            return null;
        }
    }
}
