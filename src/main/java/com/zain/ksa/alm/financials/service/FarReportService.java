package com.zain.ksa.alm.financials.service;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.response.FarReportDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.FarReport;

public interface FarReportService {

    FarReport save(FarReport farReport);
    List<FarReport> saveAll(List<FarReport> farReports);
    List<FarReport> findByAssetId(String assetId);
    Page<FarReport> findAll(Pageable pageable);

    /** Paginated filtered query — data only, no aggregates. */
    PagedResponse<FarReportDTO> findAll(DynamicFilterRequest filter, Pageable pageable);

    /**
     * Paginated filtered query WITH summary aggregates (cost, NBV, depreciation).
     * Returns both filtered sums and unfiltered grand totals.
     */
    Map<String, Object> findAllWithSummary(DynamicFilterRequest filter, Pageable pageable);

    Map<String, Object> processUpload(List<Map<String, Object>> rows, String source);


}