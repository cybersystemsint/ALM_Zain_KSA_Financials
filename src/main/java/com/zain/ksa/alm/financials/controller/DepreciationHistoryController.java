package com.zain.ksa.alm.financials.controller;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.ApiResponse;
import com.zain.ksa.alm.financials.dto.response.DepreciationHistoryDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.scheduler.DataRefreshScheduler;
import com.zain.ksa.alm.financials.scheduler.DepreciationScheduler;
import com.zain.ksa.alm.financials.service.DepreciationHistoryService;
import com.zain.ksa.alm.financials.service.ExportJobService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("depreciation-history")
public class DepreciationHistoryController {

    private final DepreciationHistoryService service;
    private final DepreciationScheduler      depreciationScheduler;
    private final DataRefreshScheduler       dataRefreshScheduler;
    private final ExportJobService           exportJobService;
    private final Executor                   schedulerExecutor;

    public DepreciationHistoryController(
            DepreciationHistoryService service,
            DepreciationScheduler depreciationScheduler,
            DataRefreshScheduler dataRefreshScheduler,
            ExportJobService exportJobService,
            @Qualifier("schedulerExecutor") Executor schedulerExecutor) {
        this.service              = service;
        this.depreciationScheduler = depreciationScheduler;
        this.dataRefreshScheduler  = dataRefreshScheduler;
        this.exportJobService      = exportJobService;
        this.schedulerExecutor     = schedulerExecutor;
    }

    // ── Depreciation list & detail ────────────────────────────────────────────

@PostMapping("/list")
public ResponseEntity<ApiResponse<PagedResponse<DepreciationHistoryDTO>>> getAll(
        @RequestBody(required = false) DynamicFilterRequest filter,
        @PageableDefault(size = 100, sort = "recordNo", direction = Sort.Direction.DESC)
        Pageable pageable) {
    if (filter == null) filter = new DynamicFilterRequest();
    return ResponseEntity.ok(ApiResponse.ok(service.findAll(filter, pageable)));
}


    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Export endpoint.
     *
     * No filters  → returns pre-warmed job ID; file is already on disk (instant download).
     *               If pre-warm is still running, returns same ID so frontend can poll.
     * With filters → runs a fresh on-demand export, retained for configured hours.
     *
     * Poll:     GET /exports/status/{jobId}
     * Download: GET /exports/download/{jobId}
     */
    @PostMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, String>>> export(
            DynamicFilterRequest filter,
            @RequestParam(defaultValue = "CSV") ExportFormat format) {

        if (filter == null) filter = new DynamicFilterRequest();
        String jobId = exportJobService.startExport("depreciation", filter, format);

        Map<String, Object> status      = exportJobService.getStatus(jobId);
        String              statusValue = status != null ? (String) status.get("status") : "RUNNING";

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "jobId",       jobId,
                "status",      statusValue,
                "pollUrl",     "/exports/status/" + jobId,
                "downloadUrl", "/exports/download/" + jobId
        )));
    }

    // ── Depreciation run trigger ──────────────────────────────────────────────

    /**
     * Manually triggers depreciation processing on the schedulerExecutor pool.
     * The scheduler's own finally-block calls refreshPreWarmExport() on success,
     * so no extra thenRun() is needed here.
     */
    @PostMapping("/run-depreciation")
    public ResponseEntity<ApiResponse<String>> triggerDepreciation() {
        if (depreciationScheduler.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Depreciation run is already in progress"));
        }

        schedulerExecutor.execute(() -> depreciationScheduler.processDepreciation(true));

        return ResponseEntity.accepted()
                .body(ApiResponse.ok("Depreciation run triggered — check server logs for progress"));
    }

    @GetMapping("/run-depreciation/status")
    public ResponseEntity<ApiResponse<String>> getDepreciationStatus() {
        String status = depreciationScheduler.isRunning() ? "RUNNING" : "IDLE";
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}