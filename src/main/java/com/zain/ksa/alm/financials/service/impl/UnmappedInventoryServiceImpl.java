package com.zain.ksa.alm.financials.service.impl;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletResponse;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.*;
import com.zain.ksa.alm.financials.entity.*;
import com.zain.ksa.alm.financials.mapper.InventoryMapper;
import com.zain.ksa.alm.financials.repository.*;
import com.zain.ksa.alm.financials.scheduler.UnmappedInventoryScheduler;
import com.zain.ksa.alm.financials.service.UnmappedInventoryService;
import com.zain.ksa.alm.financials.service.export.ExportStrategyFactory;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class UnmappedInventoryServiceImpl implements UnmappedInventoryService {

    private static final Logger log = LoggerFactory.getLogger(UnmappedInventoryServiceImpl.class);

    private final UnmappedActiveInventoryRepository  activeRepo;
    private final UnmappedPassiveInventoryRepository passiveRepo;
    private final UnmappedITInventoryRepository      itRepo;
    private final InventoryMapper                    mapper;
    private final ExportStrategyFactory              exportFactory;
    private final UnmappedInventoryScheduler         scheduler;

    @PersistenceContext
    private EntityManager entityManager;

    // For Active inventory
    private static final GenericSpecificationBuilder<UnmappedActiveInventory> ACTIVE_SPEC =
            new GenericSpecificationBuilder<>("recordDateTime");

    // For Passive inventory
    private static final GenericSpecificationBuilder<UnmappedPassiveInventory> PASSIVE_SPEC =
            new GenericSpecificationBuilder<>("recordDateTime");

    // For IT inventory
    private static final GenericSpecificationBuilder<UnmappedITInventory> IT_SPEC =
            new GenericSpecificationBuilder<>("recordDatetime");

    public UnmappedInventoryServiceImpl(
            UnmappedActiveInventoryRepository activeRepo,
            UnmappedPassiveInventoryRepository passiveRepo,
            UnmappedITInventoryRepository itRepo,
            InventoryMapper mapper,
            ExportStrategyFactory exportFactory,
            UnmappedInventoryScheduler scheduler) {
        this.activeRepo    = activeRepo;
        this.passiveRepo   = passiveRepo;
        this.itRepo        = itRepo;
        this.mapper        = mapper;
        this.exportFactory = exportFactory;
        this.scheduler     = scheduler;
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "unmapped-active:list",
               key = "T(String).valueOf(#filter.siteId) + ':' + "
                   + "T(String).valueOf(#filter.columnName) + ':' + "
                   + "T(String).valueOf(#filter.searchQuery) + ':' + "
                   + "#pageable.pageNumber + ':' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public PagedResponse<UnmappedActiveInventoryDTO> findAllActive(
            DynamicFilterRequest filter, Pageable pageable) {

        long startSeq = (long) pageable.getPageNumber() * pageable.getPageSize();
        AtomicLong sequence = new AtomicLong(startSeq);

        return PagedResponse.of(
            activeRepo.findAll(ACTIVE_SPEC.build(filter), pageable)
                .map(mapper::toDto)
                .map(dto -> dto.withSequenceNo(sequence.incrementAndGet()))
        );
    }

    @Override
    @Cacheable(value = "unmapped-passive:list",
               key = "T(String).valueOf(#filter.siteId) + ':' + "
                   + "T(String).valueOf(#filter.columnName) + ':' + "
                   + "T(String).valueOf(#filter.searchQuery) + ':' + "
                   + "#pageable.pageNumber + ':' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public PagedResponse<UnmappedPassiveInventoryDTO> findAllPassive(
            DynamicFilterRequest filter, Pageable pageable) {

        long startSeq = (long) pageable.getPageNumber() * pageable.getPageSize();
        AtomicLong sequence = new AtomicLong(startSeq);

        return PagedResponse.of(
            passiveRepo.findAll(PASSIVE_SPEC.build(filter), pageable)
                .map(mapper::toDto)
                .map(dto -> dto.withSequenceNo(sequence.incrementAndGet()))
        );
    }

    @Override
    @Cacheable(value = "unmapped-it:list",
               key = "T(String).valueOf(#filter.siteId) + ':' + "
                   + "T(String).valueOf(#filter.columnName) + ':' + "
                   + "T(String).valueOf(#filter.searchQuery) + ':' + "
                   + "#pageable.pageNumber + ':' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public PagedResponse<UnmappedITInventoryDTO> findAllIT(
            DynamicFilterRequest filter, Pageable pageable) {

        long startSeq = (long) pageable.getPageNumber() * pageable.getPageSize();
        AtomicLong sequence = new AtomicLong(startSeq);

        return PagedResponse.of(
            itRepo.findAll(IT_SPEC.build(filter), pageable)
                .map(mapper::toDto)
                .map(dto -> dto.withSequenceNo(sequence.incrementAndGet()))
        );
    }

    // ── Export (sync — writes directly to HTTP response, WITH FILTER) ─────────

    @Override
    @Transactional(readOnly = true)
    public void exportActiveToResponse(DynamicFilterRequest filter, ExportFormat format,
                                       HttpServletResponse response) throws Exception {
        log.info("Exporting unmapped active inventory, format={}", format);

        Specification<UnmappedActiveInventory> spec = ACTIVE_SPEC.build(filter);

        try (var stream = (spec != null ? activeRepo.streamAll(spec) : activeRepo.streamAll())) {
            AtomicLong sequence = new AtomicLong(1);

            exportFactory.<UnmappedActiveInventoryDTO>resolve(format)
                .export(
                    stream.peek(entityManager::detach)
                          .map(mapper::toDto)
                          .map(dto -> dto.withSequenceNo(sequence.getAndIncrement())),
                    response,
                    "unmapped_active_export"
                );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void exportPassiveToResponse(DynamicFilterRequest filter, ExportFormat format,
                                        HttpServletResponse response) throws Exception {
        log.info("Exporting unmapped passive inventory, format={}", format);

        Specification<UnmappedPassiveInventory> spec = PASSIVE_SPEC.build(filter);

        try (var stream = (spec != null ? passiveRepo.streamAll(spec) : passiveRepo.streamAll())) {
            AtomicLong sequence = new AtomicLong(1);

            exportFactory.<UnmappedPassiveInventoryDTO>resolve(format)
                .export(
                    stream.peek(entityManager::detach)
                          .map(mapper::toDto)
                          .map(dto -> dto.withSequenceNo(sequence.getAndIncrement())),
                    response,
                    "unmapped_passive_export"
                );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void exportITToResponse(DynamicFilterRequest filter, ExportFormat format,
                                   HttpServletResponse response) throws Exception {
        log.info("Exporting unmapped IT inventory, format={}", format);

        Specification<UnmappedITInventory> spec = IT_SPEC.build(filter);

        try (var stream = (spec != null ? itRepo.streamAll(spec) : itRepo.streamAll())) {
            AtomicLong sequence = new AtomicLong(1);

            exportFactory.<UnmappedITInventoryDTO>resolve(format)
                .export(
                    stream.peek(entityManager::detach)
                          .map(mapper::toDto)
                          .map(dto -> dto.withSequenceNo(sequence.getAndIncrement())),
                    response,
                    "unmapped_it_export"
                );
        }
    }

    // ── Async manual triggers ─────────────────────────────────────────────────

    @Override
    @Async("schedulerExecutor")
    @CacheEvict(value = "unmapped-active:list", allEntries = true)
    public void triggerActiveReconciliationAsync() {
        log.info("[ManualTrigger] Starting active inventory reconciliation (async)...");
        scheduler.reconcileActiveInventoryPublic();
        log.info("[ManualTrigger] Active inventory reconciliation complete.");
    }

    @Override
    @Async("schedulerExecutor")
    @CacheEvict(value = "unmapped-passive:list", allEntries = true)
    public void triggerPassiveReconciliationAsync() {
        log.info("[ManualTrigger] Starting passive inventory reconciliation (async)...");
        scheduler.reconcilePassiveInventoryPublic();
        log.info("[ManualTrigger] Passive inventory reconciliation complete.");
    }

    @Override
    @Async("schedulerExecutor")
    @CacheEvict(value = "unmapped-it:list", allEntries = true)
    public void triggerITReconciliationAsync() {
        log.info("[ManualTrigger] Starting IT inventory reconciliation (async)...");
        scheduler.reconcileITInventoryPublic();
        log.info("[ManualTrigger] IT inventory reconciliation complete.");
    }

    @Override
    @Async("schedulerExecutor")
    @CacheEvict(value = {"unmapped-active:list", "unmapped-passive:list", "unmapped-it:list"}, allEntries = true)
    public void triggerFullReconciliationAsync() {
        log.info("[ManualTrigger] Starting full reconciliation (async)...");
        scheduler.runUnmappedReconciliation();
        log.info("[ManualTrigger] Full reconciliation complete.");
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Override public boolean isActiveRunning()  { return scheduler.isActiveRunning(); }
    @Override public boolean isPassiveRunning() { return scheduler.isPassiveRunning(); }
    @Override public boolean isItRunning()      { return scheduler.isItRunning(); }
    @Override public boolean isAnyRunning()     { return scheduler.isAnyRunning(); }
}