package com.zain.ksa.alm.financials.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * Used by DepreciationScheduler and reconciliation async tasks.
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
     * Used by ExportExecutor for on-demand (filtered) exports.
     * Queue capacity of 10 prevents unbounded export pile-up.
     */
    @Bean(name = "exportTaskExecutor")
    public Executor exportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("async-export-");
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated pool for pre-warm background exports.
     * Kept separate from on-demand exports so a slow pre-warm
     * does not block user-triggered filtered exports.
     * Low core size — pre-warm runs at most once per job type at a time.
     */
    @Bean(name = "preWarmTaskExecutor")
    public Executor preWarmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("prewarm-export-");
        executor.initialize();
        return executor;
    }
}