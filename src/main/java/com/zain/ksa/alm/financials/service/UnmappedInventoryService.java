package com.zain.ksa.alm.financials.service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.response.*;
import org.springframework.data.domain.Pageable;

public interface UnmappedInventoryService {

    // ── Fetch (paginated + dynamic filter) ────────────────────────────────────
    PagedResponse<UnmappedActiveInventoryDTO>  findAllActive( DynamicFilterRequest filter, Pageable pageable);
    PagedResponse<UnmappedPassiveInventoryDTO> findAllPassive(DynamicFilterRequest filter, Pageable pageable);
    PagedResponse<UnmappedITInventoryDTO>      findAllIT(     DynamicFilterRequest filter, Pageable pageable);

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