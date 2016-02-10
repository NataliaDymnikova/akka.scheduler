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

import akka.actor.ActorSystem;
import natalia.dymnikova.cluster.scheduler.RemoteFunction;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorImpl;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithFunction;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import rx.Subscriber;

import static java.util.Collections.emptySet;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.internal.util.collections.Sets.newSet;

/**
 *
 */
public class RolesCheckerTest {

    final RolesChecker checker = new RolesChecker();

    @Test
    public void shouldReturnTrueForEmptyRolesList() throws Exception {
        final RemoteOperator<String, String> operator = subscriber -> subscriber;

        assertThat(
                checker.check(operator, emptySet()),
                is(true)
        );
    }

    @Test
    public void shouldReturnTrueForOperatorWithNoRequirements() throws Exception {
        final RemoteOperator<String, String> operator = subscriber -> subscriber;

        assertThat(
                checker.check(operator, newSet("role1")),
                is(true)
        );
    }

    @Test
    public void shouldReturnFalseForOperatorWithOneRequirement() throws Exception {
        final RemoteOperator<String, String> operator = new RemoteOperator<String, String>() {

            @Autowired
            private TestDependencyClass dependency;

            @Override
            public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
                return subscriber;
            }
        };

        assertThat(
                checker.check(operator, newSet("role1")),
                is(false)
        );
    }

    @Test
    public void shouldReturnTrueForSystemBeans() throws Exception {
        final RemoteOperator<String, String> operator = new RemoteOperator<String, String>() {

            @Autowired
            private ActorSystem dependency;

            @Override
            public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
                return subscriber;
            }
        };

        assertThat(
                checker.check(operator, newSet("role1")),
                is(true)
        );
    }

    @Test
    public void shouldReturnTrueForOperatorWithOneRequirement() throws Exception {
        final RemoteOperator<String, String> operator = new RemoteOperator<String, String>() {

            @Autowired
            private TestDependencyClass dependency;

            @Override
            public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
                return subscriber;
            }
        };

        assertThat(
                checker.check(operator, newSet("test")),
                is(true)
        );
    }

    @Test
    public void shouldReturnTrueForOperatorWithOneRequirementOfInterface() throws Exception {
        final RemoteOperator<String, String> operator = new OperatorWithOneDependency();

        assertThat(
                checker.check(operator, newSet("test")),
                is(true)
        );
    }

    @Test
    public void shouldReturnTrueForOperatorWithTwoRequirements() throws Exception {
        assertThat(
                checker.check(
                        new OperatorWithTwoDependencies(),
                        newSet("test", "test2")
                ),
                is(true)
        );
    }

    @Test
    public void shouldReturnFalseForOperatorWithTwoRequirements() throws Exception {
        assertThat(
                checker.check(
                        new OperatorWithTwoDependencies(),
                        newSet("test")
                ),
                is(false)
        );
    }

    @Test
    public void shouldReturnTrueForOperatorWithOneRequirementOfInterfaceWithTwoImpl() throws Exception {
        final RemoteOperator<String, String> operator = new RemoteOperator<String, String>() {

            @Autowired
            private TestDependencyInterfaceWithSomeImpl dependencyInterface;

            @Override
            public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
                return subscriber;
            }
        };

        assertThat(
                checker.check(
                        operator,
                        newSet("test1")
                ),
                is(true)
        );
    }

    @Test
    public void shouldReturnTrueForFunctionWrappedIntoRemoteOperatorImpl() throws Exception {
        final RemoteOperator<String, String> operator = new RemoteOperatorWithFunction<>(new RemoteFunction<String, String>() {
            @Override
            public String apply(final String s) {
                return s;
            }

            @Autowired
            private TestDependencyClass dependencyInterface;

        });

        assertThat(
                checker.check(
                        operator,
                        newSet("test")
                ),
                is(true)
        );
    }

    @Test
    public void shouldReturnFalseForFunctionWrappedIntoRemoteOperatorImpl() throws Exception {
        final RemoteOperator<String, String> operator = new RemoteOperatorWithFunction<>(new RemoteFunction<String, String>() {
            @Override
            public String apply(final String s) {
                return s;
            }

            @Autowired
            private TestDependencyClass dependencyInterface;

        });

        assertThat(
                checker.check(
                        operator,
                        newSet("notTest")
                ),
                is(false)
        );
    }

    @Test
    public void shouldReturnTrueForOperatorWithOneRequirementOfInterfaceWithTwoImplWithProfileAndWithout() throws Exception {
        final RemoteOperator<String, String> operator = new RemoteOperatorWithFunction<>(new RemoteFunction<String, String>() {
            @Override
            public String apply(final String s) {
                return s;
            }

            @Autowired
            private TestDependencyInterfaceWithSomeImpl2 dependencyInterface;

        });

        assertThat(
                checker.check(
                        operator,
                        newSet("anyProfile")
                ),
                is(true)
        );
    }

    @Test
    public void shouldReturnFalseForOperatorWithOneRequirementOfInterfaceWithTwoImplWithProfileAndWithoutWithEmptyRolesSet()
            throws Exception {
        final RemoteOperator<String, String> operator = new RemoteOperatorWithFunction<>(new RemoteFunction<String, String>() {
            @Override
            public String apply(final String s) {
                return s;
            }

            @Autowired
            private TestDependencyInterfaceWithSomeImpl2 dependencyInterface;

        });

        assertThat(
                checker.check(
                        operator,
                        newSet()
                ),
                is(false)
        );
    }

    public interface TestDependencyInterface {
    }

    @Profile("test")
    public static class TestDependencyClass implements TestDependencyInterface {
    }

    @Profile("test2")
    public static class TestDependencyClass2 {
    }

    public interface TestDependencyInterfaceWithSomeImpl {
    }

    public interface TestDependencyInterfaceWithSomeImpl2 {
    }

    @Profile("test1")
    public static class TestDependencyClassImpl1
            implements TestDependencyInterfaceWithSomeImpl, TestDependencyInterfaceWithSomeImpl2 {
    }

    @Profile("test2")
    public static class TestDependencyClassImpl2 implements TestDependencyInterfaceWithSomeImpl {
    }

    public static class TestDependencyClassWithoutProfile implements TestDependencyInterfaceWithSomeImpl2 {
    }


    private static class OperatorWithTwoDependencies implements RemoteOperator<String, String> {

        @Autowired
        private TestDependencyInterface dependency;

        @Autowired
        private TestDependencyClass2 dependency2;

        @Override
        public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
            return subscriber;
        }
    }

    private static class OperatorWithOneDependency implements RemoteOperator<String, String> {

        @Autowired
        private TestDependencyInterface dependency;

        @Override
        public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
            return subscriber;
        }
    }
}