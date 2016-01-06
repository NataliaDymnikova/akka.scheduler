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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigRenderOptions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;
import static com.typesafe.config.ConfigValueFactory.fromIterable;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * 
 */
public class ConfigBeanPostProcessorTest {

    private static final Logger log = LoggerFactory.getLogger(ConfigBeanPostProcessorTest.class);

    public static class TestBean {
        @ConfigValue("test-config")
        private Config config;

        @ConfigValue("test-config.string")
        private String aString;

        @ConfigValue("test-config.int")
        private Integer anInteger;

        @ConfigValue("test-config.int")
        private int anInt;

        @ConfigValue("test-config.bool")
        private Boolean aBoolean;

        @ConfigValue("test-config.bool")
        private boolean aBool;

        @ConfigValue("test-config.int")
        private String anIntAsString;

        @ConfigValue("test-config.duration")
        private Duration duration;

        @ConfigValue("test-config.memSize")
        private ConfigMemorySize memorySize;
    }

    private final ConfigBeanPostProcessor processor = new ConfigBeanPostProcessor() {{
        environment = new ConfiguredEnvironment(
            ConfigFactory.empty()
                .withValue("test-config.string", fromAnyRef("aString"))
                .withValue("test-config.int", fromAnyRef(1))
                .withValue("test-config.bool", fromAnyRef(true))
                .withValue("test-config.duration", fromAnyRef("5 s"))
                .withValue("test-config.memSize", fromAnyRef("5 M"))
                .withValue("akka.cluster.roles", fromIterable(emptyList()))
        );

        log.info("{}", environment.config.root().render(ConfigRenderOptions.concise().setJson(false).setFormatted(true).setOriginComments(false)));
    }};

    @Test
    public void shouldPopulateConfigField() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.config, notNullValue(Config.class)
        );
    }

    @Test
    public void shouldPopulateStringField() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.aString, is("aString")
        );
    }

    @Test
    public void shouldPopulateIntField() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.anInt, is(1)
        );
    }

    @Test
    public void shouldPopulateIntegerField() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.anInteger, is(1)
        );
    }

    @Test
    public void shouldPopulateBooleanField() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.aBoolean, is(true)
        );
    }

    @Test
    public void shouldPopulateBoolField() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.aBool, is(true)
        );
    }

    @Test
    public void shouldPopulateDurationField() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.duration, is(Duration.ofSeconds(5))
        );
    }

    @Test
    public void shouldPopulateMemorySize() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.memorySize, is(ConfigMemorySize.ofBytes(5 * 1024 * 1024))
        );
    }

    @Test
    public void shouldConvertConfigValueToString() throws Exception {
        final TestBean bean = (TestBean) processor.postProcessBeforeInitialization(new TestBean(), null);
        assertThat(
            bean.anIntAsString, is("1")
        );
    }
}
