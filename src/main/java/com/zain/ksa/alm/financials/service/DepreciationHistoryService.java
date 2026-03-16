package com.zain.ksa.alm.financials.service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.response.AssetDepreciationDetailDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DepreciationHistoryService {

    // ── Read ──────────────────────────────────────────────────────────────────
    PagedResponse<AssetDepreciationDetailDTO> findAll(DynamicFilterRequest filter, Pageable pageable);

    AssetDepreciationDetailDTO findById(Long id);

    // ── Scheduler support ─────────────────────────────────────────────────────
    Optional<DepreciationHistory> findByAssetAndPeriod(String assetId, String depreciationPeriod);

    List<DepreciationHistory> findByAssetIdsAndPeriod(List<String> assetIds, String period);

    DepreciationHistory save(DepreciationHistory entity);

    List<DepreciationHistory> saveAll(List<DepreciationHistory> entities);
}