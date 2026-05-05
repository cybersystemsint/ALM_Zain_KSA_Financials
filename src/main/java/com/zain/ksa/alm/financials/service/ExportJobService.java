package com.zain.ksa.alm.financials.service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;

import java.util.Map;

public interface ExportJobService {

    /**
     * User-facing export trigger.
     * If no filters → checks for a pre-warmed job first (instant download).
     * If filtered → runs on-demand, retained for configured hours.
     */
    String startExport(String exportType, DynamicFilterRequest filter, ExportFormat format);

    /**
     * Called by schedulers/pre-warm jobs only.
     * Always runs fresh regardless of existing jobs.
     */
    void startPreWarmedExport(String exportType, DynamicFilterRequest filter,
                               ExportFormat format, String fixedJobId);

    /**
     * Deletes the job record and its file on disk.
     * Called by scheduler before re-running pre-warm.
     */
    void invalidateExport(String jobId);

    Map<String, Object> getStatus(String jobId);

    String getFilePath(String jobId);

    String getFileName(String jobId);

    String getContentType(String jobId);

    void updateProgress(String jobId, long totalRows, long processedRows);

    void cleanupOldExports();
}