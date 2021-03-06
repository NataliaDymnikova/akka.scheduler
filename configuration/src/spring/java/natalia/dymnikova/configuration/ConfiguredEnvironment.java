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
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;

/**
 * 
 */
public class ConfiguredEnvironment extends StandardEnvironment {
    private static final Logger log = LoggerFactory.getLogger(ConfiguredEnvironment.class);
    public static final String CONFIGURED_PROPERTY_SOURCE = "configured-property-source";

    final Config config;

    public ConfiguredEnvironment() {
        this(loadConfig());
    }

    private static Config loadConfig() {
        try {
            return ConfigFactory.load();
        } catch (final ConfigException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    public ConfiguredEnvironment(final Config config) {
        this.config = config;

        final List<String> profiles = config.getStringList("akka.cluster.roles");
        setActiveProfiles(profiles.toArray(new String[profiles.size()]));
    }

    public Config getConfig() {
        return config;
    }

    @Override
    protected void customizePropertySources(final MutablePropertySources propertySources) {
        propertySources.addLast(new PropertySource<Object>(CONFIGURED_PROPERTY_SOURCE) {
            @Override
            public Object getProperty(final String name) {
                try {
                    final com.typesafe.config.ConfigValue value = config.getValue(name);
                    final ConfigValueType valueType = value.valueType();

                    if (ConfigValueType.OBJECT == valueType || ConfigValueType.LIST == valueType) {
                        final Config config = ((ConfigObject) value).toConfig();
                        log.trace("Loaded configuration object {} = {}", name, config);
                        return config;
                    } else {
                        log.trace("Loaded configuration value {} = {}", name, value);
                        return value.unwrapped();
                    }
                } catch (final ConfigException e) {
                    return null;
                }
            }
        });
    }
}
