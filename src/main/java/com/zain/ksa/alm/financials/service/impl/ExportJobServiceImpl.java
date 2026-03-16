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
 * ── Changes from previous version ───────────────────────────────────────────
 *
 *  ① JobInfo fields made volatile where cross-thread visibility is required.
 *
 *    PROBLEM: JobInfo fields written by the export worker thread (status,
 *    filePath, errorMessage, completedAt) were read by HTTP polling threads
 *    without any memory visibility guarantee. The JVM is legally allowed to
 *    cache field values in CPU registers between writes and reads on different
 *    threads. This means a polling thread could see status="RUNNING" long after
 *    the worker set it to "COMPLETED", resulting in stuck progress bars or
 *    missing downloadUrl in the status response.
 *
 *    FIX: Fields that are written by one thread and read by another are now
 *    declared volatile. volatile guarantees that every read sees the most
 *    recently written value across all CPU cores, without the overhead of full
 *    synchronization.
 *
 *    Fields marked volatile:
 *      - status        : written by worker, polled by HTTP threads
 *      - filePath      : written on completion, read for download
 *      - errorMessage  : written on failure, read for error response
 *      - completedAt   : written on completion, read for status response
 *      - totalRows     : written by progressCallback, polled
 *      - processedRows : written by progressCallback, polled (was already volatile)
 *
 *    Fields NOT volatile (written and read only on construction / same thread):
 *      - jobId, exportType, format, fileName, startedAt, isPreWarmed
 *
 *  ② Everything else is unchanged — job creation, cleanup, pre-warm logic.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
public class ExportJobServiceImpl implements ExportJobService {

    private static final Logger log = LoggerFactory.getLogger(ExportJobServiceImpl.class);

    private final ExportExecutor exportExecutor;

    @Value("${app.export.dir:${java.io.tmpdir}/data/app}")
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

        if (isUnfiltered(filter)) {
            String  preWarmId = PreWarmExportJob.buildPreWarmJobId(exportType, format);
            JobInfo preWarmed = jobs.get(preWarmId);

            if (preWarmed != null && "COMPLETED".equals(preWarmed.status)) {
                boolean fileExists = preWarmed.filePath != null
                        && new File(preWarmed.filePath).exists();
                if (fileExists) {
                    log.info("Serving pre-warmed export for type={} format={}", exportType, format);
                    return preWarmId;
                }
                log.warn("Pre-warmed file missing on disk for jobId={}, regenerating", preWarmId);
                jobs.remove(preWarmId);
                return runExport(exportType, filter, format, preWarmId, true);
            }

            if (preWarmed != null && "RUNNING".equals(preWarmed.status)) {
                log.info("Pre-warm still running for type={}, user will poll", exportType);
                return preWarmId;
            }

            String preWarmFile = buildPreWarmFilePath(preWarmId, format);
            if (new File(preWarmFile).exists()) {
                log.info("Found pre-warmed file on disk for type={} format={}, restoring metadata",
                         exportType, format);
                restorePreWarmJobInfo(preWarmId, exportType, format, preWarmFile);
                return preWarmId;
            }

            log.warn("Pre-warmed export unavailable for type={}, regenerating", exportType);
            return runExport(exportType, filter, format, preWarmId, true);
        }

        return runExport(exportType, filter, format,
                UUID.randomUUID().toString().substring(0, 8), false);
    }

    // ── Scheduler / pre-warm export ───────────────────────────────────────────

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
        job.status      = "RUNNING";    // volatile — visible to polling threads immediately
        job.fileName    = filename;
        job.startedAt   = LocalDateTime.now();
        job.isPreWarmed = isPreWarmed;
        jobs.put(jobId, job);

        exportExecutor.execute(
                jobId, exportType, filter, format, filePath,
                (totalRows, processedRows) -> {
                    // Both volatile — polling threads see updates without synchronization
                    job.totalRows     = totalRows;
                    job.processedRows = processedRows;
                },
                result -> {
                    if (result.success) {
                        // Write volatile fields in a safe order:
                        // filePath before status, so if a thread reads status="COMPLETED"
                        // it is guaranteed to also see the correct filePath.
                        job.filePath    = result.filePath;   // volatile write
                        job.completedAt = LocalDateTime.now();
                        job.status      = "COMPLETED";       // volatile write — publish last
                        log.info("Export COMPLETED: jobId={}, preWarmed={}, rows={}",
                                jobId, isPreWarmed, job.totalRows);
                    } else {
                        job.errorMessage = result.error;     // volatile write
                        job.completedAt  = LocalDateTime.now();
                        job.status       = "FAILED";         // volatile write — publish last
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

        // Read volatile fields once into locals to ensure a consistent snapshot.
        // Without this, two reads of job.status could return different values if
        // the worker thread writes between them.
        String        status      = job.status;
        String        filePath    = job.filePath;
        String        errorMsg    = job.errorMessage;
        LocalDateTime completedAt = job.completedAt;
        long          totalRows   = job.totalRows;
        long          processed   = job.processedRows;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId",         job.jobId);
        result.put("exportType",    job.exportType);
        result.put("status",        status);
        result.put("preWarmed",     job.isPreWarmed);
        result.put("startedAt",     job.startedAt   != null ? job.startedAt.toString()   : null);
        result.put("completedAt",   completedAt     != null ? completedAt.toString()     : null);
        result.put("totalRows",     totalRows);
        result.put("processedRows", processed);

        if (totalRows > 0) {
            int percent = (int) ((processed * 100) / totalRows);
            result.put("progressPercent", Math.min(percent, 100));
        } else {
            result.put("progressPercent", 0);
        }

        if ("FAILED".equals(status)) {
            result.put("error", errorMsg);
        }

        if ("COMPLETED".equals(status)) {
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
        // Read status and filePath atomically from volatile fields
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

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    @Scheduled(fixedRate = 3600000)
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

        cleanupOrphanedFiles(cutoff);
    }

    private void cleanupOrphanedFiles(LocalDateTime cutoff) {
        File dir = new File(exportDir);
        if (!dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) ->
            (name.endsWith(".xlsx") || name.endsWith(".csv"))
            && !name.contains("prewarm")
        );
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            String jobId    = fileName.substring(0, fileName.lastIndexOf('.'));

            if (!jobs.containsKey(jobId)) {
                long          lastModified = file.lastModified();
                LocalDateTime fileTime     = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(lastModified),
                    java.time.ZoneId.systemDefault()
                );
                if (fileTime.isBefore(cutoff)) {
                    if (file.delete()) {
                        log.info("Cleaned up orphaned export file: {}", file.getAbsolutePath());
                    } else {
                        log.warn("Failed to delete orphaned file: {}", file.getAbsolutePath());
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildPreWarmFilePath(String preWarmId, ExportFormat format) {
        String extension = format == ExportFormat.EXCEL ? ".xlsx" : ".csv";
        return exportDir + File.separator + preWarmId + extension;
    }

    private void restorePreWarmJobInfo(String preWarmId, String exportType,
                                        ExportFormat format, String filePath) {
        JobInfo job = new JobInfo();
        job.jobId         = preWarmId;
        job.exportType    = exportType;
        job.format        = format;
        job.fileName      = new File(filePath).getName();
        job.isPreWarmed   = true;
        job.startedAt     = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(new File(filePath).lastModified()),
            java.time.ZoneId.systemDefault()
        );
        // Write data fields before publishing status — guarantees visibility order
        job.filePath      = filePath;
        job.completedAt   = job.startedAt;
        job.totalRows     = -1;
        job.processedRows = -1;
        job.status        = "COMPLETED";   // volatile publish last

        jobs.put(preWarmId, job);
        log.info("Restored pre-warmed job from disk: jobId={}, filePath={}", preWarmId, filePath);
    }

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
        // ── Written once at construction, never changed — no volatile needed ──
        String        jobId;
        String        exportType;
        ExportFormat  format;
        String        fileName;
        LocalDateTime startedAt;
        boolean       isPreWarmed = false;

        // ── Written by worker thread, read by HTTP polling threads ────────────
        // volatile guarantees cross-thread visibility without synchronization.
        volatile String        status;
        volatile String        filePath;
        volatile String        errorMessage;
        volatile LocalDateTime completedAt;
        volatile long          totalRows;
        volatile long          processedRows;
    }
}