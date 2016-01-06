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

package natalia.dymnikova.akka.scheduler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import natalia.dymnikova.configuration.ConfiguredEnvironment;
import natalia.dymnikova.util.AutowireHelper;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static com.typesafe.config.ConfigFactory.load;

/**
 *
 */
public class IntegrationTestCtxRule implements TestRule{

    private Object testObject;
    private AnnotationConfigApplicationContext ctx;

    public IntegrationTestCtxRule(final Object test) {
        testObject = test;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {

                ctx = new AnnotationConfigApplicationContext();
                ctx.setEnvironment(new ConfiguredEnvironment(load("flow-test.conf")));
                ctx.scan("natalia.dymnikova");
                ctx.refresh();
                ctx.start();

                try {
                    final AutowireHelper helper = ctx.getBean(AutowireHelper.class);
                    helper.autowire(testObject);

                    base.evaluate();
                } finally {
                    ctx.close();
                }
            }
        };
    }
}
