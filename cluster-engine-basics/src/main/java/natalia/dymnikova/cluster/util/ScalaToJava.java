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

package natalia.dymnikova.cluster.util;

import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

import java.util.concurrent.CompletableFuture;

import static akka.dispatch.ExecutionContexts.fromExecutor;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

/**
 * 
 */
public class ScalaToJava {

    public static <T> CompletableFuture<T> toJava(final Future<T> scalaFuture) {
        final CompletableFuture<T> javaFuture = new CompletableFuture<>();

        scalaFuture.onComplete(new AbstractFunction1<Try<T>, Object>() {
            @Override
            public Object apply(final Try<T> t) {
                if (t.isSuccess()) {
                    javaFuture.complete(t.get());
                } else {
                    javaFuture.completeExceptionally(t.failed().get());
                }
                return null;
            }
        }, fromExecutor(directExecutor()));

        return javaFuture;
    }
}
