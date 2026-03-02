package com.zain.ksa.alm.financials.controller;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.*;
import com.zain.ksa.alm.financials.service.ExportJobService;
import com.zain.ksa.alm.financials.service.UnmappedInventoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("unmapped-inventory")
public class UnmappedInventoryController {

    private static final Logger log = LoggerFactory.getLogger(UnmappedInventoryController.class);

    private final UnmappedInventoryService service;
    private final ExportJobService exportJobService;

    public UnmappedInventoryController(UnmappedInventoryService service,
                                        ExportJobService exportJobService) {
        this.service = service;
        this.exportJobService = exportJobService;
    }

    // ── Active ────────────────────────────────────────────────────────────────

@PostMapping("/active")
public ResponseEntity<ApiResponse<PagedResponse<UnmappedActiveInventoryDTO>>> getActive(
        @RequestBody(required = false) DynamicFilterRequest filter,
        @PageableDefault(size = 100) Pageable pageable) {
    if (filter == null) filter = new DynamicFilterRequest();
    return ResponseEntity.ok(ApiResponse.ok(service.findAllActive(filter, pageable)));
}
    /**
     * No filters  → pre-warmed file served instantly (status = COMPLETED).
     * With filters → on-demand export, retained for configured hours.
     */
    @PostMapping("/active/export")
    public ResponseEntity<ApiResponse<Map<String, String>>> exportActive(
            DynamicFilterRequest filter,
            @RequestParam(defaultValue = "CSV") ExportFormat format) {

        if (filter == null) filter = new DynamicFilterRequest();
        String jobId = exportJobService.startExport("unmapped_active", filter, format);

        Map<String, Object> status = exportJobService.getStatus(jobId);
        String currentStatus = status != null ? (String) status.get("status") : "RUNNING";

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "jobId",       jobId,
                "status",      currentStatus,
                "pollUrl",     "/exports/status/" + jobId,
                "downloadUrl", "/exports/download/" + jobId
        )));
    }

    // ── Passive ───────────────────────────────────────────────────────────────

@PostMapping("/passive")
public ResponseEntity<ApiResponse<PagedResponse<UnmappedPassiveInventoryDTO>>> getPassive(
        @RequestBody(required = false) DynamicFilterRequest filter,
        @PageableDefault(size = 100) Pageable pageable) {
    if (filter == null) filter = new DynamicFilterRequest();
    return ResponseEntity.ok(ApiResponse.ok(service.findAllPassive(filter, pageable)));
}

    @PostMapping("/passive/export")
    public ResponseEntity<ApiResponse<Map<String, String>>> exportPassive(
            DynamicFilterRequest filter,
            @RequestParam(defaultValue = "CSV") ExportFormat format) {

        if (filter == null) filter = new DynamicFilterRequest();
        String jobId = exportJobService.startExport("unmapped_passive", filter, format);

        Map<String, Object> status = exportJobService.getStatus(jobId);
        String currentStatus = status != null ? (String) status.get("status") : "RUNNING";

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "jobId",       jobId,
                "status",      currentStatus,
                "pollUrl",     "/exports/status/" + jobId,
                "downloadUrl", "/exports/download/" + jobId
        )));
    }

    // ── IT ────────────────────────────────────────────────────────────────────
@PostMapping("/it")
public ResponseEntity<ApiResponse<PagedResponse<UnmappedITInventoryDTO>>> getIT(
        @RequestBody(required = false) DynamicFilterRequest filter,
        @PageableDefault(size = 100) Pageable pageable) {
    if (filter == null) filter = new DynamicFilterRequest();
    return ResponseEntity.ok(ApiResponse.ok(service.findAllIT(filter, pageable)));
}

    @PostMapping("/it/export")
    public ResponseEntity<ApiResponse<Map<String, String>>> exportIT(
            DynamicFilterRequest filter,
            @RequestParam(defaultValue = "CSV") ExportFormat format) {

        if (filter == null) filter = new DynamicFilterRequest();
        String jobId = exportJobService.startExport("unmapped_it", filter, format);

        Map<String, Object> status = exportJobService.getStatus(jobId);
        String currentStatus = status != null ? (String) status.get("status") : "RUNNING";

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "jobId",       jobId,
                "status",      currentStatus,
                "pollUrl",     "/exports/status/" + jobId,
                "downloadUrl", "/exports/download/" + jobId
        )));
    }

    // ── Reconciliation triggers ───────────────────────────────────────────────

    @PostMapping("/reconcile/active")
    public ResponseEntity<ApiResponse<String>> triggerActive() {
        if (service.isActiveRunning()) {
            return ResponseEntity.status(409)
                    .body(ApiResponse.error("Active reconciliation is already running"));
        }
        service.triggerActiveReconciliationAsync();
        return ResponseEntity.ok(ApiResponse.ok("Active reconciliation triggered"));
    }

    @PostMapping("/reconcile/passive")
    public ResponseEntity<ApiResponse<String>> triggerPassive() {
        if (service.isPassiveRunning()) {
            return ResponseEntity.status(409)
                    .body(ApiResponse.error("Passive reconciliation is already running"));
        }
        service.triggerPassiveReconciliationAsync();
        return ResponseEntity.ok(ApiResponse.ok("Passive reconciliation triggered"));
    }

    @PostMapping("/reconcile/it")
    public ResponseEntity<ApiResponse<String>> triggerIT() {
        if (service.isItRunning()) {
            return ResponseEntity.status(409)
                    .body(ApiResponse.error("IT reconciliation is already running"));
        }
        service.triggerITReconciliationAsync();
        return ResponseEntity.ok(ApiResponse.ok("IT reconciliation triggered"));
    }

    @PostMapping("/reconcile/all")
    public ResponseEntity<ApiResponse<String>> triggerAll() {
        if (service.isAnyRunning()) {
            return ResponseEntity.status(409)
                    .body(ApiResponse.error("A reconciliation is already running"));
        }
        service.triggerFullReconciliationAsync();
        return ResponseEntity.ok(ApiResponse.ok("Full reconciliation triggered"));
    }

    @GetMapping("/reconcile/status")
    public ResponseEntity<ApiResponse<Object>> getReconciliationStatus() {
        var status = Map.of(
                "active",  service.isActiveRunning()  ? "RUNNING" : "IDLE",
                "passive", service.isPassiveRunning() ? "RUNNING" : "IDLE",
                "it",      service.isItRunning()      ? "RUNNING" : "IDLE"
        );
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}