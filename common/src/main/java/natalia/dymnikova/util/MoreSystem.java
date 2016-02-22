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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static java.io.File.pathSeparator;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.util.Arrays.stream;

/**
 */
public class MoreSystem {

    private static final Impl impl = makeImpl();

    public static String osName () { return getProperty("os.name").toLowerCase(); }

    public static boolean isWindows () { return osName().startsWith("windows"); }

    public static boolean isMac () { return  osName().startsWith("mac") || osName().startsWith("darwin"); }

    private static Impl makeImpl() {
        if (isWindows()) {
            return  new WindowsImpl();
        } else {
            return  new LinuxImpl();
        }
    }

    public static Optional<Path> findExecutableInPath(final String name) {
        return impl.findExecutableInPath(name);
    }

    public static abstract class Impl {
        public abstract Optional<Path> findExecutableInPath(final String name);

        protected String[] getPath() {
            return getenv("PATH").split(pathSeparator);
        }
    }

    public static class LinuxImpl extends Impl {

        @Override
        public Optional<Path> findExecutableInPath(String name) {
            return stream(getPath())
                    .map(e -> Paths.get(e, name))
                    .filter(Files::exists)
                    .findFirst();
        }

    }

    public static class WindowsImpl extends Impl {

        @Override
        public Optional<Path> findExecutableInPath(final String name) {
            final String executableName = name + ".exe";
            return stream(getPath())
                    .map(e -> Paths.get(e, executableName))
                    .filter(Files::exists)
                    .findFirst();
        }
    }
}
