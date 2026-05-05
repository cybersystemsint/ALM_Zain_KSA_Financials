package com.zain.ksa.alm.financials.controller;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.ApiResponse;
import com.zain.ksa.alm.financials.dto.response.AssetDepreciationDetailDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.scheduler.DataRefreshScheduler;
import com.zain.ksa.alm.financials.scheduler.DepreciationScheduler;
import com.zain.ksa.alm.financials.service.DepreciationHistoryService;
import com.zain.ksa.alm.financials.service.ExportJobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * REST Controller for DepreciationHistory endpoints.
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>POST /depreciation-history/listv1         → Paginated list (no summary cards)</li>
 *   <li>POST /depreciation-history/list → Paginated list WITH aggregate summary cards</li>
 *   <li>POST /depreciation-history/export       → Async export (CSV/Excel)</li>
 *   <li>POST /depreciation-history/run-depreciation        → Manual scheduler trigger</li>
 *   <li>GET  /depreciation-history/run-depreciation/status → Job status</li>
 * </ul>
 */
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("depreciation-history")
public class DepreciationHistoryController {

    private static final Logger log = LoggerFactory.getLogger(DepreciationHistoryController.class);

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
        this.service               = service;
        this.depreciationScheduler = depreciationScheduler;
        this.dataRefreshScheduler  = dataRefreshScheduler;
        this.exportJobService      = exportJobService;
        this.schedulerExecutor     = schedulerExecutor;
    }

    // ── Depreciation list (no summary) ────────────────────────────────────────

    /**
     * Fetch paginated depreciation history with optional filters.
     * Does NOT include aggregate summary cards — use /list-summary for that.
     *
     * @param filter   DynamicFilterRequest (optional)
     * @param pageable pagination parameters (default: page 0, size 100, sort recordNo DESC)
     * @return ResponseEntity with paged composite DTOs
     */
    @PostMapping("/listv1")
    public ResponseEntity<ApiResponse<PagedResponse<AssetDepreciationDetailDTO>>> getAll(
            @RequestBody(required = false) DynamicFilterRequest filter,
            @PageableDefault(size = 100, sort = "recordNo", direction = Sort.Direction.DESC)
            Pageable pageable) {

        if (filter == null) {
            filter = new DynamicFilterRequest();
        }

        log.info("[API] POST /depreciation-history/listv1  page={} size={}  filter={}",
                 pageable.getPageNumber(), pageable.getPageSize(), filter);

        PagedResponse<AssetDepreciationDetailDTO> response = service.findAll(filter, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok(
                    String.format("Found %d records on page %d of %d",
                            response.getContent().size(),
                            response.getPageNumber() + 1,
                            response.getTotalPages()),
                    response
                )
        );
    }

    // ── Depreciation list WITH summary cards ──────────────────────────────────

    /**
     * Fetch paginated depreciation history WITH aggregate summary totals.
     *
     * <p>Mirrors the FAR report /list endpoint. Returns both paginated
     * data and summary cards for the frontend dashboard.</p>
     *
     * <p><b>Summary fields returned:</b></p>
     * <ul>
     *   <li>totalMonthlyDepreciation    — SUM(monthlyDepreciationAmt)    across ALL rows</li>
     *   <li>totalAccumulatedDepr        — SUM(accumulatedDepreciationAmt) across ALL rows</li>
     *   <li>totalNetCost                — SUM(netCost)                   across ALL rows</li>
     *   <li>filteredMonthlyDepreciation — SUM(monthlyDepreciationAmt)    for active filters only</li>
     *   <li>filteredAccumulatedDepr     — SUM(accumulatedDepreciationAmt) for active filters only</li>
     *   <li>filteredNetCost             — SUM(netCost)                   for active filters only</li>
     * </ul>
     *
     * <p>When no filters are active, filtered totals == grand totals and
     * only one DB query is fired (no second full-table scan).</p>
     *
     * @param filter   DynamicFilterRequest (optional — null treated as no filters)
     * @param pageable pagination parameters (default: page 0, size 100, sort recordNo DESC)
     * @return ResponseEntity wrapping the summary map
     */
    @PostMapping("/list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllWithSummary(
            @RequestBody(required = false) DynamicFilterRequest filter,
            @PageableDefault(size = 100, sort = "recordNo", direction = Sort.Direction.DESC)
            Pageable pageable) {

        if (filter == null) {
            filter = new DynamicFilterRequest();
        }

        log.info("[API] POST /depreciation-history/list-summary  page={} size={}  filter={}",
                 pageable.getPageNumber(), pageable.getPageSize(), filter);

        Map<String, Object> response = service.findAllWithSummary(filter, pageable);

        long totalRecords = response.get("totalRecords") != null
                ? ((Number) response.get("totalRecords")).longValue()
                : 0L;
        int currentPage = pageable.getPageNumber() + 1;
        int totalPages  = response.get("totalPages") != null
                ? ((Number) response.get("totalPages")).intValue()
                : 0;

        return ResponseEntity.ok(
                ApiResponse.ok(
                    String.format("Found %d records on page %d of %d",
                            totalRecords, currentPage, totalPages),
                    response
                )
        );
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Asynchronously export depreciation history to CSV or Excel.
     *
     * <p><b>Strategy:</b></p>
     * <ul>
     *   <li><b>No filters:</b> Returns pre-warmed export job ID (instant download)</li>
     *   <li><b>With filters:</b> Starts fresh on-demand export, retained for configured hours</li>
     * </ul>
     *
     * @param filter DynamicFilterRequest (optional)
     * @param format ExportFormat (EXCEL or CSV)
     * @return ResponseEntity with jobId and polling/download URLs
     */
    @PostMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, String>>> export(
            @RequestBody(required = false) DynamicFilterRequest filter,
            @RequestParam(defaultValue = "EXCEL") ExportFormat format) {

        if (filter == null) {
            filter = new DynamicFilterRequest();
        }

        log.info("[API] POST /depreciation-history/export  format={}  filter={}",
                 format, filter);

        String jobId = exportJobService.startExport("depreciation", filter, format);
        Map<String, Object> status = exportJobService.getStatus(jobId);
        String statusValue = status != null ? (String) status.get("status") : "RUNNING";

        Map<String, String> response = Map.of(
                "jobId",       jobId,
                "status",      statusValue,
                "pollUrl",     "/exports/status/" + jobId,
                "downloadUrl", "/exports/download/" + jobId
        );

        return ResponseEntity.accepted()
                .body(ApiResponse.ok(
                    "Export started. Use pollUrl to check status, downloadUrl to retrieve file.",
                    response
                ));
    }

    // ── Depreciation scheduler control ────────────────────────────────────────

    /**
     * Manually trigger depreciation processing on the scheduler executor pool.
     * Returns HTTP 202 Accepted if triggered, 409 Conflict if already running.
     */
    @PostMapping("/run-depreciation")
    public ResponseEntity<ApiResponse<String>> triggerDepreciation() {
        log.info("[API] POST /depreciation-history/run-depreciation");

        if (depreciationScheduler.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Depreciation run is already in progress"));
        }

        schedulerExecutor.execute(() -> {
            try {
                depreciationScheduler.processDepreciation(true);
            } catch (Exception ex) {
                log.error("[API] Depreciation run failed: {}", ex.getMessage(), ex);
            }
        });

        return ResponseEntity.accepted()
                .body(ApiResponse.ok("Depreciation run triggered — check server logs for progress"));
    }

    /**
     * Check if depreciation scheduler is currently running.
     *
     * @return ResponseEntity with status (RUNNING or IDLE)
     */
    @GetMapping("/run-depreciation/status")
    public ResponseEntity<ApiResponse<String>> getDepreciationStatus() {
        String status = depreciationScheduler.isRunning() ? "RUNNING" : "IDLE";
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    // ── Health check ──────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(
                ApiResponse.ok("Depreciation history service is healthy")
        );
    }
}