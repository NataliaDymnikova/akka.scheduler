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

import java.io.Serializable;

/**
 * Base interface for all functional interfaces which can be used as transformations in {@link RemoteObservable}
 * <p>
 * An implementation of this interface can declare {@link org.springframework.beans.factory.annotation.Autowired} fields
 * and have {@link @javax.annotation.PostConstruct} and {@link @javax.annotation.PreDestroy} methods.
 * <p>
 * Those will be initialized as for any other regular Spring bean.
 * <p>
 * Any {@link org.springframework.beans.factory.annotation.Autowired} dependencies will be used by an engine implementation
 * to determine a node which fits for this remote operation.
 */
public interface Remote extends Serializable {

    /**
     * Returns an instance of {@link RunCriteria} which is evaluated on node to
     * know whether particular node fits for this function execution.
     *
     * This method is called on an instance of {@link Remote} after all {@link org.springframework.beans.factory.annotation.Autowired} fields are initialized
     * and {@link @javax.annotation.PostConstruct} callbacks called
     */
    default RunCriteria getRunCriteria() {
        return (RunCriteria) () -> true;
    }

}
