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

import natalia.dymnikova.util.MoreSubscribers;
import natalia.dymnikova.util.MoreSubscribers.GetBlockingSubscriber;
import natalia.dymnikova.util.MoreThrowables;
import rx.Observable;
import rx.Subscriber;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.lang.Thread.currentThread;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static natalia.dymnikova.util.MoreThrowables.unchecked;

/**
 */
public class ObservableGetBlockingForTests {
    public static <T> List<T> getListBlocking(final Observable<T> observable) {
        return getListBlocking(observable, ofSeconds(1));
    }

    public static <T> List<T> getListBlocking(final Observable<T> observable, final Duration duration) {
        final CompletableFuture<List<T>> objectCompletableFuture = new CompletableFuture<>();
        observable.toList().first().subscribe(new GetBlockingSubscriber<>(objectCompletableFuture));
        try {
            return objectCompletableFuture.get(duration.toMillis(), MILLISECONDS);
        } catch (final InterruptedException e) {
            currentThread().interrupt();
            throw unchecked(e);
        } catch (final ExecutionException | TimeoutException e) {
            throw unchecked(e);
        }
    }

    public static <T> T getBlocking(final Observable<T> observable) throws Exception {
        return getBlocking(observable, ofSeconds(1));
    }

    public static <T> T getBlocking(final Observable<T> observable, final Duration duration) {
        final CompletableFuture<T> objectCompletableFuture = new CompletableFuture<>();
        observable.first().subscribe(new GetBlockingSubscriber<>(objectCompletableFuture));
        try {
            return objectCompletableFuture.get(duration.toMillis(), MILLISECONDS);
        } catch (final InterruptedException e) {
            currentThread().interrupt();
            throw unchecked(e);
        } catch (final ExecutionException | TimeoutException e) {
            throw unchecked(e);
        }
    }

}
