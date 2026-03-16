package com.zain.ksa.alm.financials.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.response.*;
import com.zain.ksa.alm.financials.entity.*;
import com.zain.ksa.alm.financials.mapper.InventoryMapper;
import com.zain.ksa.alm.financials.repository.*;
import com.zain.ksa.alm.financials.scheduler.UnmappedInventoryScheduler;
import com.zain.ksa.alm.financials.service.UnmappedInventoryService;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for Unmapped Inventory (Active, Passive, IT).
 *
 * ── Changes from previous version ───────────────────────────────────────────
 *
 *  ① exportActiveToResponse(), exportPassiveToResponse(), exportITToResponse()
 *    REMOVED entirely.
 *
 *    These three methods had two compounding problems:
 *
 *    PROBLEM A — Wrong export path (bypasses job system):
 *    The production flow is:
 *      Controller → ExportJobService.startExport() → ExportExecutor
 *      → FileExportStrategy → disk file → ExportController streams to client
 *
 *    All three export controllers (UnmappedInventoryController) already call
 *    exportJobService.startExport("unmapped_active/passive/it", ...) which
 *    routes through ExportExecutor. The exportToResponse() methods were dead
 *    code — nothing called them in the production path.
 *
 *    If they had been called, they would have attempted to write directly to
 *    HttpServletResponse from a JPA stream on a background thread. This causes:
 *      - IllegalStateException if the response was already committed
 *      - Silent data loss if the socket closed mid-stream
 *      - Bypassed progress tracking (ExportJobService never updated)
 *      - No file on disk (nothing to download from /exports/download/{jobId})
 *
 *    PROBLEM B — ExportStrategyFactory resolves old reflection-based strategies:
 *    CsvExportStrategy and ExcelExportStrategy use Java reflection to discover
 *    DTO fields for headers, and write directly to HttpServletResponse output
 *    stream. These are incompatible with the async file-based export pipeline.
 *    Using them would produce a response body with no Content-Disposition header,
 *    no Content-Length, no GZIP, and no progress polling support.
 *
 *    FIX: Removed all three exportToResponse() methods and the ExportStrategyFactory
 *    dependency. Exports for all three inventory types are handled entirely by
 *    ExportExecutor.exportUnmappedActive/Passive/IT() via the job system.
 *    The ExportStrategyFactory and its strategies (CsvExportStrategy,
 *    ExcelExportStrategy) can be deleted from the project if they are not used
 *    elsewhere.
 *
 *  ② findAll* methods unchanged — list endpoints, caching, pagination are correct.
 *  ③ Reconciliation triggers unchanged.
 *  ④ Status methods unchanged.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
public class UnmappedInventoryServiceImpl implements UnmappedInventoryService {

    private static final Logger log = LoggerFactory.getLogger(UnmappedInventoryServiceImpl.class);

    private final UnmappedActiveInventoryRepository  activeRepo;
    private final UnmappedPassiveInventoryRepository passiveRepo;
    private final UnmappedITInventoryRepository      itRepo;
    private final InventoryMapper                    mapper;
    private final UnmappedInventoryScheduler         scheduler;

    @PersistenceContext
    private EntityManager entityManager;

    private static final GenericSpecificationBuilder<UnmappedActiveInventory>  ACTIVE_SPEC  =
            new GenericSpecificationBuilder<>("recordDateTime");
    private static final GenericSpecificationBuilder<UnmappedPassiveInventory> PASSIVE_SPEC =
            new GenericSpecificationBuilder<>("recordDateTime");
    private static final GenericSpecificationBuilder<UnmappedITInventory>      IT_SPEC      =
            new GenericSpecificationBuilder<>("recordDatetime");

    public UnmappedInventoryServiceImpl(
            UnmappedActiveInventoryRepository activeRepo,
            UnmappedPassiveInventoryRepository passiveRepo,
            UnmappedITInventoryRepository itRepo,
            InventoryMapper mapper,
            UnmappedInventoryScheduler scheduler) {
        this.activeRepo  = activeRepo;
        this.passiveRepo = passiveRepo;
        this.itRepo      = itRepo;
        this.mapper      = mapper;
        this.scheduler   = scheduler;
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(
        value = "unmapped-active:list",
        key = "T(java.util.Objects).hash(#filter.siteId, #filter.columnName, #filter.searchQuery) " +
              "+ ':' + #pageable.pageNumber + ':' + #pageable.pageSize",
        condition = "#filter.isEmpty()"
    )
    @Transactional(readOnly = true)
    public PagedResponse<UnmappedActiveInventoryDTO> findAllActive(
            DynamicFilterRequest filter, Pageable pageable) {

        log.debug("Fetching active inventory: filter={}, page={}/{}",
                  filter, pageable.getPageNumber(), pageable.getPageSize());

        long startSeq = (long) pageable.getPageNumber() * pageable.getPageSize();
        AtomicLong sequence = new AtomicLong(startSeq);

        return PagedResponse.of(
            activeRepo.findAll(ACTIVE_SPEC.build(filter), pageable)
                .map(mapper::toDto)
                .map(dto -> dto.withSequenceNo(sequence.incrementAndGet()))
        );
    }

    @Override
    @Cacheable(
        value = "unmapped-passive:list",
        key = "T(java.util.Objects).hash(#filter.siteId, #filter.columnName, #filter.searchQuery) " +
              "+ ':' + #pageable.pageNumber + ':' + #pageable.pageSize",
        condition = "#filter.isEmpty()"
    )
    @Transactional(readOnly = true)
    public PagedResponse<UnmappedPassiveInventoryDTO> findAllPassive(
            DynamicFilterRequest filter, Pageable pageable) {

        log.debug("Fetching passive inventory: filter={}, page={}/{}",
                  filter, pageable.getPageNumber(), pageable.getPageSize());

        long startSeq = (long) pageable.getPageNumber() * pageable.getPageSize();
        AtomicLong sequence = new AtomicLong(startSeq);

        return PagedResponse.of(
            passiveRepo.findAll(PASSIVE_SPEC.build(filter), pageable)
                .map(mapper::toDto)
                .map(dto -> dto.withSequenceNo(sequence.incrementAndGet()))
        );
    }

    @Override
    @Cacheable(
        value = "unmapped-it:list",
        key = "T(java.util.Objects).hash(#filter.siteId, #filter.columnName, #filter.searchQuery) " +
              "+ ':' + #pageable.pageNumber + ':' + #pageable.pageSize",
        condition = "#filter.isEmpty()"
    )
    @Transactional(readOnly = true)
    public PagedResponse<UnmappedITInventoryDTO> findAllIT(
            DynamicFilterRequest filter, Pageable pageable) {

        log.debug("Fetching IT inventory: filter={}, page={}/{}",
                  filter, pageable.getPageNumber(), pageable.getPageSize());

        long startSeq = (long) pageable.getPageNumber() * pageable.getPageSize();
        AtomicLong sequence = new AtomicLong(startSeq);

        return PagedResponse.of(
            itRepo.findAll(IT_SPEC.build(filter), pageable)
                .map(mapper::toDto)
                .map(dto -> dto.withSequenceNo(sequence.incrementAndGet()))
        );
    }

    // ── Async reconciliation triggers ─────────────────────────────────────────

    @Override
    @Async("schedulerExecutor")
    @CacheEvict(value = "unmapped-active:list", allEntries = true)
    public void triggerActiveReconciliationAsync() {
        log.info("[ManualTrigger] Starting active inventory reconciliation...");
        scheduler.reconcileActiveInventoryPublic();
        log.info("[ManualTrigger] Active inventory reconciliation complete.");
    }

    @Override
    @Async("schedulerExecutor")
    @CacheEvict(value = "unmapped-passive:list", allEntries = true)
    public void triggerPassiveReconciliationAsync() {
        log.info("[ManualTrigger] Starting passive inventory reconciliation...");
        scheduler.reconcilePassiveInventoryPublic();
        log.info("[ManualTrigger] Passive inventory reconciliation complete.");
    }

    @Override
    @Async("schedulerExecutor")
    @CacheEvict(value = "unmapped-it:list", allEntries = true)
    public void triggerITReconciliationAsync() {
        log.info("[ManualTrigger] Starting IT inventory reconciliation...");
        scheduler.reconcileITInventoryPublic();
        log.info("[ManualTrigger] IT inventory reconciliation complete.");
    }

    @Override
    @Async("schedulerExecutor")
    @CacheEvict(value = {"unmapped-active:list", "unmapped-passive:list", "unmapped-it:list"}, allEntries = true)
    public void triggerFullReconciliationAsync() {
        log.info("[ManualTrigger] Starting full reconciliation...");
        scheduler.runUnmappedReconciliation();
        log.info("[ManualTrigger] Full reconciliation complete.");
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Override public boolean isActiveRunning()  { return scheduler.isActiveRunning(); }
    @Override public boolean isPassiveRunning() { return scheduler.isPassiveRunning(); }
    @Override public boolean isItRunning()      { return scheduler.isItRunning(); }
    @Override public boolean isAnyRunning()     { return scheduler.isAnyRunning(); }
}