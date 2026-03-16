package com.zain.ksa.alm.financials.service.impl;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.AssetDepreciationDetailDTO;
import com.zain.ksa.alm.financials.dto.response.DepreciationHistoryDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;
import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.exception.ResourceNotFoundException;
import com.zain.ksa.alm.financials.repository.DepreciationHistoryRepository;
import com.zain.ksa.alm.financials.service.DepreciationHistoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementation for DepreciationHistory.
 *
 * ── Changes from previous version ───────────────────────────────────────────
 *
 *  ① exportToResponse() REMOVED entirely.
 *
 *    PROBLEM 1 — OOM on large datasets:
 *    The method called stream.collect(Collectors.groupingBy(...)) which
 *    materializes the ENTIRE result set into a HashMap in heap before any
 *    row is written to the response. For a 3M-row table this allocates
 *    ~2–4 GB of objects, triggering OutOfMemoryError or GC pauses that
 *    stall the JVM for tens of seconds.
 *
 *    PROBLEM 2 — N+1 query explosion:
 *    Inside the flatMap, repository.findFarReportByAssetId(assetId) fired
 *    one DB round-trip per unique assetId. With 100K unique assets that is
 *    100,000 individual SQL SELECT statements — equivalent to a full table
 *    scan repeated 100K times.
 *
 *    PROBLEM 3 — Dead code / wrong thread:
 *    The production export path is ExportExecutor → FileExportStrategy →
 *    ExportController (async job system). exportToResponse() wrote directly
 *    to HttpServletResponse from a stream lambda, bypassing the job system
 *    entirely. If ever called, it would write to an HTTP response from a
 *    background thread context, potentially causing IllegalStateException
 *    ("response already committed") or writing to a closed socket.
 *
 *    FIX: Removed. All depreciation exports go through ExportExecutor which
 *    uses a proper JDBC streaming cursor (setFetchSize(MIN_VALUE)), writes
 *    rows one at a time to disk, and serves the completed file via
 *    ExportController with GZIP compression. The DepreciationHistoryController
 *    already routes to ExportJobService.startExport("depreciation", ...) so
 *    no controller changes are needed.
 *
 *  ② ExportStrategyFactory dependency REMOVED.
 *    It was only used by the deleted exportToResponse(). Removing it
 *    eliminates an unnecessary Spring bean dependency.
 *
 *  ③ findAll() — N+1 for list endpoint documented and left as-is.
 *    The list endpoint (findAll) does one extra query per page to fetch
 *    FarReport records via findFarReportsByAssetIds(). This is an acceptable
 *    N=2 pattern for pagination (page query + one IN-clause lookup) and does
 *    not need fixing. It is documented here for clarity.
 *
 *  ④ Everything else unchanged — findAll, findById, scheduler support,
 *    save, saveAll, mergeToCompositeDTO.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
public class DepreciationHistoryServiceImpl implements DepreciationHistoryService {

    private static final Logger log = LoggerFactory.getLogger(DepreciationHistoryServiceImpl.class);

    private final DepreciationHistoryRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final GenericSpecificationBuilder<DepreciationHistory> SPEC_BUILDER =
            new GenericSpecificationBuilder<>("depreciationDate");

    public DepreciationHistoryServiceImpl(DepreciationHistoryRepository repository) {
        this.repository = repository;
    }

    // ── Read: Paginated composite DTO ─────────────────────────────────────────

    /**
     * Fetch paginated depreciation records with full asset context.
     *
     * Query pattern (2 queries per page, not N+1):
     *   1. Page query on tb_DepreciationHistory with filter spec
     *   2. One IN-clause on tb_FarReport for the asset IDs on this page
     *
     * This is an intentional and acceptable trade-off: a JOIN on the JPA
     * pagination layer would require a count query workaround, and the
     * ExportExecutor already handles the full-table case with a proper
     * SQL JOIN + streaming cursor.
     */
    @Override
    @Cacheable(
        value = "depreciation:list",
        key = "T(java.util.Objects).hash(#filter.columnName, #filter.searchQuery, " +
              "#filter.dateFrom, #filter.dateTo, #pageable.pageNumber, #pageable.pageSize)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<AssetDepreciationDetailDTO> findAll(DynamicFilterRequest filter, Pageable pageable) {
        Specification<DepreciationHistory> spec = SPEC_BUILDER.build(filter);
        Page<DepreciationHistory> depreciationPage = repository.findAll(spec, pageable);

        if (depreciationPage.isEmpty()) {
            return PagedResponse.<AssetDepreciationDetailDTO>builder()
                    .content(List.of())
                    .pageNumber(depreciationPage.getNumber())
                    .pageSize(depreciationPage.getSize())
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();
        }

        List<String> assetIds = depreciationPage.getContent().stream()
                .map(DepreciationHistory::getAssetId)
                .collect(Collectors.toList());

        // Single IN-clause query — not N+1
        List<FarReport> farReports = repository.findFarReportsByAssetIds(assetIds);
        Map<String, FarReport> farMap = farReports.stream()
                .collect(Collectors.toMap(FarReport::getAssetId, f -> f, (a, b) -> a));

        List<AssetDepreciationDetailDTO> content = depreciationPage.getContent().stream()
                .map(depr -> mergeToCompositeDTO(depr, farMap.get(depr.getAssetId())))
                .collect(Collectors.toList());

        return PagedResponse.<AssetDepreciationDetailDTO>builder()
                .content(content)
                .pageNumber(depreciationPage.getNumber())
                .pageSize(depreciationPage.getSize())
                .totalElements(depreciationPage.getTotalElements())
                .totalPages(depreciationPage.getTotalPages())
                .last(depreciationPage.isLast())
                .build();
    }

    /**
     * Fetch single record with asset context by DepreciationHistory ID.
     */
    @Override
    @Cacheable(value = "depreciation:single", key = "#id")
    @Transactional(readOnly = true)
    public AssetDepreciationDetailDTO findById(Long id) {
        DepreciationHistory depr = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DepreciationHistory", "id", id));

        FarReport far = repository.findFarReportByAssetId(depr.getAssetId()).orElse(null);
        return mergeToCompositeDTO(depr, far);
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
        if (assetIds == null || assetIds.isEmpty()) return List.of();
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

    // ── Merge helper ──────────────────────────────────────────────────────────

    /**
     * Merges a DepreciationHistory record with its FarReport into a composite DTO.
     * FarReport may be null if the asset was deleted — depreciation fields are
     * always populated, asset fields are populated only when far != null.
     */
    private AssetDepreciationDetailDTO mergeToCompositeDTO(DepreciationHistory depr, FarReport far) {
        AssetDepreciationDetailDTO.AssetDepreciationDetailDTOBuilder builder =
                AssetDepreciationDetailDTO.builder()
                        .recordNo(depr.getRecordNo())
                        .depreciationPeriod(depr.getDepreciationPeriod())
                        .monthlyDepreciationAmt(depr.getMonthlyDepreciationAmt())
                        .accumulatedDepreciationAmt(depr.getAccumulatedDepreciationAmt())
                        .netCost(depr.getNetCost())
                        .depreciationDate(depr.getDepreciationDate())
                        .recordDatetime(depr.getRecordDatetime())
                        .createdBy(depr.getCreatedBy())
                        .changedBy(depr.getChangedBy())
                        .assetId(depr.getAssetId());

        if (far != null) {
            builder
                    .book(far.getBook())
                    .description(far.getDescription())
                    .serialNumber(far.getSerialNumber())
                    .assetType(far.getAssetType())
                    .category(far.getCategory())
                    .categoryDescription(far.getCategoryDescription())
                    .cost(far.getCost())
                    .salvageValue(far.getSalvageValue())
                    .life(far.getLife())
                    .datePlacedInService(far.getDatePlacedInService() != null
                            ? far.getDatePlacedInService().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                            : null)
                    .costAccount(far.getCostAccount())
                    .accumulatedDepreAccount(far.getAccumulatedDepreAccount())
                    .expenseAccount(far.getExpenseAccount())
                    .quantity(far.getQuantity())
                    .value(far.getValue());
        }

        return builder.build();
    }
}