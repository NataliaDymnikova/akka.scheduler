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

import rx.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 *
 */
public class MoreSubscribers {
    public static class SubscriberWithFutureForCompleted<T> extends Subscriber<T> {

        private CompletableFuture<T> future = new CompletableFuture<>();
        private Consumer<T> onNext;

        public SubscriberWithFutureForCompleted() {
            this(t -> { });
        }

        public SubscriberWithFutureForCompleted(final Consumer<T> onNext) {
//            request(0);
            this.onNext = onNext;
        }

        @Override
        public void onCompleted() {
            future.complete(null);
        }

        @Override
        public void onError(final Throwable e) {
            future.completeExceptionally(e);
        }

        @Override
        public void onNext(final T t) {
            onNext.accept(t);
        }

        public CompletableFuture<T> getFuture() {
            return future;
        }
    }

    public static class GetBlockingSubscriber<T> extends Subscriber<T> {
        private final CompletableFuture<T> objectCompletableFuture;
        private T ts;

        public GetBlockingSubscriber(final CompletableFuture<T> objectCompletableFuture) {
            this.objectCompletableFuture = objectCompletableFuture;
        }

        @Override
        public void onCompleted() {
            objectCompletableFuture.complete(ts);
        }

        @Override
        public void onError(final Throwable e) {
            objectCompletableFuture.completeExceptionally(e);
        }

        @Override
        public void onNext(final T ts) {
            this.ts = ts;
        }
    }

    public static class SubscriberToCompletableFutureAdapter<T> extends Subscriber<T> {
        public final CompletableFuture<List<T>> future = new CompletableFuture<>();
        private final List<T> entries = new ArrayList<>();

        @Override
        public void onStart() {
//            request(0);
        }

        @Override
        public void onCompleted() {
            future.complete(entries);
        }

        @Override
        public void onError(final Throwable e) {
            future.completeExceptionally(e);
        }

        @Override
        public void onNext(final T value) {
            entries.add(value);
        }
    }
}
