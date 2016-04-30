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
import com.typesafe.config.ConfigMemorySize;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static java.util.Arrays.stream;
import static natalia.dymnikova.util.MoreThrowables.unchecked;
import static org.springframework.util.ClassUtils.getUserClass;
import static org.springframework.util.ReflectionUtils.setField;

/**
 * 
 */
@Component
public class ConfigBeanPostProcessor implements BeanPostProcessor {

    @Autowired
    ConfiguredEnvironment environment;

    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException {
        Class<?> currentClass = getUserClass(bean.getClass());

        while (currentClass != null && currentClass != Object.class) {
            stream(currentClass.getDeclaredFields())
                .filter(f -> f.getAnnotation(ConfigValue.class) != null)
                .map(this::makeAccessible)
                .forEach(field -> injectConfiguration(bean, beanName, field));

            currentClass = currentClass.getSuperclass();
        }

        return bean;
    }

    private void injectConfiguration(Object bean, String beanName, Field field) {
        final String path = field.getAnnotation(ConfigValue.class).value();
        try {
            setField(
                field,
                bean,
                convert(
                    field.getGenericType(), environment.config, path
                )
            );
        } catch (final com.typesafe.config.ConfigException e) {
            throw unchecked(
                e,
                "Failed to apply configuration '{}' to property '{}' of bean '{}' of type {}",
                path,
                field.getName(),
                beanName,
                bean.getClass().getName()
            );
        }
    }

    private Field makeAccessible(Field f) {
        ReflectionUtils.makeAccessible(f);
        return f;
    }

    private Object convert(final Type targetType,
                           final Config config,
                           final String path) {
        if (targetType == Config.class) {
            if (path.length() == 0) {
                return config;
            }
            return config.getConfig(path);
        } else if (targetType == int.class || targetType == Integer.class) {
            return config.getInt(path);
        } else if (targetType == long.class || targetType == Long.class) {
            return config.getLong(path);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return config.getBoolean(path);
        } else if (targetType == String.class) {
            return config.getString(path);
        } else if (targetType == double.class || targetType == Double.class) {
            return config.getDouble(path);
        } else if (targetType == Duration.class) {
            return config.getDuration(path);
        } else if (targetType == ConfigMemorySize.class) {
            return config.getMemorySize(path);
        } else if (targetType == Path.class) {
            return Paths.get(config.getString(path));
        } else if (targetType == List.class) {
            final Type type = ((ParameterizedType) targetType).getActualTypeArguments()[0];
            if (type == String.class) {
                return config.getStringList(path);
            } else if (type == Long.class) {
                return config.getLongList(path);
            } else if (type == Integer.class) {
                return config.getIntList(path);
            } else {
                throw new UnsupportedOperationException("Unsupported type of a List field " + type.getTypeName());
            }
        } else {
            return config.getValue(path).unwrapped();
        }
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        return bean;
    }
}
