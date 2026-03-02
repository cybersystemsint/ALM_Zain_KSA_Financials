package com.zain.ksa.alm.financials.service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.*;

import javax.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Pageable;

public interface UnmappedInventoryService {

    // ── Fetch (paginated + dynamic filter) ────────────────────────────────────
    PagedResponse<UnmappedActiveInventoryDTO>  findAllActive( DynamicFilterRequest filter, Pageable pageable);
    PagedResponse<UnmappedPassiveInventoryDTO> findAllPassive(DynamicFilterRequest filter, Pageable pageable);
    PagedResponse<UnmappedITInventoryDTO>      findAllIT(     DynamicFilterRequest filter, Pageable pageable);

    // ── Export (sync — writes to HTTP response) ───────────────────────────────
    void exportActiveToResponse( DynamicFilterRequest filter, ExportFormat format, HttpServletResponse response) throws Exception;
    void exportPassiveToResponse(DynamicFilterRequest filter, ExportFormat format, HttpServletResponse response) throws Exception;
    void exportITToResponse(     DynamicFilterRequest filter, ExportFormat format, HttpServletResponse response) throws Exception;

    // ── Async manual scheduler triggers ───────────────────────────────────────
    void triggerActiveReconciliationAsync();
    void triggerPassiveReconciliationAsync();
    void triggerITReconciliationAsync();
    void triggerFullReconciliationAsync();

    // ── Status checks ─────────────────────────────────────────────────────────
    boolean isActiveRunning();
    boolean isPassiveRunning();
    boolean isItRunning();
    boolean isAnyRunning();
}