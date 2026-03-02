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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
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

    // ── Export ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public void exportToResponse(DynamicFilterRequest filter, ExportFormat format,
                                 HttpServletResponse response) throws Exception {
        log.info("Starting depreciation export, format={}", format);

        Map<String, String> filterBy    = filter.getFilterBy();
        String              assetId      = getFilterValue(filterBy, "assetId");
        String              serialNumber = getFilterValue(filterBy, "serialNumber");
        String              statusFlag   = getFilterValue(filterBy, "statusFlag");
        String              category     = getFilterValue(filterBy, "category");
        String              nodeType     = getFilterValue(filterBy, "nodeType");
        String              mapped       = getFilterValue(filterBy, "mapped");

        try (Stream<DepreciationHistory> stream = repository.streamByFilters(
                assetId, serialNumber, statusFlag, category, nodeType, mapped,
                filter.getDateFrom(), filter.getDateTo())) {

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

    /**
     * Batch-saves a list of DepreciationHistory records in one JDBC round-trip
     * per chunk. Cache is evicted so stale list/single entries are cleared
     * immediately after the batch completes.
     *
     * Requires in application.properties:
     *   spring.jpa.properties.hibernate.jdbc.batch_size=500
     *   spring.jpa.properties.hibernate.order_inserts=true
     */
    @Override
    @CacheEvict(value = {"depreciation:list", "depreciation:single"}, allEntries = true)
    @Transactional
    public List<DepreciationHistory> saveAll(List<DepreciationHistory> entities) {
        return repository.saveAll(entities);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getFilterValue(Map<String, String> filterBy, String key) {
        if (filterBy == null) return null;
        String val = filterBy.get(key);
        return (val != null && !val.isBlank()) ? val : null;
    }
}