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

import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorImpl;
import natalia.dymnikova.configuration.ConfigValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 *
 */
@Component
public class RolesChecker {

    @ConfigValue("natalia-dymnikova.scheduler.base-package")
    private String basePackage = "natalia.dymnikova";

    public boolean check(final Remote operator, final Set<String> roles) {
        if (operator instanceof RemoteOperatorImpl) {
            return doCheck(((RemoteOperatorImpl) operator).delegate, roles);
        } else {
            return doCheck(operator, roles);
        }
    }

    private boolean doCheck(final Remote remote, final Set<String> roles) {
        return doCheck(roles, remote.getClass());
    }

    private boolean doCheck(final Set<String> roles, final Class<? extends Remote> aClass) {
        final Field[] declaredFields = aClass.getDeclaredFields();
        final List<TypeFilter> fields = Arrays.stream(declaredFields)
                .filter(field -> field.isAnnotationPresent(Autowired.class))
                .map(Field::getType)
                .map(AssignableTypeFilter::new)
                .collect(toList());

        final MultiValueMap<String, Object> matchAll = new LinkedMultiValueMap<>();
        matchAll.put("value", singletonList(roles.toArray(new String[roles.size()])));

        final List<Set<ScannedGenericBeanDefinition>> set = scan(basePackage, fields);

        return set.stream().map(definitions -> definitions.stream()
                .map(def -> {
                            final MultiValueMap<String, Object> map = ofNullable(def.getMetadata()
                                    .getAllAnnotationAttributes(Profile.class.getName())
                            ).orElse(matchAll);

                            //noinspection unchecked
                            return ((List<String[]>) (Object) map.get("value")).stream()
                                    .flatMap(Arrays::stream)
                                    .collect(toList());
                        }
                ).flatMap(Collection::stream).collect(toList())
        ).allMatch(profiles -> profiles.stream()
                .filter(roles::contains)
                .findAny()
                .isPresent()
        );
    }

    private List<Set<ScannedGenericBeanDefinition>> scan(final String basePackage, final List<TypeFilter> filters) {
        final ArrayList<Set<ScannedGenericBeanDefinition>> sets = new ArrayList<>(filters.size());

        final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                false,
                new StandardEnvironment() {
                    @Override
                    protected boolean isProfileActive(final String profile) {
                        return true;
                    }
                }
        );

        for (final TypeFilter filter : filters) {
            scanner.resetFilters(false);
            scanner.addIncludeFilter(filter);

            final Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(
                    basePackage
            );

            final Set<ScannedGenericBeanDefinition> collect = candidateComponents
                    .stream()
                    .map(bd -> (ScannedGenericBeanDefinition) bd)
                    .collect(toSet());

            sets.add(collect);
        }

        scanner.clearCache();

        return sets;
    }

}
