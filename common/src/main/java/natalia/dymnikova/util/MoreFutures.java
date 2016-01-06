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

package natalia.dymnikova.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.util.MoreThrowables.unchecked;

/**
 * 
 */
public class MoreFutures {
    public static <T> CompletableFuture<T> immediateFuture(final T value) {
        return completedFuture(value);
    }

    public static <T> CompletableFuture<T> immediateFailedFuture(final Throwable t) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(t);
        return future;
    }

    @SafeVarargs
    public static <T> CompletableFuture<List<T>> allOf(final CompletableFuture<T>... futures) {
        return CompletableFuture.allOf(futures).thenApply(v -> asList(futures).stream().map(f -> f.getNow(null)).collect(toList()));
    }

    @SafeVarargs
    public static <T> CompletableFuture<List<Result<T>>> successful(final CompletableFuture<T>... futures) {
        return CompletableFuture.allOf(futures)
                .exceptionally(t -> null)
                .thenApply(v -> asList(futures)
                                .stream()
                                .map(f -> {
                                    if (f.isCompletedExceptionally()) {
                                        try {
                                            return new Result<T>(f.getNow(null), null);
                                        } catch (Exception e) {
                                            return new Result<T>(null, e);
                                        }
                                    } else {
                                        return new Result<T>(f.getNow(null), null);
                                    }
                                })
                                .collect(toList())
                );
    }



    public static <T> T getUncheckedNow(final CompletableFuture<T> future) {
        return getUnchecked(future, 1, MICROSECONDS);
    }

    public static <T> T getUnchecked(final CompletableFuture<T> future, final long timeout, final TimeUnit unit) {
        try {
            return future.get(timeout, unit);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unchecked(e);
        } catch (ExecutionException | TimeoutException e) {
            throw unchecked(e);
        }
    }

    public static class Result<T> {
        public final T entry;
        public final Throwable t;

        public Result(final T entry, final Throwable t) {
            this.entry = entry;
            this.t = t;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "entry=" + entry +
                    ", t=" + t +
                    '}';
        }
    }
}
