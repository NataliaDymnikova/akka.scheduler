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

package natalia.dymnikova.cluster.scheduler.impl.requirements;

import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorImpl;
import natalia.dymnikova.configuration.ConfigValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 *
 */
@Lazy
@Component
public class ComputeMemberRequirementsBuilder {

    @ConfigValue("natalia-dymnikova.scheduler.base-package")
    private String basePackage = "natalia.dymnikova";

    private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    private CachingMetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);


    public ComputeMemberRequirementsBuilder() {
        metadataReaderFactory.setCacheLimit(4048);
    }

    private final StandardEnvironment scannerEnvironment = new StandardEnvironment() {
        @Override
        protected boolean isProfileActive(final String profile) {
            return true;
        }
    };

    public List<MemberRequirements> build(final Remote operator) {
        if (operator instanceof RemoteOperatorImpl) {
            return doBuild(((RemoteOperatorImpl) operator).delegate);
        } else {
            return doBuild(operator);
        }
    }

    private List<MemberRequirements> doBuild(final Remote remote) {
        // TODO deal with 3pp dependencies???
        return Arrays.stream(remote.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Autowired.class))
                .filter(field -> field.getType().getPackage().getName().startsWith(basePackage))
                .map(TypeFilterWithField::new)
                .map(this::scan)
                .map(e -> assemblyRequirement(remote, e.getKey(), e.getValue()))
                .collect(toList());
    }

    private MemberRequirements assemblyRequirement(final Remote remote,
                                                   final Field dependency,
                                                   final Set<ScannedGenericBeanDefinition> candidates) {
        final Set<String> roles = roles(candidates);


        return new MemberRequirementsImpl(new ArrayList<>());
    }

    private Set<String> roles(final Set<ScannedGenericBeanDefinition> candidates) {
        return candidates.stream()
                .map(def -> ofNullable(def.getMetadata().getAllAnnotationAttributes(Profile.class.getName())).map(map ->
                        ((List<String[]>) (Object) map.get("value")).stream()
                                .flatMap(Arrays::stream)
                                .collect(toList()))
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Collection::stream)
                .collect(toSet());
    }

    private static class TypeFilterWithField extends AssignableTypeFilter {
        private final Field field;

        public TypeFilterWithField(final Field field) {
            super(field.getType());
            this.field = field;
        }
    }


    private Entry<Field, Set<ScannedGenericBeanDefinition>> scan(TypeFilterWithField filter) {
        final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                false, scannerEnvironment
        );

        scanner.setResourceLoader(resourcePatternResolver);
        scanner.setMetadataReaderFactory(metadataReaderFactory);

        scanner.resetFilters(false);
        scanner.addIncludeFilter(new CompositeFilter(
                new AnnotationTypeFilter(Component.class), filter
        ));

        final Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(
                basePackage
        );

        final Set<ScannedGenericBeanDefinition> collect = candidateComponents
                .stream()
                .map(bd -> (ScannedGenericBeanDefinition) bd)
                .collect(toSet());

        return new SimpleEntry<>(filter.field, collect);
    }

    private static class CompositeFilter implements TypeFilter {

        private final TypeFilter[] filters;

        public CompositeFilter(final TypeFilter... filters) {
            this.filters = filters;
        }

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
            for (final TypeFilter filter : filters) {
                if (!filter.match(metadataReader, metadataReaderFactory)) {
                    return false;
                }
            }
            return true;
        }
    }
}
