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

import scala.PartialFunction;
import scala.runtime.AbstractPartialFunction;
import scala.runtime.BoxedUnit;

import java.util.function.Consumer;

/**
 * 
 */
public class ReceiveAdapter extends AbstractPartialFunction<Object, BoxedUnit> {
    private final PartialFunction<Object, BoxedUnit> receive;
    private final Consumer<Throwable> tConsumer;

    public ReceiveAdapter(final PartialFunction<Object, BoxedUnit> receive, final Consumer<Throwable> tConsumer) {
        this.receive = receive;
        this.tConsumer = tConsumer;
    }

    @Override
    public boolean isDefinedAt(final Object x) {
        return receive.isDefinedAt(x);
    }

    @Override
    public BoxedUnit apply(final Object x) {
        try {
            return receive.apply(x);
        } catch (final Throwable e) {
            tConsumer.accept(e);
            throw e;
        }
    }
}