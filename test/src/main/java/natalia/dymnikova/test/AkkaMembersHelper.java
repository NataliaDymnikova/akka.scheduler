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

package natalia.dymnikova.test;

import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.ClusterReadView;
import akka.cluster.Member;
import scala.collection.immutable.SortedSet;
import scala.collection.immutable.SortedSet$;
import scala.collection.mutable.Builder;
import scala.math.Ordering$;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * encapsulates all pain of testing akka actors and allows simple bdd unit tests creation
 */
public class AkkaMembersHelper {

    public static void givenMembers(final Cluster cluster, final Consumer<MembersBuilder> b) {
        final MembersBuilder membersBuilder = new MembersBuilder();
        b.accept(membersBuilder);

        final ClusterReadView clusterReadView = mock(ClusterReadView.class);

        doReturn(membersBuilder.builder.result()).when(clusterReadView).members();
        doReturn(clusterReadView).when(cluster).readView();
    }

    public static class MembersBuilder {
        private final Builder<Member, SortedSet> builder = SortedSet$.MODULE$.<Member>newBuilder(Ordering$.MODULE$.comparatorToOrdering((o1, o2) -> -1));

        public MembersBuilder member(final Consumer<MemberBuilder> consumer) {
            final MemberBuilder t = new MemberBuilder();
            consumer.accept(t);

            builder.$plus$eq(t.member);
            return this;
        }
    }

    public static class MemberBuilder {
        private Member member = mock(Member.class);

        public MemberBuilder withRole(final String... roles) {
            final List<String> rolesList = Arrays.asList(roles);

            doAnswer(i -> rolesList.contains(i.getArgumentAt(0, String.class))).when(member).hasRole(anyString());
            doAnswer(i -> new HashSet<>(rolesList)).when(member).getRoles();

            return null;
        }

        public MemberBuilder withAddress(final Address address) {
            doReturn(address).when(member).address();
            return this;
        }
    }
}
