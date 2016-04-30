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

package natalia.dymnikova.cluster.scheduler;

import rx.Observable;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * An interface of an generic data source. Implementing this interface will help {@link Scheduler} logic to
 * identify a right node to place a data processing task
 */
public interface DataSource<Key extends Serializable> {
    /**
     * @return true if this DataSource has specified item
     */
    CompletableFuture<Boolean> hasItem(final Key itemKey);

    /**
     * DataSource needs to call a consumer for each new data item to notify {@link Scheduler} logic about newly
     * arrived items
     */
    void addItemObserver(final Consumer<Key> consumer);

    /**
     * {@link Scheduler} logic will call this method to list content of this data source
     */
    Observable<Key> items();
}
