package com.zain.ksa.alm.financials.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async + scheduling configuration.
 *
 * <h3>Thread pools</h3>
 * <ul>
 *   <li><b>schedulerExecutor</b> — for DepreciationScheduler, reconciliation, and other
 *       periodic tasks. Core=2, Max=4.</li>
 *   <li><b>preWarmTaskExecutor</b> — dedicated pool for pre-warm background exports.
 *       Kept separate so a slow pre-warm doesn't block user-triggered exports or
 *       scheduled tasks. Core=1, Max=2.</li>
 * </ul>
 *
 * <h3>Export thread pool</h3>
 * <p>The on-demand export thread pool is now managed inside
 * {@link com.zain.ksa.alm.financials.service.impl.ExportExecutor} directly,
 * using a {@code ThreadPoolExecutor} with a bounded queue and a {@code Semaphore}
 * for concurrency control. This gives tighter control than Spring's {@code @Async}:
 * <ul>
 *   <li>Bounded queue (10) prevents unbounded export pile-up</li>
 *   <li>CallerRunsPolicy provides backpressure</li>
 *   <li>Semaphore ensures max N concurrent exports regardless of queue state</li>
 *   <li>Daemon threads don't block JVM shutdown</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Used by DepreciationScheduler, reconciliation, and other periodic async tasks.
     */
    @Bean(name = "schedulerExecutor")
    public Executor schedulerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("async-scheduler-");
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated pool for pre-warm background exports.
     * Low core size — pre-warm runs at most once per export type at a time.
     * Rejection policy: log and skip (pre-warm is best-effort optimization).
     */
    @Bean(name = "preWarmTaskExecutor")
    public Executor preWarmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("prewarm-export-");
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("[AsyncConfig] Pre-warm task rejected (queue full), skipping — " +
                     "this is expected under heavy load and does not affect user exports");
        });
        executor.initialize();
        return executor;
    }
}