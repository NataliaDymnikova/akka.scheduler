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

package natalia.dymnikova.cluster;

import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static java.lang.System.nanoTime;
import static java.time.Duration.ofNanos;
import static org.slf4j.LoggerFactory.getLogger;

/**
 */
public class ClusterMemberSpringContext extends AnnotationConfigApplicationContext {
    private static final Logger log = getLogger(ClusterMemberSpringContext.class);

    public ClusterMemberSpringContext() {
        super(new DefaultListableBeanFactory() {
            @Override
            public void preInstantiateSingletons() throws BeansException {
                final long start = nanoTime();
                try {
                    super.preInstantiateSingletons();
                } finally {
                    log.info("Singleton beans initialized in {} sec", ofNanos(nanoTime() - start).getSeconds());
                }
            }

            @Override
            protected Object createBean(final String beanName, final RootBeanDefinition mbd, final Object[] args) throws BeanCreationException {
                final long start = nanoTime();
                try {
                    return super.createBean(beanName, mbd, args);
                } finally {
                    log.info("Bean {} created in {} ms", beanName, ofNanos(nanoTime() - start).toMillis());
                }
            }
        });
    }

    @Override
    public void refresh() throws BeansException, IllegalStateException {
        final long start = nanoTime();
        try {
            super.refresh();
        } finally {
            log.info("Spring context initialized in {} sec", ofNanos(nanoTime() - start).getSeconds());
        }
    }

    @Override
    protected void onClose() {
        log.info("Context is closing...");
        super.onClose();
    }
}
