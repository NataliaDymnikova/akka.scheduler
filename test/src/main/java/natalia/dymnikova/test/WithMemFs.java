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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static natalia.dymnikova.util.MoreThrowables.propagateUnchecked;

/**
 * 
 */
public class WithMemFs implements TestRule {

    private FileSystem fs;

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                        .setAttributeViews("basic", "user")
                        .build());

                try {
                    base.evaluate();
                } finally {
                    fs.close();
                }
            }
        };
    }

    public URI file(final Path filePath) {
        final Path path = fs.getPath(filePath.toString());
        try {
            return Files.createFile(path).toUri();
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public URI dir(final Path ditPath) {
        final Path path = fs.getPath(ditPath.toString());
        try {
            return Files.createDirectories(path).toUri();
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public boolean isDirectory(final Path path) {
        return Files.isDirectory(fs.getPath(path.toString()));
    }

    public Path root() {
        return fs.getRootDirectories().iterator().next();
    }
}
