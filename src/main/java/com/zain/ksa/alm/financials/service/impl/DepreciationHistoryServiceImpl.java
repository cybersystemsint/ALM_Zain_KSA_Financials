package com.zain.ksa.alm.financials.service.impl;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.DepreciationHistoryDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;
import com.zain.ksa.alm.financials.exception.ResourceNotFoundException;
import com.zain.ksa.alm.financials.mapper.InventoryMapper;
import com.zain.ksa.alm.financials.repository.DepreciationHistoryRepository;
import com.zain.ksa.alm.financials.service.DepreciationHistoryService;
import com.zain.ksa.alm.financials.service.export.ExportStrategyFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class DepreciationHistoryServiceImpl implements DepreciationHistoryService {

    private static final Logger log = LoggerFactory.getLogger(DepreciationHistoryServiceImpl.class);

    private final DepreciationHistoryRepository repository;
    private final InventoryMapper               mapper;
    private final ExportStrategyFactory         exportFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private static final GenericSpecificationBuilder<DepreciationHistory> SPEC_BUILDER =
            new GenericSpecificationBuilder<>("depreciationDate");

    public DepreciationHistoryServiceImpl(
            DepreciationHistoryRepository repository,
            InventoryMapper mapper,
            ExportStrategyFactory exportFactory,
            EntityManager entityManager) {
        this.repository    = repository;
        this.mapper        = mapper;
        this.exportFactory = exportFactory;
        this.entityManager = entityManager;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(
        value = "depreciation:list",
        key   = "#filter.columnName + ':' + #filter.searchQuery + ':' + "
              + "#filter.dateFrom + ':' + #filter.dateTo + ':' + "
              + "#pageable.pageNumber + ':' + #pageable.pageSize"
    )
    @Transactional(readOnly = true)
    public PagedResponse<DepreciationHistoryDTO> findAll(DynamicFilterRequest filter, Pageable pageable) {
        return PagedResponse.of(
            repository.findAll(SPEC_BUILDER.build(filter), pageable)
                      .map(mapper::toDto)
        );
    }

    @Override
    @Cacheable(value = "depreciation:single", key = "#id")
    @Transactional(readOnly = true)
    public DepreciationHistoryDTO findById(Long id) {
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("DepreciationHistory", "id", id));
    }

    // ── Export (now uses GenericSpecificationBuilder + FilteredStreamRepository) ──

    @Override
    @Transactional(readOnly = true)
    public void exportToResponse(DynamicFilterRequest filter, ExportFormat format,
                                 HttpServletResponse response) throws Exception {
        log.info("Starting depreciation export, format={}", format);

        Specification<DepreciationHistory> spec = SPEC_BUILDER.build(filter);

        try (Stream<DepreciationHistory> stream =
                 (spec != null ? repository.streamAll(spec) : repository.streamAll())) {

            exportFactory.<DepreciationHistoryDTO>resolve(format)
                    .export(
                        stream.peek(entityManager::detach)
                              .map(mapper::toDto),
                        response,
                        "depreciation_history_export"
                    );
        }

        log.info("Depreciation export completed, format={}", format);
    }

    // ── Scheduler support ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<DepreciationHistory> findByAssetAndPeriod(String assetId, String depreciationPeriod) {
        return repository.findByAssetIdAndDepreciationPeriod(assetId, depreciationPeriod);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepreciationHistory> findByAssetIdsAndPeriod(List<String> assetIds, String period) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        return repository.findByAssetIdsAndPeriod(assetIds, period);
    }

    @Override
    @CacheEvict(value = {"depreciation:list", "depreciation:single"}, allEntries = true)
    @Transactional
    public DepreciationHistory save(DepreciationHistory entity) {
        return repository.save(entity);
    }

    @Override
    @CacheEvict(value = {"depreciation:list", "depreciation:single"}, allEntries = true)
    @Transactional
    public List<DepreciationHistory> saveAll(List<DepreciationHistory> entities) {
        return repository.saveAll(entities);
    }
}