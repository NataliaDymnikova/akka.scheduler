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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.util.streamex.EntryStream;
import javax.util.streamex.StreamEx;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.google.common.collect.Maps.transformEntries;
import static java.lang.String.valueOf;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.nio.ByteBuffer.wrap;
import static java.nio.channels.FileChannel.open;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.getFileAttributeView;
import static java.nio.file.Files.move;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Files.write;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.time.Instant.now;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static javax.util.streamex.EntryStream.of;
import static natalia.dymnikova.util.MoreThrowables.propagateUnchecked;

/**
 *
 */
public class MoreFiles {
    private static final Logger log = LoggerFactory.getLogger(MoreFiles.class);
    public static final long DefaultTimoutAfterLockFailure = 1000L;

    public static void deleteRecursively(final Path path) {
        if (exists(path)) {
            try {
                walkFileTree(path, new DeleteDirectoryContent());
            } catch (final IOException e) {
                throw propagateUnchecked(e);
            }
        }
    }

    public static void replace(final Path file,
                               final String content) {
        try {
            createDirectories(file.toAbsolutePath().normalize().getParent());

            final Path tmpFile = file.resolveSibling(file.getFileName().toString() + ".tmp");
            write(tmpFile, content.getBytes(UTF_8));
            move(tmpFile, file, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public static Function<Path, AsynchronousFileChannel> asyncChannelFactory() {
        return path -> asyncChannel(path);
    }

    public static AsynchronousFileChannel asyncChannel(final Path path) {
        try {
            return AsynchronousFileChannel.open(path);
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public static void close(final Closeable closeable) {
        try {
            closeable.close();
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public static InputStream gzippedInputStream(final Path path) {
        try {
            return new GZIPInputStream(Files.newInputStream(path));
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public static void createDirectories(final Path dir) {
        try {
            Files.createDirectories(dir.toAbsolutePath().normalize());
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public static Stream<Path> files(final Path dir, final String glob) {
        if (!Files.exists(dir)) {
            return Collections.<Path>emptyList().stream();
        }

        try {
            final PathMatcher pathMatcher = dir.getFileSystem().getPathMatcher("glob:" + glob);

            final List<Path> paths = new ArrayList<>();
            walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, BasicFileAttributes attrs) throws IOException {
                    if (pathMatcher.matches(dir.relativize(file))) {
                        paths.add(file);
                    }
                    return CONTINUE;
                }
            });

            return paths.stream();
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public static void withLock(final Path path, final Runnable runnable, final Duration timeout) {
        withLock(path, () -> {
            runnable.run();
            return null;
        }, timeout);
    }

    public static <T> T withLock(final Path lockFile, final Supplier<T> supplier, final Duration timeout) {
        createDirectories(lockFile.toAbsolutePath().normalize().getParent());

        final long end = currentTimeMillis() + timeout.toMillis();

        while (end > currentTimeMillis()) {
            try (final FileChannel fc = open(lockFile, CREATE, WRITE, TRUNCATE_EXISTING)) {
                final FileLock fileLock = fc.tryLock(0, Long.MAX_VALUE, false);
                if (fileLock != null) {
                    try {
                        writeLockIdentity(fc);
                        return supplier.get();
                    } finally {
                        fileLock.close();
                    }
                } else {
                    log.trace("Cannot get lock on {}", lockFile);
                    try {
                        sleep(DefaultTimoutAfterLockFailure);
                    } catch (final InterruptedException e) {
                        currentThread().interrupt();
                        throw new UncheckedIOException(new IOException("Cannot lock file " + lockFile));
                    }
                }
            } catch (final IOException e) {
                throw propagateUnchecked(e);
            }
        }

        throw new UncheckedIOException(new IOException(
                "Cannot lock file " + lockFile + " in " + timeout + " process identity holding lock"
        ));
    }

    public static String toString(final Path lockFile) {
        try {
            return new String(readAllBytes(lockFile), UTF_8);
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    private static void writeLockIdentity(final FileChannel fc) throws IOException {
        fc.write(wrap((getRuntimeMXBean().getName() + "\n").getBytes(UTF_8)));
        fc.write(wrap((get(".").toAbsolutePath().normalize() + "\n").getBytes(UTF_8)));
        fc.write(wrap((ISO_INSTANT.format(now()) + "\n").getBytes(UTF_8)));
        fc.force(true);
    }

    public static void delete(final Path path) {
        if (exists(path)) {
            try {
                Files.delete(path);
            } catch (final IOException e) {
                throw propagateUnchecked(e);
            }
        }
    }

    public static FileLock lockFile(final Path lockFile,
                                    final Duration timeout) {
        createDirectories(lockFile.toAbsolutePath().normalize().getParent());

        final long end = currentTimeMillis() + timeout.toMillis();

        while (end > currentTimeMillis()) {
            try (final FileChannel fc = open(lockFile, CREATE, WRITE, TRUNCATE_EXISTING)) {
                final FileLock fileLock = fc.tryLock(0, Long.MAX_VALUE, false);
                if (fileLock != null) {
                    writeLockIdentity(fc);
                    return fileLock;
                } else {
                    log.trace("Cannot get lock on {}", lockFile);
                    try {
                        sleep(DefaultTimoutAfterLockFailure);
                    } catch (final InterruptedException e) {
                        currentThread().interrupt();
                        throw new UncheckedIOException(new IOException("Cannot lock file " + lockFile));
                    }
                }
            } catch (final IOException e) {
                throw propagateUnchecked(e);
            }
        }
        throw new UncheckedIOException(new IOException("Cannot lock file " + lockFile));
    }

    public static Stream<String> lines(final Path path) {
        try {
            return Files.lines(path);
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public static void setAttributes(final Path path, final Map<String, String> attributes) {
        final UserDefinedFileAttributeView view = getFileAttributeView(path, UserDefinedFileAttributeView.class);
        of(attributes)
                .mapValues(v -> v.getBytes(UTF_8))
                .mapValues(ByteBuffer::wrap)
                .forEach(e -> {
                    try {
                        view.write(e.getKey(), e.getValue());
                    } catch (final IOException ex) {
                        throw propagateUnchecked(ex);
                    }
                });
    }

    public static Map<String, String> getAttributes(final Path path) {
        try {
            return ImmutableMap.<String, String>builder()
                    .putAll(transformEntries(
                            readAttributes(path, "*"), (key, value) -> attributeValue(value)
                    ))
                    .putAll(transformEntries(
                            readAttributes(path, "user:*"), (key, value) -> attributeValue(value)
                    ))
                    .build();
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    private static String attributeValue(final Object attributeValue) {
        if (attributeValue instanceof byte[]) {
            return new String((byte[]) attributeValue, UTF_8);
        } else {
            return valueOf(attributeValue);
        }
    }

    private static class DeleteDirectoryContent extends SimpleFileVisitor<Path> {
        private static final Logger log = LoggerFactory.getLogger(DeleteDirectoryContent.class);

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            log.trace("Deleting {}", file);
            delete(file);
            return CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
            log.trace("Deleting {}", file);
            delete(file);
            return CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            if (exc == null) {
                log.trace("Deleting {}", dir);
                delete(dir);
                return CONTINUE;
            } else {
                throw exc;
            }
        }
    }
}
