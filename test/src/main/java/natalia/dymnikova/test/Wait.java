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

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.String.valueOf;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * 
 */
public class Wait {

    public static void waitFor(final Predicate<Void> condition) {
        waitFor(condition, ofSeconds(10));
    }

    public static void waitFor(final Predicate<Void> condition,
                               final Duration duration) {
        waitFor(condition, duration, ofMillis(100));
    }

    public static void waitFor(final Predicate<Void> condition,
                               final Duration timeout,
                               final Duration interval){
        for (final long endTime = currentTimeMillis() + timeout.toMillis(); currentTimeMillis() < endTime; ) {
            if (condition.test(null)) {
                return;
            }
            try {
                sleep(interval.toMillis());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        assertThat(
            condition.test(null),
            is(true)
        );
    }

    public static <T> T waitFor(final Supplier<T> supplier,
                                final Predicate<T> condition,
                                final Duration timeout,
                                final Duration interval) {
        for (final long endTime = currentTimeMillis() + timeout.toMillis(); currentTimeMillis() < endTime; ) {
            final T value = supplier.get();
            if (condition.test(value)) {
                return value;
            }
            try {
                sleep(interval.toMillis());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }

        final T value = supplier.get();
        assertThat(
            value,
            is(notNullValue())
        );

        if (condition.test(value)) {
            return value;
        } else {
            throw new AssertionError(valueOf(value));
        }
    }
}
