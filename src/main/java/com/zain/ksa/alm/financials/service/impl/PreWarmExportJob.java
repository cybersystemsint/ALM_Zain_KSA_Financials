package com.zain.ksa.alm.financials.service.impl;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.service.ExportJobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manages pre-warmed (background) exports for static/rarely-changing datasets.
 *
 * <h3>When pre-warm is triggered</h3>
 * <ul>
 *   <li><b>ApplicationReadyEvent</b> — on startup so files are ready from day one</li>
 *   <li><b>UnmappedInventoryScheduler</b> — after nightly reconciliation</li>
 *   <li><b>DepreciationScheduler</b> — after monthly depreciation run</li>
 *   <li><b>FarReportService.processUpload()</b> — after any FAR upload completes</li>
 *   <li><b>Scheduled cron</b> — daily at 2 AM (configurable)</li>
 * </ul>
 *
 * <p>Pre-warmed jobs use a deterministic ID so the same file is served to all
 * users until the scheduler invalidates and replaces it.</p>
 *
 * <p>ID format: {@code "prewarm-{exportType}-{FORMAT}"}</p>
 */
@Component
public class PreWarmExportJob {

    private static final Logger log = LoggerFactory.getLogger(PreWarmExportJob.class);

    private final ExportJobService exportJobService;

    /**
     * All export types that get pre-warmed. FAR report included because it only
     * changes on upload or depreciation sync — perfect pre-warm candidate.
     */
    private static final List<PreWarmTarget> TARGETS = List.of(
            new PreWarmTarget("unmapped_active",  ExportFormat.EXCEL),
            new PreWarmTarget("unmapped_passive", ExportFormat.EXCEL),
            new PreWarmTarget("unmapped_it",      ExportFormat.EXCEL),
            new PreWarmTarget("depreciation",     ExportFormat.EXCEL),
            new PreWarmTarget("far_report",       ExportFormat.EXCEL)
    );

    public PreWarmExportJob(ExportJobService exportJobService) {
        this.exportJobService = exportJobService;
    }

    /**
     * Scheduled pre-warm: runs daily at 2 AM server time.
     * Configurable via {@code app.export.prewarm-cron}.
     */
    @Scheduled(cron = "${app.export.prewarm-cron:0 0 2 * * *}")
    public void scheduledWarmAll() {
        warmAll();
    }

    /** Pre-warms all configured export types. Called on application startup. */
    public void warmAll() {
        log.info("Pre-warming all static exports ({} targets)", TARGETS.size());
        TARGETS.forEach(t -> warmSingle(t.exportType(), t.format()));
    }

    /**
     * Pre-warms a single export type.
     * Invalidates the old file first so users never download stale data.
     * Runs on the dedicated preWarmTaskExecutor thread pool.
     */
    @Async("preWarmTaskExecutor")
    public void warmSingle(String exportType, ExportFormat format) {
        String jobId = buildPreWarmJobId(exportType, format);

        try {
            exportJobService.invalidateExport(jobId);

            log.info("Starting pre-warm for jobId={}", jobId);
            exportJobService.startPreWarmedExport(
                    exportType,
                    new DynamicFilterRequest(),
                    format,
                    jobId
            );
        } catch (Exception e) {
            log.error("[PreWarm] Failed for {}: {}", jobId, e.getMessage(), e);
        }
    }

    /**
     * Builds the deterministic job ID for a pre-warmed export.
     * Used here and in ExportJobServiceImpl to look up the pre-warm cache.
     */
    public static String buildPreWarmJobId(String exportType, ExportFormat format) {
        return "prewarm-" + exportType + "-" + format.name();
    }

    private record PreWarmTarget(String exportType, ExportFormat format) {}
}