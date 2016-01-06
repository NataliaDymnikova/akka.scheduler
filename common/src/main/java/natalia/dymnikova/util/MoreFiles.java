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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Files.write;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static natalia.dymnikova.util.MoreThrowables.propagateUnchecked;

/**
 * 
 */
public class MoreFiles {
    private static final Logger log = LoggerFactory.getLogger(MoreFiles.class);

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
            final Path tmpFile = file.resolveSibling(file.getFileName().toString() + ".tmp");
            write(tmpFile, content.getBytes(UTF_8));
            Files.move(tmpFile, file, REPLACE_EXISTING);
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

    public interface FileCallback {
        void apply(final RandomAccessFile file) throws IOException;
    }

    public static abstract class TryLockCondition {

        public abstract boolean shouldContinue();

        public static TryLockCondition timeout(final long timeout, final TimeUnit timeUnit) {
            return new TimeoutCondition(timeout, timeUnit);
        }

        public static TryLockCondition and(final TryLockCondition... conditions) {
            return new AndCondition(conditions);
        }
    }

    private static class AndCondition extends TryLockCondition {

        private final TryLockCondition[] conditions;

        public AndCondition(final TryLockCondition[] conditions) {
            this.conditions = conditions;
        }

        @Override
        public boolean shouldContinue() {
            for (TryLockCondition condition : conditions) {
                if (!condition.shouldContinue()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class TimeoutCondition extends TryLockCondition {

        private final long end;

        public TimeoutCondition(final long timeout, final TimeUnit timeUnit) {
            end = currentTimeMillis() + timeUnit.toMillis(timeout);
        }

        @Override
        public boolean shouldContinue() {
            return end > currentTimeMillis();
        }
    }

    public static void tryLock(final File file,
                               final String mode,
                               final TryLockCondition condition,
                               final FileCallback fileCallback) throws IOException, InterruptedException {
        if (!file.exists()) {
            throw new FileNotFoundException(file.toString());
        }

        while (condition.shouldContinue()) {
            try {
                try (final RandomAccessFile raFile = new RandomAccessFile(file, mode)) {
                    try (final FileLock lock = raFile.getChannel().lock()) {
                        log.trace("Successfully acquired lock on {}", file);

                        fileCallback.apply(raFile);
                        return;
                    }
                }
            } catch (final FileNotFoundException | OverlappingFileLockException e) {
                log.trace("Failed to acquire lock on file {} due to error {}", file, e.getMessage());
                sleep(1000L);
            }
        }
        throw new OverlappingFileLockException();
    }

    private static class DeleteDirectoryContent extends SimpleFileVisitor<Path> {
        private static final Logger log = LoggerFactory.getLogger(DeleteDirectoryContent.class);

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            log.trace("Deleting {}", file);
            delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
            log.trace("Deleting {}", file);
            delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            if (exc == null) {
                log.trace("Deleting {}", dir);
                delete(dir);
                return FileVisitResult.CONTINUE;
            } else {
                throw exc;
            }
        }
    }
}
