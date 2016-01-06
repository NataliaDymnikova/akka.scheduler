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

package natalia.dymnikova.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 */
public class MoreThrowables {
    public static UncheckedIOException propagateUnchecked(final IOException e) {
        throw new UncheckedIOException(e);
    }

    public static RuntimeException noSuchElement(final String format, final Object ...params) {
        return new NoSuchElementException(format(format, params));
    }

    public static RuntimeException unchecked(final String format, final Object ...params) {
        return new RuntimeException(format(format, params));
    }

    public static RuntimeException unchecked(final Throwable e, final String format, final Object ...params) {
        return new RuntimeException(format(format, params), e);
    }

    public static RuntimeException unchecked(final Throwable e) {
        return new RuntimeException(e);
    }

    private static final Pattern fmtToken = Pattern.compile("\\{\\}");

    private static String format(final String format, final Object... replacements) {
        final StringBuilder b = new StringBuilder();

        int startPos = 0;
        int count = 0;

        final Matcher matcher = fmtToken.matcher(format);
        while (matcher.find()) {
            b.append(format.substring(startPos, matcher.start()));
            b.append(get(replacements, count++).orElse("{}"));
            startPos = matcher.end();
        }

        b.append(format.substring(startPos));
        return b.toString();
    }

    private static Optional<Object> get(final Object[] arr, final int index) {
        if (index >= 0 && index < arr.length) {
            return Optional.of(arr[index]);
        } else {
            return Optional.empty();
        }
    }

    public static void propagate(final Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else if (t instanceof Error) {
            throw (Error) t;
        } else {
            throw unchecked(t);
        }
    }

    @FunctionalInterface
    public interface ThrowingFunction<A, B> extends Function<A, B> {
        default B apply(A a) {
            try {
                return applyThrowing(a);
            } catch (final Exception e) {
                throw unchecked(e);
            }
        }

        B applyThrowing(A a) throws Exception;
    }
}
