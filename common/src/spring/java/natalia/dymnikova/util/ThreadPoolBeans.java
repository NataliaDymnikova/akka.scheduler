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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

/**
 * 
 */
@Configuration
public class ThreadPoolBeans {
    private static final Logger log = LoggerFactory.getLogger(ThreadPoolBeans.class);

    private ExecutorService commonPurposeExecutor0() {
        final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .setNameFormat("common-purpose-%03d")
                .setDaemon(true)
                .build()
        );

        log.debug("Constructed common purpose executor {}", executor);
        return executor;
    }

    public ScheduledExecutorService commonPurposeScheduler0() {
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10, new ThreadFactoryBuilder()
                .setNameFormat("common-purpose-scheduler-%03d")
                .setDaemon(true)
                .setUncaughtExceptionHandler((t, e) -> log.debug("Uncaught exception in thread {}", t.getName(), e))
                .build()
        );

        log.debug("Constructed common purpose scheduler {}", scheduler);
        return scheduler;
    }

    public ExecutorService firstPriorityTasksExecutor0() {
        final ThreadFactory factory = new ThreadFactoryBuilder()
            .setNameFormat("first-priority-%03d")
            .setDaemon(true)
            .build();

        final ExecutorService executor = new ThreadPoolExecutor(
            0,
            Integer.MAX_VALUE,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            factory
        );

        log.debug("Constructed first priority tasks executor {}", executor);
        return executor;
    }

    /**
     * An executor which maintains threads for high priority tasks execution.
     * <p>
     * It is advised to use leased threads for a short period of time.
     */
    @Bean
    public ListeningExecutorService firstPriorityTasksExecutor() {
        return listeningDecorator(firstPriorityTasksExecutor0());
    }

    /**
     * An executor which maintains threads for running tasks which do not
     * require immediate execution.
     * <p>
     * Threads can be leased for a long period of time.
     */
    @Bean
    public ListeningExecutorService commonPurposeExecutor() {
        return listeningDecorator(commonPurposeExecutor0());
    }

    @Bean
    public ListeningScheduledExecutorService commonPurposeScheduler() {
        return listeningDecorator(commonPurposeScheduler0());
    }

    /**
     * An executor which maintains threads for long running background tasks.
     * <p>
     * Threads can be leased for a long period of time.
     */
    @Bean
    public ListeningExecutorService backgroundTasksExecutor() {
        return listeningDecorator(backgroundTasksExecutor0());
    }

    public ExecutorService backgroundTasksExecutor0() {
        final ThreadFactory factory = new ThreadFactoryBuilder()
            .setNameFormat("background-%03d")
            .setDaemon(true)
            .build();

        final ExecutorService executor = new ThreadPoolExecutor(
            0,
            Integer.MAX_VALUE,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            factory
        );

        log.debug("Constructed background tasks executor {}", executor);
        return executor;
    }
}
