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

import com.typesafe.config.*;
import com.typesafe.config.ConfigValue;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.json.JsonConfigurationFactory;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;
import static com.typesafe.config.ConfigValueType.LIST;
import static com.typesafe.config.ConfigValueType.OBJECT;

/**
 * 
 */
@Plugin(
    name = "Log4jConfigurationPlugin",
    category = "ConfigurationFactory"
)
@Order(1)
public class Log4jConfigurationPlugin extends JsonConfigurationFactory {

    @Override
    protected boolean isActive() {
        return true;
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{".conf"};
    }

    public Configuration getConfiguration(final ConfigurationSource source) {
        final Config config = ConfigFactory.load();
        return getConfiguration(source, config.getConfig("natalia-dymnikova.logging"));
    }

    public Configuration getConfiguration(final ConfigurationSource source,
                                          final Config loggingConfig) {
        LOGGER.debug("Using logging config: {}", loggingConfig);

        return new Log4jConfiguration(source, loggingConfig);
    }

    private static class Log4jConfiguration extends AbstractConfiguration {
        public static final EnumSet<ConfigValueType> COMPLEX_TYPES = EnumSet.of(OBJECT, ConfigValueType.LIST);
        private final Config config;

        public Log4jConfiguration(final ConfigurationSource source, final Config config) {
            super(source);
            this.config = config;
            processAttributes(rootNode, config.root());
        }

        private void processAttributes(final Node parent, final ConfigObject node) {
            final Map<String, String> attrs = parent.getAttributes();
            for (final String entryKey : node.keySet()) {
                if (!entryKey.equalsIgnoreCase("type")) {
                    final ConfigValue value = node.get(entryKey);
                    if (!COMPLEX_TYPES.contains(value.valueType())) {
                        attrs.put(entryKey, String.valueOf(value.unwrapped()));
                    }
                }
            }
        }

        @Override
        protected void setup() {
            final List<Node> children = rootNode.getChildren();
            final ConfigObject root = config.root();
            for (final String entryKey : root.keySet()) {
                final ConfigValue n = root.get(entryKey);
                if (n.valueType() == OBJECT) {
                    LOGGER.debug("Processing node for object {}", entryKey);
                    children.add(constructNode(entryKey, rootNode, (ConfigObject) n));
                } else if (n.valueType() == LIST) {
                    LOGGER.error("Arrays are not supported at the root configuration: {}", entryKey);
                }
            }
            LOGGER.debug("Completed parsing configuration");
        }

        private Node constructNode(final String name,
                                   final Node parent,
                                   final ConfigObject jsonNode) {
            final Node node = new Node(
                    parent,
                    name,
                    getPluginType(name, jsonNode)
            );

            processAttributes(node, jsonNode);
            final List<Node> children = node.getChildren();

            for (final String entryKey : jsonNode.keySet()) {
                final ConfigValue n = jsonNode.get(entryKey);
                if (n.valueType() == LIST) {
                    LOGGER.debug("Processing node for array {}", entryKey);
                    final ConfigList list = (ConfigList) n;
                    final int size = list.size();
                    for (int i = 0; i < size; ++i) {
                        final ConfigObject cfgObject = (ConfigObject) list.get(i);

                        final String pluginType = getType(cfgObject, entryKey);
                        final PluginType<?> entryType = pluginManager.getPluginType(pluginType);
                        final Node item = new Node(node, entryKey, entryType);
                        processAttributes(item, cfgObject);

                        LOGGER.debug("Processing {} {}[{}]", pluginType, entryKey, i);

                        final List<Node> itemChildren = item.getChildren();
                        for (final Map.Entry<String, ConfigValue> itemEntry : cfgObject.entrySet()) {
                            if (itemEntry.getValue().valueType() == OBJECT) {
                                LOGGER.debug("Processing node for object {}", itemEntry.getKey());
                                itemChildren.add(constructNode(itemEntry.getKey(), item, (ConfigObject) itemEntry.getValue()));
                            } else if (itemEntry.getValue().valueType() == LIST) {
                                final ConfigList array = (ConfigList) itemEntry.getValue();
                                final String entryName = itemEntry.getKey();
                                LOGGER.debug("Processing array for object {}", entryName);
                                for (final ConfigValue anArray : array) {
                                    itemChildren.add(constructNode(entryName, item, (ConfigObject) anArray));
                                }
                            }
                        }
                        children.add(item);
                    }
                } else if (n.valueType() == OBJECT) {
                    LOGGER.debug("Processing node for object {}", entryKey);
                    children.add(constructNode(entryKey, node, (ConfigObject) n));
                }
            }

            String t;
            if (getPluginType(name, jsonNode) == null) {
                t = "null";
            } else {
                t = getPluginType(name, jsonNode).getElementName() + ':' + getPluginType(name, jsonNode).getPluginClass();
            }

            final String p = node.getParent() == null ? "null" : node.getParent().getName() == null ? "root" : node
                .getParent().getName();
            LOGGER.debug("Returning {} with parent {} of type {}", node.getName(), p, t);
            return node;
        }

        private PluginType<?> getPluginType(String name, ConfigObject jsonNode) {
            final PluginType<?> type;
            if (jsonNode.toConfig().hasPath("type")) {
                type = pluginManager.getPluginType(jsonNode.toConfig().getString("type"));
            } else {
                type = pluginManager.getPluginType(name);
            }
            return type;
        }

        private String getType(final ConfigObject node, final String name) {
            for (Map.Entry<String, ConfigValue> entry : node.entrySet()) {
                if (entry.getKey().equalsIgnoreCase("type")) {
                    return String.valueOf(entry.getValue().unwrapped());
                }
            }
            return name;
        }
    }


}
