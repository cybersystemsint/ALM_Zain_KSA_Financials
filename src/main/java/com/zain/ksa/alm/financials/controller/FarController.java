package com.zain.ksa.alm.financials.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.ApiResponse;
import com.zain.ksa.alm.financials.service.ExportJobService;
import com.zain.ksa.alm.financials.service.FarReportService;

/**
 * FAR Report controller with filtering and async export support.
 */
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("far-report")
public class FarController {

    private static final Logger log = LoggerFactory.getLogger(FarController.class);

    private final FarReportService farReportService;
    private final ExportJobService exportJobService;

    public FarController(FarReportService farReportService, ExportJobService exportJobService) {
        this.farReportService = farReportService;
        this.exportJobService = exportJobService;
    }

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

    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestBody java.util.List<Map<String, Object>> data,
            @RequestParam(defaultValue = "Excel") String source) {

        if (data == null || data.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("No data provided"));
        }
        if (data.size() > 10_000) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Upload limited to 10,000 rows per request"));
        }

        Map<String, Object> result = farReportService.processUpload(data, source);

        // ── Build human-readable message matching the response format ──────────
        String status   = (String)  result.get("status");
        int    total    = ((Number) result.get("total")).intValue();
        int    failed   = ((Number) result.get("failed")).intValue();
        int    inserted = ((Number) result.get("inserted")).intValue();
        int    updated  = ((Number) result.get("updated")).intValue();

        String message;
        if ("FAILED".equals(status)) {
            // All rows rejected
            message = String.format(
                "Upload failed: all %d row(s) were rejected. Check 'rowErrors' for details.",
                failed
            );
        } else if ("PARTIAL".equals(status)) {
            // Some rows succeeded, some failed
            message = String.format(
                "Upload partially completed: %d inserted, %d updated, %d row(s) rejected. Check 'rowErrors' for details.",
                inserted, updated, failed
            );
        } else {
            // Full success
            if (updated > 0 && inserted > 0) {
                message = String.format(
                    "Upload successful: %d inserted, %d updated.", inserted, updated);
            } else if (updated > 0) {
                message = String.format("Upload successful: %d row(s) updated.", updated);
            } else {
                message = String.format("Upload successful: %d row(s) inserted.", inserted);
            }
        }

        // success=true on the outer envelope always — the HTTP call itself succeeded.
        // The upload status (SUCCESS / PARTIAL / FAILED) lives inside data.status.
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }

    @PostMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startExport(
            @RequestBody(required = false) DynamicFilterRequest filter,
            @RequestParam(defaultValue = "EXCEL") ExportFormat format) {

        if (filter == null) filter = new DynamicFilterRequest();
        log.info("[FarController] Export request, format={}", format);

        String jobId = exportJobService.startExport("far_report", filter, format);
        Map<String, Object> status = exportJobService.getStatus(jobId);
        String currentStatus = status != null ? (String) status.get("status") : "RUNNING";

        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
            "jobId",       jobId,
            "status",      currentStatus,
            "pollUrl",     "/exports/status/" + jobId,
            "downloadUrl", "/exports/download/" + jobId
        )));
    }
}