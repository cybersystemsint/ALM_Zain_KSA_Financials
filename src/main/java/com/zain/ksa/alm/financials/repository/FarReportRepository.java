package com.zain.ksa.alm.financials.repository;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import javax.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.repository.custom.FilteredStreamRepository;

import static org.hibernate.jpa.QueryHints.HINT_CACHEABLE;
import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@Repository
public interface FarReportRepository
        extends JpaRepository<FarReport, Long>,
                JpaSpecificationExecutor<FarReport>,
                FilteredStreamRepository<FarReport> {

    List<FarReport> findByAssetId(String assetId);

    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT r FROM FarReport r")
    Stream<FarReport> streamAll();

    @Query("SELECT f FROM FarReport f WHERE f.assetId IN :assetIds")
    List<FarReport> findByAssetIdIn(@Param("assetIds") Collection<String> assetIds);
}