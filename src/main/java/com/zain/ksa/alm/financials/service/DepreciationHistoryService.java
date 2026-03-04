package com.zain.ksa.alm.financials.service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.AssetDepreciationDetailDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;

import org.springframework.data.domain.Pageable;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

/**
 * Service for DepreciationHistory with normalized schema (v2).
 *
 * <p><b>API Contract:</b></p>
 * <ul>
 *   <li>Reads return AssetDepreciationDetailDTO (composite: depreciation + asset context)</li>
 *   <li>Filters applied to DepreciationHistory, results JOINed with FarReport</li>
 *   <li>Exports stream depreciation metrics with asset details for context</li>
 * </ul>
 *
 * <p><b>Scheduler Contract:</b></p>
 * <ul>
 *   <li>Stores only depreciation metrics in DepreciationHistory table</li>
 *   <li>Bulk upsert support via findByAssetIdsAndPeriod + saveAll</li>
 *   <li>FarReport updated separately for backward compatibility</li>
 * </ul>
 */
public interface DepreciationHistoryService {

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Fetch paginated depreciation history with full asset context.
     *
     * <p><b>Response includes:</b></p>
     * <ul>
     *   <li>Depreciation metrics: monthly, accumulated, netCost, period</li>
     *   <li>Asset master data: description, category, cost, life, etc.</li>
     *   <li>Audit trail: createdBy, changedBy, timestamps</li>
     * </ul>
     *
     * @param filter DynamicFilterRequest (filters on depreciation metrics)
     * @param pageable pagination (pageNumber, pageSize)
     * @return PagedResponse of composite DTOs
     */
    PagedResponse<AssetDepreciationDetailDTO> findAll(DynamicFilterRequest filter, Pageable pageable);

    /**
     * Fetch single depreciation record by DepreciationHistory ID with asset context.
     *
     * @param id DepreciationHistory.recordNo
     * @return composite DTO with asset details
     * @throws com.zain.ksa.alm.financials.exception.ResourceNotFoundException if not found
     */
    AssetDepreciationDetailDTO findById(Long id);

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Stream depreciation records with asset context for large exports (CSV/Excel).
     *
     * <p><b>Streaming strategy:</b></p>
     * <ul>
     *   <li>Server-side cursor for memory efficiency</li>
     *   <li>Batch JOIN with FarReport every N records</li>
     *   <li>Composite DTOs written incrementally to response stream</li>
     * </ul>
     *
     * @param filter DynamicFilterRequest (optional)
     * @param format ExportFormat (EXCEL, CSV, etc.)
     * @param response HttpServletResponse for streaming output
     * @throws Exception if streaming or format conversion fails
     */
    void exportToResponse(DynamicFilterRequest filter, ExportFormat format,
                          HttpServletResponse response) throws Exception;

    // ── Scheduler support ─────────────────────────────────────────────────────

    /**
     * Single-asset lookup by assetId and period.
     *
     * <p><b>Use case:</b> One-off corrections or diagnostics outside batch context.</p>
     *
     * @param assetId asset identifier
     * @param depreciationPeriod period in "YYYY-MM" format
     * @return optional DepreciationHistory record (lean, no JOINs)
     */
    Optional<DepreciationHistory> findByAssetAndPeriod(String assetId, String depreciationPeriod);

    /**
     * Bulk-fetch existing DepreciationHistory records for efficient upsert.
     *
     * <p><b>Scheduler use case:</b> Check which records already exist in a given period
     * before inserting or updating in batch. Replaces N individual queries with one
     * IN-clause query.</p>
     *
     * @param assetIds list of asset IDs to check
     * @param period depreciation period in "YYYY-MM" format
     * @return list of existing DepreciationHistory records (lean)
     */
    List<DepreciationHistory> findByAssetIdsAndPeriod(List<String> assetIds, String period);

    /**
     * Single save for one-off corrections outside batch contexts.
     *
     * @param entity DepreciationHistory record to save
     * @return saved entity
     */
    DepreciationHistory save(DepreciationHistory entity);

    /**
     * Batch save for scheduler bulk inserts/updates.
     *
     * <p><b>Performance:</b> Flushes entire chunk in one JDBC round-trip
     * instead of N individual INSERT/UPDATE calls. Batch size typically 500.</p>
     *
     * @param entities list of DepreciationHistory records
     * @return list of saved entities
     */
    List<DepreciationHistory> saveAll(List<DepreciationHistory> entities);
}