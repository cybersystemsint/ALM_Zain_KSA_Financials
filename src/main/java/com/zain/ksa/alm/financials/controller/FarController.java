package com.zain.ksa.alm.financials.controller;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.FarReportDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.service.ExportJobService;
import com.zain.ksa.alm.financials.service.FarReportService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unified controller for all FAR report operations.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/far/list}       — paginated, multi-column filtered query</li>
 *   <li>{@code POST /api/far/upload}      — bulk upload (Excel or CSV payload)</li>
 *   <li>{@code POST /api/far/export}      — start async export job (returns jobId)</li>
 *   <li>{@code GET  /api/far/export/{id}/status}   — poll export progress</li>
 *   <li>{@code GET  /api/far/export/{id}/download}  — download completed file</li>
 * </ul>
 *
 * <h3>Export flow</h3>
 * <ol>
 *   <li>Frontend calls {@code POST /export} → gets jobId</li>
 *   <li>Frontend polls {@code GET /export/{id}/status} → gets progress %</li>
 *   <li>When status is COMPLETED → frontend calls {@code GET /export/{id}/download}</li>
 * </ol>
 *
 * <p>If a pre-warmed export exists (no filters), the export endpoint returns the
 * pre-warmed jobId immediately — no waiting.</p>
 */
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("/far-report")
public class FarController {

    private static final Logger log = LoggerFactory.getLogger(FarController.class);

    private final FarReportService farReportService;
    private final ExportJobService exportJobService;

    public FarController(FarReportService farReportService,
                         ExportJobService exportJobService) {
        this.farReportService = farReportService;
        this.exportJobService = exportJobService;
    }

    // ── 1. Paginated List with Multi-Column Filtering ─────────────────────────

    /**
     * Fetches paginated FAR records with optional multi-column filtering.
     *
     * <p>Request body example:</p>
     * <pre>{@code
     * {
     *   "columnName": "assetId",
     *   "searchQuery": "A12345",
     *   "filterBy": {
     *     "category": "IT Equipment",
     *     "statusFlag": "Active"
     *   },
     *   "dateFrom": "2024-01-01T00:00:00",
     *   "dateTo": "2024-12-31T23:59:59",
     *   "page": 0,
     *   "size": 50
     * }
     * }</pre>
     */
@PostMapping("/list")
public ResponseEntity<Map<String, Object>> list(
        @RequestBody(required = false) DynamicFilterRequest filter,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size) {

    if (filter == null) filter = new DynamicFilterRequest();
    size = Math.min(Math.max(size, 1), 500);
    page = Math.max(page, 0);

    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(farReportService.findAllWithSummary(filter, pageable));
}



    // ── 2. Bulk Upload ────────────────────────────────────────────────────────

    /**
     * Uploads FAR records in bulk. Accepts a JSON array of row objects.
     * Upserts by assetId — existing records are updated, new ones inserted.
     *
     * <p>After upload completes, the pre-warmed FAR export is refreshed
     * in the background so the next download is instant.</p>
     *
     * @param data   list of maps where keys are FAR column names
     * @param source label for logging: "Excel" or "CSV"
     * @return summary with inserted/updated/failed counts
     */
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestBody List<Map<String, Object>> data,
            @RequestParam(defaultValue = "Excel") String source) {

        log.info("[FarController] Upload request received, rows={}, source={}", data.size(), source);

        if (data.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", "No data provided"));
        }

        Map<String, Object> result = farReportService.processUpload(data, source);
        return ResponseEntity.ok(result);
    }

    // ── 3. Start Export Job ───────────────────────────────────────────────────

    /**
     * Starts an async FAR export job. Returns a jobId for status polling.
     *
     * <p>If no filters are provided, checks for a pre-warmed export first.
     * If a pre-warmed file exists and is ready, returns its jobId immediately
     * (instant download).</p>
     *
     * @param format CSV or EXCEL (default EXCEL)
     * @return jobId and initial status
     */
    @PostMapping("/export")
    public ResponseEntity<Map<String, Object>> startExport(
            @RequestBody(required = false) DynamicFilterRequest filter,
            @RequestParam(defaultValue = "EXCEL") ExportFormat format) {

        if (filter == null) {
            filter = new DynamicFilterRequest(); // no filters = full dataset
        }

        log.info("[FarController] Export request, format={}", format);

        String jobId = exportJobService.startExport("far_report", filter, format);

        return ResponseEntity.accepted()
                .body(Map.of(
                    "jobId", jobId,
                    "status", "ACCEPTED",
                    "message", "Export job started. Poll /api/far/export/" + jobId + "/status for progress."
                ));
    }

    // ── 4. Poll Export Status ─────────────────────────────────────────────────

    /**
     * Returns current status of an export job.
     *
     * <p>Response fields: status (PENDING, IN_PROGRESS, COMPLETED, FAILED),
     * totalRows, processedRows, percentComplete.</p>
     */
    @GetMapping("/export/status/{jobId}")
    public ResponseEntity<Map<String, Object>> exportStatus(@PathVariable String jobId) {
        Map<String, Object> status = exportJobService.getStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    // ── 5. Download Completed Export ──────────────────────────────────────────

    /**
     * Streams the completed export file to the client.
     * Returns 404 if the job doesn't exist, 409 if not yet complete.
     */
    @GetMapping("/export/download/{jobId}")
    public void downloadExport(@PathVariable String jobId, HttpServletResponse response)
            throws Exception {

        String filePath = exportJobService.getFilePath(jobId);
        if (filePath == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Export job not found: " + jobId);
            return;
        }

        Map<String, Object> status = exportJobService.getStatus(jobId);
        if (status == null || !"COMPLETED".equals(status.get("status"))) {
            response.sendError(HttpServletResponse.SC_CONFLICT,
                    "Export not yet complete. Current status: " + (status != null ? status.get("status") : "UNKNOWN"));
            return;
        }

        String contentType = exportJobService.getContentType(jobId);
        String fileName    = exportJobService.getFileName(jobId);

        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"");

        try (InputStream in = new BufferedInputStream(new FileInputStream(filePath), 32 * 1024)) {
            StreamUtils.copy(in, response.getOutputStream());
            response.flushBuffer();
        }

        log.info("[FarController] Download complete for jobId={}", jobId);
    }
}