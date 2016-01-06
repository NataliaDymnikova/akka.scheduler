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

package natalia.dymnikova.configuration;

import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 
 */
public class Log4jConfigurationPluginTest {

    static {
        //StatusLogger.getLogger().setLevel(Level.ALL);
    }

    final Configuration cfg = new Log4jConfigurationPlugin().getConfiguration(
        mock(ConfigurationSource.class), ConfigFactory.parseResources(
            Log4jConfigurationPluginTest.class, Log4jConfigurationPluginTest.class.getSimpleName() + ".conf"
        )
    );

    @Before
    public void setUp() throws Exception {
        cfg.start();
    }

    @Test
    public void shouldHaveConsoleAppender() throws Exception {
        assertThat(
            cfg.getAppender("stdOut"),
            notNullValue()
        );
    }

    @Test
    public void shouldHaveFileAppender() throws Exception {
        assertThat(
            cfg.getAppender("rollingFile"),
            notNullValue()
        );
    }

    @Test
    public void fileAppenderShouldHasTriggeringPolicy() throws Exception {
        assertThat(
            ((RollingFileAppender) cfg.getAppender("rollingFile"))
                .getManager()
                .getTriggeringPolicy(),
            notNullValue()
        );
    }

    @Test
    public void shouldHasEventLogger() throws Exception {
        assertThat(
            cfg.getLoggerConfig("EventLogger"),
            notNullValue()
        );
    }

    @Test
    public void shouldHasFooBarLogger() throws Exception {
        assertThat(
            cfg.getLoggerConfig("com.foo.bar"),
            notNullValue()
        );
    }

    @Test
    public void shouldHasRootLogger() throws Exception {
        assertThat(
            cfg.getLoggerConfig("root"),
            notNullValue()
        );
    }

    @Test
    public void undefinedLoggerShouldInheritRootConfig() throws Exception {
        assertThat(
            cfg.getLoggerConfig("undefined"),
            allOf(
                notNullValue(),
                hasProperty("name", isEmptyString())
            )
        );
    }
}