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

package natalia.dymnikova.cluster;

import natalia.dymnikova.configuration.ConfiguredEnvironment;
import natalia.dymnikova.util.MoreFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.time.Duration;

import static java.lang.Runtime.getRuntime;
import static java.nio.file.Paths.get;
import static java.time.Duration.ofSeconds;
import static natalia.dymnikova.util.MoreFiles.lockFile;

/**
 *
 */
public class ClusterMemberMain {
    private static final Logger log = LoggerFactory.getLogger(ClusterMemberMain.class);

    private AnnotationConfigApplicationContext ctx;

    private FileLock fileLock;

    @SuppressWarnings("unchecked")
    protected void startNode() {
        fileLock = lockFile(get(".", ".process-lock"), ofSeconds(15));

        final ConfiguredEnvironment environment = new ConfiguredEnvironment();

        final String version = environment.getConfig().getString("natalia-dymnikova.version");
        final String build = environment.getConfig().getString("natalia-dymnikova.build");
        log.info("Log analysis version '{}' build '{}'", version, build);

        ctx = new ClusterMemberSpringContext();
        getRuntime().addShutdownHook(new Thread(ctx::stop));

        ctx.setEnvironment(environment);

        ctx.scan("natalia.dymnikova");
        ctx.refresh();
        ctx.start();
    }

    public ApplicationContext getCtx() {
        return ctx;
    }

}
