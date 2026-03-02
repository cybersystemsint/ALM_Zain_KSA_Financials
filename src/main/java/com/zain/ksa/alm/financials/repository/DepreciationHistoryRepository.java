package com.zain.ksa.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.DepreciationHistory;

import javax.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_CACHEABLE;
import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

/**
 * Repository for tb_DepreciationHistory.
 *
 * <h3>Design notes for ~3M rows/month</h3>
 * <ul>
 *   <li>{@link JpaSpecificationExecutor} enables dynamic multi-column filtering
 *       without N custom queries.</li>
 *   <li>Streaming queries use server-side cursors ({@code fetchSize=500}) to
 *       avoid loading all rows into memory. The caller must wrap in a read-only
 *       {@code @Transactional} and close the stream via try-with-resources.</li>
 *   <li>{@code HINT_CACHEABLE=false} prevents L2 query cache from defeating
 *       streaming — a cached result would materialise the entire list in heap.</li>
 *   <li>{@code findByAssetIdsAndPeriod} uses the composite index
 *       idx_dh_asset_period for efficient bulk upsert lookups.</li>
 * </ul>
 *
 * <h3>MySQL requirement</h3>
 * <p>JDBC URL must include {@code useCursorFetch=true} for fetch-size hints to
 * take effect. Without it, MySQL Connector/J loads the entire ResultSet into
 * client memory regardless of the hint value.</p>
 *
 * <h3>Performance improvements (v2)</h3>
 * <ul>
 *   <li>Unique constraint uk_dh_asset_period enforced at DB level eliminates
 *       duplicate checks in application code.</li>
 *   <li>Reduced index count (4 instead of 9) makes INSERT/UPDATE batches faster
 *       by reducing index maintenance overhead.</li>
 *   <li>Composite index idx_dh_asset_period used by scheduler bulk-fetch query
 *       — IN clause with 500 asset IDs now uses a fast leaf-level scan.</li>
 * </ul>
 */
@Repository
public interface DepreciationHistoryRepository
        extends JpaRepository<DepreciationHistory, Long>,
                JpaSpecificationExecutor<DepreciationHistory> {

    // ─── Scheduler upsert support ────────────────────────────────────────────

    /**
     * Find existing record for an asset in a specific period (YYYY-MM).
     * Used by the depreciation scheduler to decide insert vs update.
     *
     * Uses composite index idx_dh_asset_period (assetId, depreciationPeriod).
     */
    Optional<DepreciationHistory> findByAssetIdAndDepreciationPeriod(
            String assetId, String depreciationPeriod);

    /**
     * Bulk-fetch existing records for a list of assets in a specific period.
     * Replaces N individual findByAssetIdAndDepreciationPeriod calls with
     * a single IN-clause query per chunk — critical for performance at scale.
     *
     * <p><b>Expected: ~10-50 ms per 500 assets</b> (with proper indexes and
     * database statistics). Much faster than 5 minutes per batch.</p>
     *
     * Uses the composite index idx_dh_asset_period (assetId, depreciationPeriod).
     */
    @Query("SELECT d FROM DepreciationHistory d " +
           "WHERE d.depreciationPeriod = :period AND d.assetId IN :assetIds")
    List<DepreciationHistory> findByAssetIdsAndPeriod(
            @Param("assetIds") List<String> assetIds,
            @Param("period")   String period);

    // ─── Streaming export query ──────────────────────────────────────────────

    /**
     * Streaming query for large filtered exports.
     *
     * <p>Uses server-side cursor with fetchSize=500 to handle millions of rows
     * without OOM. Filters use the {@code IS NULL OR} pattern so null parameters
     * act as wildcards — passing all nulls streams the entire table.</p>
     *
     * <p><b>Fetch size 500</b> balances memory (~1 MB per fetch) against network
     * round-trips. Aligned with the SXSSFWorkbook row-access window and the
     * {@code EntityManager.clear()} interval in {@code ExportExecutor}.</p>
     */
    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT d FROM DepreciationHistory d WHERE "
         + "(:assetId IS NULL OR d.assetId = :assetId) AND "
         + "(:serialNumber IS NULL OR d.serialNumber = :serialNumber) AND "
         + "(:statusFlag IS NULL OR d.statusFlag = :statusFlag) AND "
         + "(:category IS NULL OR d.category = :category) AND "
         + "(:nodeType IS NULL OR d.nodeType = :nodeType) AND "
         + "(:mapped IS NULL OR d.mapped = :mapped) AND "
         + "(:from IS NULL OR d.depreciationDate >= :from) AND "
         + "(:to IS NULL OR d.depreciationDate <= :to)")
    Stream<DepreciationHistory> streamByFilters(
            @Param("assetId")       String assetId,
            @Param("serialNumber")  String serialNumber,
            @Param("statusFlag")    String statusFlag,
            @Param("category")      String category,
            @Param("nodeType")      String nodeType,
            @Param("mapped")        String mapped,
            @Param("from")          LocalDateTime from,
            @Param("to")            LocalDateTime to
    );

    // ─── Convenience finders ─────────────────────────────────────────────────

    Page<DepreciationHistory> findByAssetId(String assetId, Pageable pageable);

    Page<DepreciationHistory> findBySerialNumber(String serialNumber, Pageable pageable);

    boolean existsBySerialNumber(String serialNumber);

    /**
     * Streams all distinct serial numbers. Used by reconciliation to build
     * the in-memory lookup set.
     */
    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT DISTINCT d.serialNumber FROM DepreciationHistory d "
         + "WHERE d.serialNumber IS NOT NULL")
    Stream<String> streamAllSerialNumbers();
}