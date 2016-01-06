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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;

import static java.lang.Thread.currentThread;
import static natalia.dymnikova.util.MoreThrowables.*;
import static natalia.dymnikova.util.MoreThrowables.propagate;
import static natalia.dymnikova.util.MoreThrowables.propagateUnchecked;
import static natalia.dymnikova.util.MoreThrowables.unchecked;

/**
 * 
 */
public class MoreClasses {
    public static Class<?> forName(final String className) {
        try {
            return currentThread().getContextClassLoader().loadClass(className);
        } catch (final ClassNotFoundException e) {
            throw unchecked(e);
        }
    }

    public static URI getLocation(final Class<?> aClass) {
        final String classFileName = aClass.getName().replace('.', '/') + ".class";
        final URL resource = aClass.getClassLoader().getResource(
                classFileName
        );

        if ("jar".equalsIgnoreCase(resource.getProtocol())) {
            try {
                return ((JarURLConnection) resource.openConnection())
                        .getJarFileURL()
                        .toURI();
            } catch (final IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            return URI.create(resource.toString().substring(
                    0, resource.toString().length() - classFileName.length()
            ));
        }
    }

    public static Method getDeclaredMethod(final Class<?> aClass, String methodName) {
        try {
            return aClass.getDeclaredMethod(methodName);
        } catch (final NoSuchMethodException e) {
            throw unchecked(e);
        }
    }

    public static Method setAccessible(final Method method) {
        method.setAccessible(true);
        return method;
    }
}
