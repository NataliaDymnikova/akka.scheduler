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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static java.io.File.pathSeparator;
import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 */
public class MoreSystem {

    private static final Impl impl = makeImpl();

    public static String osName() {
        return getProperty("os.name").toLowerCase();
    }

    public static boolean isWindows() {
        return osName().startsWith("windows");
    }

    public static boolean isMac() {
        return osName().startsWith("mac") || osName().startsWith("darwin");
    }

    private static Impl makeImpl() {
        if (isWindows()) {
            return new WindowsImpl();
        } else {
            return new LinuxImpl();
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

        public abstract void kill(String pid) throws InterruptedException;
    }

    public static class LinuxImpl extends Impl {

        @Override
        public Optional<Path> findExecutableInPath(String name) {
            return stream(getPath())
                    .map(e -> Paths.get(e, name))
                    .filter(Files::exists)
                    .findFirst();
        }

        @Override
        public void kill(String pid) throws InterruptedException {
            try {
                new ProcessBuilder("kill", "-9", pid)
                        .redirectErrorStream(true)
                        .redirectOutput(INHERIT)
                        .start()
                        .waitFor(10, SECONDS);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    public static class WindowsImpl extends Impl {

        private Optional<Path> killBinnary = findExecutableInPath("kill");

        @Override
        public Optional<Path> findExecutableInPath(final String name) {
            final String executableName = name + ".exe";
            return stream(getPath())
                    .map(e -> Paths.get(e, executableName))
                    .filter(Files::exists)
                    .findFirst();
        }

        @Override
        public void kill(final String pid) throws InterruptedException {
            try {
                new ProcessBuilder(getKillCommand(pid))
                        .redirectErrorStream(true)
                        .redirectOutput(INHERIT)
                        .start()
                        .waitFor(10, SECONDS);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private String[] getKillCommand(final String pidString) {
            return killBinnary.map((p) ->
                    new String[]{"kill", "-9", "-f", pidString}
            ).orElseGet(() ->
                    new String[]{"taskkill", "/F", "/PID", pidString}
            );
        }
    }

    public static void kill(final String pid) throws IOException, InterruptedException {
        impl.kill(pid);
    }

}
