package com.zain.ksa.alm.financials.service.impl;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;
import com.zain.ksa.alm.financials.mapper.InventoryMapper;
import com.zain.ksa.alm.financials.repository.DepreciationHistoryRepository;
import com.zain.ksa.alm.financials.repository.FarReportRepository;
import com.zain.ksa.alm.financials.repository.UnmappedActiveInventoryRepository;
import com.zain.ksa.alm.financials.repository.UnmappedITInventoryRepository;
import com.zain.ksa.alm.financials.repository.UnmappedPassiveInventoryRepository;
import com.zain.ksa.alm.financials.service.export.FileExportStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Executes async, streaming exports to disk files.
 *
 * <h3>Memory management strategy</h3>
 * <p>
 * JPA entities fetched via a database cursor stream are immediately detached
 * from the persistence context after being read. Every {@value L1_CACHE_CLEAR_INTERVAL}
 * rows the entire L1 cache is cleared to reclaim memory, preventing the
 * {@link EntityManager}'s identity map from growing unbounded on multi-million-row exports.
 * </p>
 *
 * <h3>Supported export types</h3>
 * <ul>
 *   <li>{@code depreciation} — filtered streaming from tb_DepreciationHistory</li>
 *   <li>{@code unmapped_active} — full stream from tb_unmapped_active_inventory</li>
 *   <li>{@code unmapped_passive} — full stream from tb_unmapped_passive_inventory</li>
 *   <li>{@code unmapped_it} — full stream from tb_unmapped_IT_Inventory</li>
 *   <li>{@code far_report} — full stream from tb_FarReport</li>
 * </ul>
 */
@Component
public class ExportExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExportExecutor.class);

    private static final int L1_CACHE_CLEAR_INTERVAL   = 1_000;
    private static final int PROGRESS_REPORT_INTERVAL   = 500;

    private final DepreciationHistoryRepository      depreciationRepo;
    private final UnmappedActiveInventoryRepository   activeRepo;
    private final UnmappedPassiveInventoryRepository  passiveRepo;
    private final UnmappedITInventoryRepository       itRepo;
    private final FarReportRepository                 farReportRepo;
    private final InventoryMapper                     mapper;
    private final FileExportStrategy                  fileExportStrategy;

    @PersistenceContext
    private EntityManager entityManager;

    public ExportExecutor(DepreciationHistoryRepository depreciationRepo,
                          UnmappedActiveInventoryRepository activeRepo,
                          UnmappedPassiveInventoryRepository passiveRepo,
                          UnmappedITInventoryRepository itRepo,
                          FarReportRepository farReportRepo,
                          InventoryMapper mapper,
                          FileExportStrategy fileExportStrategy) {
        this.depreciationRepo   = depreciationRepo;
        this.activeRepo         = activeRepo;
        this.passiveRepo        = passiveRepo;
        this.itRepo             = itRepo;
        this.farReportRepo      = farReportRepo;
        this.mapper             = mapper;
        this.fileExportStrategy = fileExportStrategy;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * @param exportType one of: depreciation, unmapped_active, unmapped_passive, unmapped_it, far_report
     */
    @Async("exportTaskExecutor")
    @Transactional(readOnly = true)
    public void execute(String jobId, String exportType, DynamicFilterRequest filter,
                        ExportFormat format, String filePath,
                        BiConsumer<Long, Long> progressCallback,
                        Consumer<ExportResult> resultCallback) {
        long startMs = System.currentTimeMillis();
        try {
            log.info("[Export] Starting jobId={}, type={}, format={}, file={}",
                     jobId, exportType, format, filePath);

            l1Counter.set(0L); // reset for this run

            long totalRows = countRows(exportType);
            progressCallback.accept(totalRows, 0L);

            if (totalRows == 0) {
                log.warn("[Export] No rows to export for jobId={}, type={}", jobId, exportType);
                resultCallback.accept(ExportResult.success(filePath));
                return;
            }

            AtomicLong processed = new AtomicLong(0);

            switch (exportType) {
                case "depreciation"     -> exportDepreciation(filter, format, filePath, totalRows, processed, progressCallback);
                case "unmapped_active"  -> exportUnmappedActive(format, filePath, totalRows, processed, progressCallback);
                case "unmapped_passive" -> exportUnmappedPassive(format, filePath, totalRows, processed, progressCallback);
                case "unmapped_it"      -> exportUnmappedIT(format, filePath, totalRows, processed, progressCallback);
                case "far_report"       -> exportFarReport(format, filePath, totalRows, processed, progressCallback);
                default -> throw new IllegalArgumentException("Unknown export type: " + exportType);
            }

            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("[Export] Completed jobId={}, rows={}, elapsed={}ms ({}s)",
                     jobId, processed.get(), elapsedMs, elapsedMs / 1000);
            resultCallback.accept(ExportResult.success(filePath));

        } catch (Exception ex) {
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.error("[Export] Failed jobId={} after {}ms: {}", jobId, elapsedMs, ex.getMessage(), ex);
            resultCallback.accept(ExportResult.failure(ex.getMessage()));
        } finally {
            l1Counter.remove(); // prevent ThreadLocal leak in thread pools
        }
    }

    // ── Row counts ────────────────────────────────────────────────────────────

    private long countRows(String exportType) {
        return switch (exportType) {
            case "depreciation"     -> depreciationRepo.count();
            case "unmapped_active"  -> activeRepo.count();
            case "unmapped_passive" -> passiveRepo.count();
            case "unmapped_it"      -> itRepo.count();
            case "far_report"       -> farReportRepo.count();
            default -> 0;
        };
    }

    // ── Export methods ────────────────────────────────────────────────────────

    private void exportDepreciation(DynamicFilterRequest filter, ExportFormat format,
                                     String filePath, long totalRows, AtomicLong processed,
                                     BiConsumer<Long, Long> progressCallback) throws Exception {
        Map<String, String> filterBy = filter.getFilterBy();
        String assetId      = getFilterValue(filterBy, "assetId");
        String serialNumber = getFilterValue(filterBy, "serialNumber");
        String statusFlag   = getFilterValue(filterBy, "statusFlag");
        String category     = getFilterValue(filterBy, "category");
        String nodeType     = getFilterValue(filterBy, "nodeType");
        String mapped       = getFilterValue(filterBy, "mapped");

        try (Stream<DepreciationHistory> stream = depreciationRepo.streamByFilters(
                assetId, serialNumber, statusFlag, category, nodeType, mapped,
                filter.getDateFrom(), filter.getDateTo())) {

            fileExportStrategy.exportToFile(
                    stream.peek(this::detachAndMaybeClearL1Cache)
                          .peek(e -> trackProgress(totalRows, processed, progressCallback))
                          .map(mapper::toDto),
                    filePath, format);
        }
    }

    private void exportUnmappedActive(ExportFormat format, String filePath,
                                       long totalRows, AtomicLong processed,
                                       BiConsumer<Long, Long> progressCallback) throws Exception {
        try (var stream = activeRepo.streamAll()) {
            fileExportStrategy.exportToFile(
                    stream.peek(this::detachAndMaybeClearL1Cache)
                          .peek(e -> trackProgress(totalRows, processed, progressCallback))
                          .map(mapper::toDto),
                    filePath, format);
        }
    }

    private void exportUnmappedPassive(ExportFormat format, String filePath,
                                        long totalRows, AtomicLong processed,
                                        BiConsumer<Long, Long> progressCallback) throws Exception {
        try (var stream = passiveRepo.streamAll()) {
            fileExportStrategy.exportToFile(
                    stream.peek(this::detachAndMaybeClearL1Cache)
                          .peek(e -> trackProgress(totalRows, processed, progressCallback))
                          .map(mapper::toDto),
                    filePath, format);
        }
    }

    private void exportUnmappedIT(ExportFormat format, String filePath,
                                   long totalRows, AtomicLong processed,
                                   BiConsumer<Long, Long> progressCallback) throws Exception {
        try (var stream = itRepo.streamAll()) {
            fileExportStrategy.exportToFile(
                    stream.peek(this::detachAndMaybeClearL1Cache)
                          .peek(e -> trackProgress(totalRows, processed, progressCallback))
                          .map(mapper::toDto),
                    filePath, format);
        }
    }

    /**
     * Streams entire FAR report to file via DTO mapping.
     * Same detach + L1 clear strategy as all other exports.
     */
    private void exportFarReport(ExportFormat format, String filePath,
                                  long totalRows, AtomicLong processed,
                                  BiConsumer<Long, Long> progressCallback) throws Exception {
        try (var stream = farReportRepo.streamAll()) {
            fileExportStrategy.exportToFile(
                    stream.peek(this::detachAndMaybeClearL1Cache)
                          .peek(e -> trackProgress(totalRows, processed, progressCallback))
                          .map(mapper::toFarReportDto),
                    filePath, format);
        }
    }

    // ── L1 cache management ───────────────────────────────────────────────────

    private final ThreadLocal<Long> l1Counter = ThreadLocal.withInitial(() -> 0L);

    private void detachAndMaybeClearL1Cache(Object entity) {
        entityManager.detach(entity);

        long count = l1Counter.get() + 1;
        l1Counter.set(count);

        if (count % L1_CACHE_CLEAR_INTERVAL == 0) {
            entityManager.clear();
        }
    }

    // ── Progress tracking ─────────────────────────────────────────────────────

    private void trackProgress(long totalRows, AtomicLong processed,
                                BiConsumer<Long, Long> progressCallback) {
        long count = processed.incrementAndGet();
        if (count % PROGRESS_REPORT_INTERVAL == 0 || count == totalRows) {
            progressCallback.accept(totalRows, count);
        }
    }

    // ── Filter helpers ────────────────────────────────────────────────────────

    private String getFilterValue(Map<String, String> filterBy, String key) {
        if (filterBy == null) return null;
        String val = filterBy.get(key);
        return (val != null && !val.isBlank()) ? val : null;
    }

    // ── Result type ───────────────────────────────────────────────────────────

    public static class ExportResult {
        public final boolean success;
        public final String  filePath;
        public final String  error;

        private ExportResult(boolean success, String filePath, String error) {
            this.success  = success;
            this.filePath = filePath;
            this.error    = error;
        }

        public static ExportResult success(String filePath) {
            return new ExportResult(true, filePath, null);
        }

        public static ExportResult failure(String error) {
            return new ExportResult(false, null, error);
        }
    }
}