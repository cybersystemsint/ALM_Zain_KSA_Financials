package com.zain.ksa.alm.financials.scheduler;

import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.service.impl.PreWarmExportJob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Hooks into the application lifecycle and nightly data events to
 * keep pre-warmed exports fresh.
 *
 * Nightly flow:
 *   2:00am  → your ETL / reconciliation process writes new unmapped data
 *   2:30am  → afterNightlyDataLoad() fires → invalidates + regenerates
 *             unmapped_active, unmapped_passive, unmapped_it exports
 *
 * Monthly flow:
 *   DepreciationScheduler.processDepreciation() completes
 *   → calls afterDepreciationRun() → invalidates + regenerates
 *     depreciation export only
 *
 * Startup flow:
 *   App starts → onApplicationReady() runs via preWarmTaskExecutor
 *   so it never blocks Spring context startup.
 */
@Component
public class DataRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRefreshScheduler.class);

    private final PreWarmExportJob preWarmExportJob;

    public DataRefreshScheduler(PreWarmExportJob preWarmExportJob) {
        this.preWarmExportJob = preWarmExportJob;
    }

    /**
     * Fires after nightly reconciliation/ETL completes.
     * Uncomment @Scheduled and adjust cron to your ETL window.
     */
    // // @Scheduled(cron = "0 30 2 * * *")
    public void afterNightlyDataLoad() {
        log.info("Nightly data load complete — refreshing unmapped pre-warmed exports");
        preWarmExportJob.warmSingle("unmapped_active",  ExportFormat.EXCEL);
        preWarmExportJob.warmSingle("unmapped_passive", ExportFormat.EXCEL);
        preWarmExportJob.warmSingle("unmapped_it",      ExportFormat.EXCEL);
    }

    /**
     * Called programmatically by DepreciationHistoryController
     * after monthly depreciation run completes.
     */
    public void afterDepreciationRun() {
        log.info("Depreciation run complete — refreshing depreciation pre-warmed export");
        preWarmExportJob.warmSingle("depreciation", ExportFormat.EXCEL);
    }

    /**
     * Runs after Spring context is fully started.
     * @Async on an @EventListener dispatches it to the thread pool
     * instead of blocking the main startup thread — Java 17 compatible.
     */
    // @Async("preWarmTaskExecutor")
    // @EventListener(ApplicationReadyEvent.class)
    // public void onApplicationReady() {
    //     log.info("Application ready — pre-warming all static exports in background");
    //     preWarmExportJob.warmAll();
    // }
}