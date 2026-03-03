package com.zain.ksa.alm.financials.service.impl;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.service.ExportJobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages export job lifecycle: creation, status tracking, file cleanup.
 *
 * <h3>Changes from previous version</h3>
 * <p>None to the public API or job lifecycle. The only internal change is that
 * {@link #runExport} now calls {@link ExportExecutor#execute} which manages its
 * own bounded thread pool and semaphore, instead of relying on Spring's
 * {@code @Async("exportTaskExecutor")}. This gives tighter control over
 * concurrency, queue depth, and backpressure.</p>
 */
@Service
public class ExportJobServiceImpl implements ExportJobService {

    private static final Logger log = LoggerFactory.getLogger(ExportJobServiceImpl.class);

    private final ExportExecutor exportExecutor;

    @Value("${app.export.dir:${java.io.tmpdir}/alm-exports}")
    private String exportDir;

    @Value("${app.export.retention-hours:2}")
    private int retentionHours;

    private final ConcurrentHashMap<String, JobInfo> jobs = new ConcurrentHashMap<>();

    public ExportJobServiceImpl(ExportExecutor exportExecutor) {
        this.exportExecutor = exportExecutor;
    }

    // ── User-facing export ────────────────────────────────────────────────────

    @Override
    public String startExport(String exportType, DynamicFilterRequest filter, ExportFormat format) {

        // If no filters applied → check for a pre-warmed job
        if (isUnfiltered(filter)) {
            String preWarmId = PreWarmExportJob.buildPreWarmJobId(exportType, format);
            JobInfo preWarmed = jobs.get(preWarmId);

            if (preWarmed != null && "COMPLETED".equals(preWarmed.status)) {
                boolean fileExists = preWarmed.filePath != null
                        && new File(preWarmed.filePath).exists();
                if (fileExists) {
                    log.info("Serving pre-warmed export for type={} format={}", exportType, format);
                    return preWarmId;
                }
                log.warn("Pre-warmed file missing on disk for jobId={}, falling back to on-demand",
                        preWarmId);
                jobs.remove(preWarmId);
            }

            if (preWarmed != null && "RUNNING".equals(preWarmed.status)) {
                log.info("Pre-warm still running for type={}, user will poll", exportType);
                return preWarmId;
            }

            log.warn("Pre-warmed export unavailable for type={}, running on-demand", exportType);
        }

        return runExport(exportType, filter, format,
                UUID.randomUUID().toString().substring(0, 8), false);
    }

    // ── Scheduler/pre-warm export ─────────────────────────────────────────────

    @Override
    public void startPreWarmedExport(String exportType, DynamicFilterRequest filter,
                                      ExportFormat format, String fixedJobId) {
        log.info("Launching pre-warmed export: jobId={}", fixedJobId);
        runExport(exportType, filter, format, fixedJobId, true);
    }

    // ── Shared run logic ──────────────────────────────────────────────────────

    private String runExport(String exportType, DynamicFilterRequest filter,
                              ExportFormat format, String jobId, boolean isPreWarmed) {
        File dir = new File(exportDir);
        if (!dir.exists()) dir.mkdirs();

        String extension = format == ExportFormat.EXCEL ? ".xlsx" : ".csv";
        String filename  = jobId + extension;
        String filePath  = exportDir + File.separator + filename;

        JobInfo job = new JobInfo();
        job.jobId       = jobId;
        job.exportType  = exportType;
        job.format      = format;
        job.status      = "RUNNING";
        job.fileName    = filename;
        job.startedAt   = LocalDateTime.now();
        job.isPreWarmed = isPreWarmed;
        jobs.put(jobId, job);

        // ExportExecutor manages its own thread pool + semaphore internally
        exportExecutor.execute(
                jobId, exportType, filter, format, filePath,
                (totalRows, processedRows) -> {
                    job.totalRows     = totalRows;
                    job.processedRows = processedRows;
                },
                result -> {
                    if (result.success) {
                        job.status      = "COMPLETED";
                        job.filePath    = result.filePath;
                        job.completedAt = LocalDateTime.now();
                        log.info("Export COMPLETED: jobId={}, preWarmed={}, rows={}",
                                jobId, isPreWarmed, job.totalRows);
                    } else {
                        job.status       = "FAILED";
                        job.errorMessage = result.error;
                        job.completedAt  = LocalDateTime.now();
                        log.error("Export FAILED: jobId={}, error={}", jobId, result.error);
                    }
                }
        );

        return jobId;
    }

    // ── Invalidate ────────────────────────────────────────────────────────────

    @Override
    public void invalidateExport(String jobId) {
        JobInfo job = jobs.remove(jobId);
        if (job != null) {
            if (job.filePath != null) {
                File file = new File(job.filePath);
                if (file.exists()) {
                    file.delete();
                    log.info("Invalidated export file: {}", job.filePath);
                }
            }
            log.info("Invalidated export job: {}", jobId);
        }
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> getStatus(String jobId) {
        JobInfo job = jobs.get(jobId);
        if (job == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId",       job.jobId);
        result.put("exportType",  job.exportType);
        result.put("status",      job.status);
        result.put("preWarmed",   job.isPreWarmed);
        result.put("startedAt",   job.startedAt   != null ? job.startedAt.toString()   : null);
        result.put("completedAt", job.completedAt != null ? job.completedAt.toString() : null);
        result.put("totalRows",     job.totalRows);
        result.put("processedRows", job.processedRows);

        if (job.totalRows > 0) {
            int percent = (int) ((job.processedRows * 100) / job.totalRows);
            result.put("progressPercent", Math.min(percent, 100));
        } else {
            result.put("progressPercent", 0);
        }

        if ("FAILED".equals(job.status)) {
            result.put("error", job.errorMessage);
        }

        if ("COMPLETED".equals(job.status)) {
            result.put("downloadUrl", "/exports/download/" + job.jobId);
            if (!job.isPreWarmed) {
                result.put("expiresAt", job.startedAt.plusHours(retentionHours).toString());
            }
        }

        return result;
    }

    @Override
    public void updateProgress(String jobId, long totalRows, long processedRows) {
        JobInfo job = jobs.get(jobId);
        if (job != null) {
            job.totalRows     = totalRows;
            job.processedRows = processedRows;
        }
    }

    @Override
    public String getFilePath(String jobId) {
        JobInfo job = jobs.get(jobId);
        if (job == null || !"COMPLETED".equals(job.status)) return null;
        return job.filePath;
    }

    @Override
    public String getFileName(String jobId) {
        JobInfo job = jobs.get(jobId);
        return job != null ? job.fileName : null;
    }

    @Override
    public String getContentType(String jobId) {
        JobInfo job = jobs.get(jobId);
        if (job == null) return null;
        return job.format == ExportFormat.EXCEL
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv;charset=UTF-8";
    }

    // ── Cleanup — only on-demand jobs ─────────────────────────────────────────

    @Override
    @Scheduled(fixedRate = 3600000) // every hour
    public void cleanupOldExports() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);
        jobs.entrySet().removeIf(entry -> {
            JobInfo job = entry.getValue();
            if (job.isPreWarmed) return false;

            if (job.startedAt != null && job.startedAt.isBefore(cutoff)) {
                if (job.filePath != null) {
                    File file = new File(job.filePath);
                    if (file.exists()) {
                        file.delete();
                        log.info("Cleaned up on-demand export: {}", job.filePath);
                    }
                }
                return true;
            }
            return false;
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isUnfiltered(DynamicFilterRequest filter) {
        if (filter == null) return true;
        boolean noSearch = (filter.getColumnName() == null || filter.getColumnName().isBlank())
                        && (filter.getSearchQuery() == null || filter.getSearchQuery().isBlank());
        Map<String, String> filterBy = filter.getFilterBy();
        boolean noFilters = filterBy == null || filterBy.values().stream()
                .allMatch(v -> v == null || v.isBlank());
        boolean noDates = filter.getDateFrom() == null && filter.getDateTo() == null;
        return noSearch && noFilters && noDates;
    }

    // ── JobInfo ───────────────────────────────────────────────────────────────

    private static class JobInfo {
        String jobId;
        String exportType;
        ExportFormat format;
        String status;
        String filePath;
        String fileName;
        String errorMessage;
        LocalDateTime startedAt;
        LocalDateTime completedAt;
        boolean isPreWarmed = false;
        volatile long totalRows;
        volatile long processedRows;
    }
}