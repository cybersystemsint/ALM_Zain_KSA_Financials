package com.zain.ksa.alm.financials.repository;

import com.zain.ksa.alm.financials.entity.DepreciationHistory;
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


@Repository
public interface DepreciationHistoryRepository
        extends JpaRepository<DepreciationHistory, Long>,
                JpaSpecificationExecutor<DepreciationHistory>,
                FilteredStreamRepository<DepreciationHistory> {

    // ─── Scheduler upsert support ────────────────────────────────────────────

    Optional<DepreciationHistory> findByAssetIdAndDepreciationPeriod(
            String assetId, String depreciationPeriod);

    @Query("SELECT d FROM DepreciationHistory d " +
           "WHERE d.depreciationPeriod = :period AND d.assetId IN :assetIds")
    List<DepreciationHistory> findByAssetIdsAndPeriod(
            @Param("assetIds") List<String> assetIds,
            @Param("period")   String period);

    // ─── Streaming export query (unfiltered) ─────────────────────────────────

    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT d FROM DepreciationHistory d")
    Stream<DepreciationHistory> streamAll();

    // ─── Convenience finders ─────────────────────────────────────────────────

    Page<DepreciationHistory> findByAssetId(String assetId, Pageable pageable);

    Page<DepreciationHistory> findBySerialNumber(String serialNumber, Pageable pageable);

    boolean existsBySerialNumber(String serialNumber);

    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT DISTINCT d.serialNumber FROM DepreciationHistory d "
         + "WHERE d.serialNumber IS NOT NULL")
    Stream<String> streamAllSerialNumbers();
}