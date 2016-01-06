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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static natalia.dymnikova.util.MoreClasses.getDeclaredMethod;
import static natalia.dymnikova.util.MoreClasses.setAccessible;

/**
 * 
 */
public class MoreRegex {

    private static final Pattern spaces = compile("\\s");

    public static Pattern regex(final String... patternString) {
        return compile(asList(patternString)
                .parallelStream()
                .collect(joining()),
                Pattern.COMMENTS
        );
    }

    public static String regexStringFromTimeFormat(final String timeFormat) {
        final StringBuilder b = new StringBuilder();

        int patternCharCount = 0;
        char lastPatternChar = 0;

        for (int i = 0; i < timeFormat.length(); i++) {
            final char c = timeFormat.charAt(i);
            switch (c) {
                case 'y':
                case 'M':
                case 'd':
                case 'H':
                case 's':
                case 'm':
                case 'S':
                    if (patternCharCount > 0 && lastPatternChar != c) {
                        b.append("\\d{").append(patternCharCount).append("}");
                        patternCharCount = 0;
                    }
                    patternCharCount++;
                    lastPatternChar = c;
                    break;

                case '\'':
                    break;

                default:
                    if (patternCharCount != 0) {
                        b.append("\\d{").append(patternCharCount).append("}");
                        patternCharCount = 0;
                    }
                    b.append(c);
            }
        }

        b.append("\\d{").append(patternCharCount).append("}");

        return b.toString();
    }

    public static String replaceAll(final String src, final String chars, final Function<Character, String> replacementFactory) {
        int length = src.length();
        final StringBuilder b = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = src.charAt(i);
            if (chars.indexOf(c) >= 0) {
                b.append(replacementFactory.apply(c));
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static final Method namedGroups = setAccessible(getDeclaredMethod(
            Pattern.class, "namedGroups"
    ));

    public static List<String> getGroupsNames(final Pattern regex) {
        try {
            return new ArrayList<>(((Map<String, Integer>) namedGroups.invoke(regex)).keySet());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw MoreThrowables.unchecked(e);
        }
    }
}
