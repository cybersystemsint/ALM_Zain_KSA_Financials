package com.zain.ksa.alm.financials.repository;

import com.zain.ksa.alm.financials.entity.DepreciationHistory;
import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.repository.custom.FilteredStreamRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_CACHEABLE;
import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;


/**
 * Repository for DepreciationHistory entity.
 *
 * <p><b>Normalization Support (v2):</b></p>
 * <ul>
 *   <li>Scheduler methods: findByAssetIdsAndPeriod, findByAssetIdAndDepreciationPeriod</li>
 *   <li>JOIN methods: findFarReportByAssetId, findFarReportsByAssetIds</li>
 *   <li>Stream support: streamAll(spec), streamAll() for large exports</li>
 * </ul>
 */
@Repository
public interface DepreciationHistoryRepository
        extends JpaRepository<DepreciationHistory, Long>,
                JpaSpecificationExecutor<DepreciationHistory>,
                FilteredStreamRepository<DepreciationHistory> {

    // ─── Scheduler upsert support ────────────────────────────────────────────

    /**
     * Single-asset lookup by assetId and period.
     *
     * @param assetId asset identifier
     * @param depreciationPeriod period in "YYYY-MM" format
     * @return optional DepreciationHistory record
     */
    Optional<DepreciationHistory> findByAssetIdAndDepreciationPeriod(
            String assetId, String depreciationPeriod);

    /**
     * Bulk-fetch DepreciationHistory records for multiple assets in a given period.
     *
     * <p><b>Scheduler use case:</b> Replaces N individual queries with one IN-clause query
     * for efficient upsert batching (checking which records already exist).</p>
     *
     * @param assetIds list of asset IDs
     * @param period depreciation period in "YYYY-MM" format
     * @return list of existing DepreciationHistory records
     */
    @Query("SELECT d FROM DepreciationHistory d " +
           "WHERE d.depreciationPeriod = :period AND d.assetId IN :assetIds")
    List<DepreciationHistory> findByAssetIdsAndPeriod(
            @Param("assetIds") List<String> assetIds,
            @Param("period")   String period);

    // ─── JOIN support: Fetch FarReport for asset context ──────────────────────

    /**
     * Fetch single FarReport record by assetId.
     *
     * <p><b>API response use case:</b> Called after fetching DepreciationHistory
     * to populate composite AssetDepreciationDetailDTO with asset master data.</p>
     *
     * @param assetId asset identifier
     * @return optional FarReport record
     */
    @Query("SELECT f FROM FarReport f WHERE f.assetId = :assetId")
    Optional<FarReport> findFarReportByAssetId(@Param("assetId") String assetId);

    /**
     * Fetch multiple FarReport records by assetId list.
     *
     * <p><b>API response use case:</b> Batch fetch FarReport records for a page
     * of DepreciationHistory results in one query instead of N individual lookups.</p>
     *
     * @param assetIds list of asset IDs
     * @return list of FarReport records
     */
    @Query("SELECT f FROM FarReport f WHERE f.assetId IN :assetIds")
    List<FarReport> findFarReportsByAssetIds(@Param("assetIds") List<String> assetIds);

    // ─── Streaming export query (unfiltered) ─────────────────────────────────

    /**
     * Stream all DepreciationHistory records without filter.
     *
     * <p><b>Used by:</b> Export endpoint when no DynamicFilterRequest is provided.
     * Returns a server-side cursor for memory-efficient large exports.</p>
     *
     * @return stream of DepreciationHistory records
     */
    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT d FROM DepreciationHistory d")
    Stream<DepreciationHistory> streamAll();

    // ─── Convenience finders ─────────────────────────────────────────────────

    /**
     * Fetch paginated DepreciationHistory records for a single asset.
     *
     * @param assetId asset identifier
     * @param pageable pagination parameters
     * @return page of DepreciationHistory records
     */
    Page<DepreciationHistory> findByAssetId(String assetId, Pageable pageable);


    /**
     * Stream all distinct serial numbers from assets with depreciation history.
     *
     * <p><b>Used by:</b> Audit or data validation features to list all assets.</p>
     *
     * @return stream of serial numbers
     */
    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT DISTINCT d.assetId FROM DepreciationHistory d " +
           "WHERE d.assetId IS NOT NULL")
    Stream<String> streamAllAssetIds();
}