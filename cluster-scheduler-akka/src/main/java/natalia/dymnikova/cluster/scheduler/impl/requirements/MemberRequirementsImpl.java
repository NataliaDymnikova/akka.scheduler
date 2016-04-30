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

import natalia.dymnikova.cluster.scheduler.akka.Flow.MemberCheckResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.lang.String.join;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.MemberCheckResultVerdict.Ok;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.MemberCheckResultVerdict.Veto;

/**
 */
public class MemberRequirementsImpl implements MemberRequirements {
    private ArrayList<Check> checks;

    public MemberRequirementsImpl(ArrayList<Check> checks) {
        this.checks = checks;
    }

    @Override
    public List<MemberCheckResult> eval(final MemberInfo memberInfo) {
        return checks.stream().map(c -> c.eval(memberInfo)).collect(Collectors.toList());
    }

    public static Check memberRoles(final Set<String> roles) {
        return new RolesCheck(roles);
    }

    public interface Check extends Serializable {
        MemberCheckResult eval(final MemberInfo memberInfo);
    }

    public static class RolesCheck implements Check {
        private Set<String> roles;

        public RolesCheck(final Set<String> roles) {
            this.roles = roles;
        }

        @Override
        public MemberCheckResult eval(final MemberInfo memberInfo) {
            final Set<String> roles = memberInfo.getRoles();
            return roles.stream()
                    .filter(this.roles::contains)
                    .findAny()
                    .map(b -> ok(getClass()))
                    .orElseGet(veto(
                            getClass(),
                            "Member %s with roles [%s] does not match requirements for roles [%s]",
                            memberInfo.getAddress(),
                            join(",", roles),
                            join(",", this.roles))
                    );
        }
    }

    private static MemberCheckResult ok(final Class<? extends RolesCheck> check) {
        return MemberCheckResult.newBuilder().setType(check.getName()).setVerdict(Ok).build();
    }

    private static Supplier<MemberCheckResult> veto(final Class<? extends Check> check,
                                                    final String fmt,
                                                    final Object... args) {
        return () -> MemberCheckResult.newBuilder()
                .setMessage(format(fmt, args))
                .setType(check.getName())
                .setVerdict(Veto)
                .build();
    }
}
