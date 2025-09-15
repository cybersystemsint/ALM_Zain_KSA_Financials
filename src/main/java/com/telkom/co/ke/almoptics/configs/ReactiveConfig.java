package com.telkom.co.ke.almoptics.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PreDestroy;

@Configuration
public class ReactiveConfig {

    private Scheduler databaseScheduler;

    @Bean
    public Scheduler databaseScheduler() {
        this.databaseScheduler = Schedulers.newBoundedElastic(
                Runtime.getRuntime().availableProcessors() * 2,
                Integer.MAX_VALUE,
                "db-operations",
                60,
                true
        );
        return this.databaseScheduler;
    }

    @PreDestroy
    public void cleanup() {
        if (databaseScheduler != null) {
            databaseScheduler.dispose();
        }
    }
}