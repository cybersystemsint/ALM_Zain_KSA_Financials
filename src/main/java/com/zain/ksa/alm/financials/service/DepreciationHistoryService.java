package com.zain.ksa.alm.financials.service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.DepreciationHistoryDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;

import org.springframework.data.domain.Pageable;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

public interface DepreciationHistoryService {

    // ── Read ──────────────────────────────────────────────────────────────────

    PagedResponse<DepreciationHistoryDTO> findAll(DynamicFilterRequest filter, Pageable pageable);

    DepreciationHistoryDTO findById(Long id);

    // ── Export ────────────────────────────────────────────────────────────────

    void exportToResponse(DynamicFilterRequest filter, ExportFormat format,
                          HttpServletResponse response) throws Exception;

    // ── Scheduler support ─────────────────────────────────────────────────────

    /** Single-asset lookup — kept for one-off corrections or diagnostics. */
    Optional<DepreciationHistory> findByAssetAndPeriod(String assetId, String depreciationPeriod);

    /**
     * Bulk-fetch existing DepreciationHistory records for a list of asset IDs
     * in a given period. Replaces N individual queries with 1 IN-clause query.
     * Used by the depreciation scheduler for efficient upsert batching.
     */
    List<DepreciationHistory> findByAssetIdsAndPeriod(List<String> assetIds, String period);

    /** Single save — used outside batch contexts (e.g. one-off corrections). */
    DepreciationHistory save(DepreciationHistory entity);

    /**
     * Batch save — used by DepreciationScheduler to flush an entire chunk
     * in one JDBC round-trip instead of N individual INSERT/UPDATE calls.
     */
    List<DepreciationHistory> saveAll(List<DepreciationHistory> entities);
}