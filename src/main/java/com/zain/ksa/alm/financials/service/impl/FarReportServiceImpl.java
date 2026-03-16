package com.zain.ksa.alm.financials.service.impl;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.FarReportDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.mapper.InventoryMapper;
import com.zain.ksa.alm.financials.repository.FarReportRepository;
import com.zain.ksa.alm.financials.service.FarReportService;
import com.zain.ksa.alm.financials.service.validation.FarReportValidation;
import com.zain.ksa.alm.financials.service.validation.FarReportValidation.ValidationResult;

/**
 * Production-ready FAR report service.
 *
 * ── Changes from previous version ───────────────────────────────────────────
 *
 *  ① SimpleDateFormat REMOVED everywhere.
 *    SimpleDateFormat is not thread-safe. Under concurrent exports it produced
 *    garbled / swapped date values silently. All date formatting now uses
 *    thread-safe java.time.format.DateTimeFormatter (immutable, stateless).
 *
 *  ② streamExportToCsv() REMOVED.
 *    This legacy method had no JDBC fetch-size cursor set, meaning for large
 *    tables the full result set was loaded into heap before writing. It was
 *    also dead code — the controller routes all exports through ExportJobService
 *    → ExportExecutor → FileExportStrategy. Keeping it created a maintenance
 *    trap and a latent OOM risk.
 *
 *  ③ DATE_FORMATS list — synchronized(fmt) blocks REPLACED.
 *    The old list used synchronized(fmt) on each SimpleDateFormat as a
 *    workaround for thread-unsafety. The new implementation uses a list of
 *    DateTimeFormatter instances (truly immutable) with no synchronization.
 *
 *  ④ Everything else is unchanged — upload, findAllWithSummary, CRUD, cache.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
public class FarReportServiceImpl implements FarReportService {

    private static final Logger log = LoggerFactory.getLogger(FarReportServiceImpl.class);

    /** Rows flushed per batch during upload. Matches Hibernate jdbc.batch_size. */
    private static final int UPLOAD_BATCH_SIZE = 500;

    private final FarReportRepository farReportRepository;
    private final JdbcTemplate        jdbcTemplate;
    private final InventoryMapper     mapper;
    private final PreWarmExportJob    preWarmExportJob;

    @PersistenceContext
    private EntityManager entityManager;

    // ── Date range uses creationDate ──────────────────────────────────────────
    private static final GenericSpecificationBuilder<FarReport> SPEC_BUILDER =
            new GenericSpecificationBuilder<>("creationDate");

    // ── Export column definitions (business-required set only) ───────────────

    private static final String[] COLUMNS = {
        "book", "assetId", "quantity",
        "description", "creationDate", "serialNumber", "tagNumber",
        "picStatus", "picDate", "cipDeliveryDate", "linkId", "acceptanceNumber",
        "depreciateFlag", "cipEu", "invoiceNumber", "poNumber", "poLineNumber",
        "uplLine", "transferToNewFar", "assetStatus", "partNumber",
        "vendorName", "vendorNumber", "mergedCode", "costAccount",
        "cipCostAccount", "expenseCostCenter",
        "expenseAccount", "Life", "datePlacedInService", "cost", "nbv",
        "depreciationAmount", "ytdDepreciation", "depreciationReserve",
        "salvageValue", "category", "categoryDescription",
        "locationSegment1", "locationSegment2", "locationSegment3", "locationSegment4", "locations",
        "createdBy", "createdDate", "updatedBy", "updatedDate",
        "monthlyDepreciationAmt", "depreciationDate", "netCost",
        "statusFlag", "financialApproval", "nodeType"
    };

    private static final Set<String> ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(COLUMNS));

    // ── Thread-safe date formatter (immutable — safe to share across threads) ─
    private static final DateTimeFormatter OUTPUT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Ordered list of parseable input formats (DateTimeFormatter is immutable) ─
    // These replace the old synchronized(SimpleDateFormat) workaround.
    private static final List<DateTimeFormatter> DATE_PARSE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    public FarReportServiceImpl(FarReportRepository farReportRepository,
                                JdbcTemplate jdbcTemplate,
                                InventoryMapper mapper,
                                PreWarmExportJob preWarmExportJob) {
        this.farReportRepository = farReportRepository;
        this.jdbcTemplate        = jdbcTemplate;
        this.mapper              = mapper;
        this.preWarmExportJob    = preWarmExportJob;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "far-report:list", allEntries = true)
    @Transactional
    public FarReport save(FarReport farReport) {
        return farReportRepository.save(farReport);
    }

    @Override
    @CacheEvict(value = "far-report:list", allEntries = true)
    @Transactional
    public List<FarReport> saveAll(List<FarReport> farReports) {
        return farReportRepository.saveAll(farReports);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FarReport> findByAssetId(String assetId) {
        return farReportRepository.findByAssetId(assetId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FarReport> findAll(Pageable pageable) {
        return farReportRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FarReportDTO> findAll(DynamicFilterRequest filter, Pageable pageable) {
        return PagedResponse.of(
            farReportRepository.findAll(SPEC_BUILDER.build(filter), pageable)
                               .map(mapper::toFarReportDto)
        );
    }

    // ── List with summary ─────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "far-report:list",
               key = "T(java.util.Objects).hash(#filter.columnName, #filter.searchQuery, " +
                     "#filter.filterBy, #filter.dateFrom, #filter.dateTo, " +
                     "#filter.isMapped, #filter.siteId, " +
                     "#pageable.pageNumber, #pageable.pageSize, #pageable.sort)")
    @Transactional(readOnly = true)
    public Map<String, Object> findAllWithSummary(DynamicFilterRequest filter, Pageable pageable) {

        StringBuilder where  = new StringBuilder(" WHERE 1=1");
        List<Object>  params = new ArrayList<>();

        if (filter.getColumnName() != null && !filter.getColumnName().isBlank()
                && filter.getSearchQuery() != null && !filter.getSearchQuery().isBlank()) {
            String col = validateColumn(filter.getColumnName());
            if (col != null) {
                where.append(" AND LOWER(").append(col).append(") LIKE LOWER(?)");
                params.add("%" + filter.getSearchQuery() + "%");
            } else {
                log.warn("[findAllWithSummary] Rejected unknown columnName: {}", filter.getColumnName());
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    String col = validateColumn(entry.getKey());
                    if (col != null) {
                        where.append(" AND ").append(col).append(" = ?");
                        params.add(entry.getValue());
                    } else {
                        log.warn("[findAllWithSummary] Rejected unknown filterBy key: {}", entry.getKey());
                    }
                }
            }
        }

        LocalDateTime dateFrom = parseDate(filter.getDateFrom(), true);
        LocalDateTime dateTo   = parseDate(filter.getDateTo(), false);

        if (dateFrom != null) {
            where.append(" AND creationDate >= ?");
            params.add(java.sql.Timestamp.valueOf(dateFrom));
        }
        if (dateTo != null) {
            where.append(" AND creationDate <= ?");
            params.add(java.sql.Timestamp.valueOf(dateTo));
        }

        if (dateFrom != null) filter.setDateFrom(dateFrom.toString());
        if (dateTo   != null) filter.setDateTo(dateTo.toString());

       boolean hasFilters = params.size() > 0;

        String aggregateSql =
            "SELECT COUNT(*) AS cnt," +
            "  COALESCE(SUM(cost), 0)                       AS filteredCost," +
            "  COALESCE(SUM(accumulatedDepreciationAmt), 0) AS filteredDepreciation," +
            "  COALESCE(SUM(netCost), 0)                    AS filteredNBV" +
            " FROM tb_FarReport" + where;

        Map<String, Object> agg = jdbcTemplate.queryForMap(aggregateSql, params.toArray());

        long       totalRecords         = ((Number) agg.get("cnt")).longValue();
        BigDecimal filteredCost         = toBigDecimal(agg.get("filteredCost"));
        BigDecimal filteredDepreciation = toBigDecimal(agg.get("filteredDepreciation"));
        BigDecimal filteredNBV          = toBigDecimal(agg.get("filteredNBV"));

        BigDecimal totalCost;
        BigDecimal totalNBV;
        BigDecimal totalDepreciation;

        if (!hasFilters) {
            totalCost         = filteredCost;
            totalNBV          = filteredNBV;
            totalDepreciation = filteredDepreciation;
        } else {
            Map<String, Object> totals = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(cost), 0)                       AS totalCost," +
                "       COALESCE(SUM(accumulatedDepreciationAmt), 0) AS totalDepreciation," +
                "       COALESCE(SUM(netCost), 0)                   AS totalNBV" +
                " FROM tb_FarReport");
            totalCost         = toBigDecimal(totals.get("totalCost"));
            totalDepreciation = toBigDecimal(totals.get("totalDepreciation"));
            totalNBV          = toBigDecimal(totals.get("totalNBV"));
        }

        Specification<FarReport> spec = SPEC_BUILDER.build(filter);
        Page<FarReportDTO> page = farReportRepository
                .findAll(spec, pageable)
                .map(mapper::toFarReportDto);

        // NBV spec check: NC = IC − AD (warn if discrepancy > 1% of total cost)
        BigDecimal specCheck = totalCost.subtract(totalDepreciation);
        BigDecimal diff      = totalNBV.subtract(specCheck).abs();
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal threshold = totalCost.multiply(BigDecimal.valueOf(0.01));
            if (diff.compareTo(threshold) > 0) {
                log.warn("[findAllWithSummary] NBV spec check: totalNBV={} vs (cost-AD)={} diff={} " +
                         "— may indicate unprocessed assets or large ADJ values",
                         totalNBV, specCheck, diff);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data",                 page.getContent());
        response.put("totalRecords",         totalRecords);
        response.put("totalPages",           (int) Math.ceil((double) totalRecords / pageable.getPageSize()));
        response.put("currentPage",          pageable.getPageNumber());
        response.put("pageSize",             pageable.getPageSize());
        response.put("totalCost",            totalCost);
        response.put("totalDepreciation",    totalDepreciation);
        response.put("totalNBV",             totalNBV);
        response.put("filteredCost",         filteredCost);
        response.put("filteredDepreciation", filteredDepreciation);
        response.put("filteredNBV",          filteredNBV);

        return response;
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "far-report:list", allEntries = true)
    @Transactional
    public Map<String, Object> processUpload(List<Map<String, Object>> rows, String source) {
        log.info("[FAR Upload] Starting {} upload, rows={}", source, rows.size());
        long startMs = System.currentTimeMillis();

        List<FarReport>           parsed    = new ArrayList<>(rows.size());
        List<Map<String, Object>> rowErrors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 1;
            try {
                ValidationResult validation = FarReportValidation.validateRow(rows.get(i), i);
                if (validation.hasErrors()) {
                    Map<String, Object> errorEntry = new HashMap<>();
                    errorEntry.put("rowNumber", rowNumber);
                    errorEntry.put("errors",    validation.errors);
                    rowErrors.add(errorEntry);
                    log.warn("[FAR Upload] Row {} rejected — {} error(s): {}",
                             rowNumber, validation.errors.size(), validation.errors);
                    continue;
                }
                FarReport entity = mapRowToEntity(rows.get(i));
                parsed.add(entity);
            } catch (Exception ex) {
                Map<String, Object> errorEntry = new HashMap<>();
                errorEntry.put("rowNumber", rowNumber);
                errorEntry.put("errors",    List.of("Row " + rowNumber + ": Parse error — " + ex.getMessage()));
                rowErrors.add(errorEntry);
                log.warn("[FAR Upload] Row {} parse failed: {}", rowNumber, ex.getMessage());
            }
        }

        Set<String> assetIds = parsed.stream()
                .map(FarReport::getAssetId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, FarReport> existingByAssetId = farReportRepository
                .findByAssetIdIn(assetIds)
                .stream()
                .collect(Collectors.toMap(FarReport::getAssetId, f -> f, (a, b) -> a));

        int inserted = 0, updated = 0;
        List<FarReport> batch = new ArrayList<>(UPLOAD_BATCH_SIZE);
        Date now = new Date();

        for (FarReport entity : parsed) {
            String assetId = entity.getAssetId();
            if (assetId == null || assetId.isBlank()) continue;

            FarReport existing = existingByAssetId.get(assetId);
            if (existing != null) {
                copyFieldsForUpdate(entity, existing);
                existing.setUpdatedDate(now);
                existing.setUpdatedBy(
                    entity.getUpdatedBy() != null ? entity.getUpdatedBy()
                    : (entity.getCreatedBy() != null ? entity.getCreatedBy() : existing.getCreatedBy())
                );
                existing.setChangedBy(existing.getUpdatedBy());
                existing.setChangedDate(now);
                batch.add(existing);
                updated++;
            } else {
                entity.setRecordDatetime(now);
                entity.setCreatedDate(now);
                entity.setInsertedBy(
                    entity.getCreatedBy() != null ? entity.getCreatedBy()
                    : (entity.getUpdatedBy() != null ? entity.getUpdatedBy() : "null")
                );
                if (entity.getCreatedBy() == null) {
                    entity.setCreatedBy(
                        entity.getUpdatedBy() != null ? entity.getUpdatedBy() : "null"
                    );
                }
                entity.setUpdatedBy(null);
                entity.setUpdatedDate(null);
                entity.setChangedBy(null);
                entity.setChangedDate(null);
                batch.add(entity);
                inserted++;
            }

            if (batch.size() >= UPLOAD_BATCH_SIZE) {
                flushBatch(batch);
            }
        }

        if (!batch.isEmpty()) {
            flushBatch(batch);
        }

        int  failed  = rowErrors.size();
        long elapsed = System.currentTimeMillis() - startMs;
        log.info("[FAR Upload] Complete: inserted={}, updated={}, failed={}, elapsed={}ms",
                 inserted, updated, failed, elapsed);

        refreshPreWarmExport();

        Map<String, Object> result = new HashMap<>();
        result.put("status",    failed == 0 ? "SUCCESS" : (inserted + updated == 0 ? "FAILED" : "PARTIAL"));
        result.put("inserted",  inserted);
        result.put("updated",   updated);
        result.put("failed",    failed);
        result.put("total",     rows.size());
        result.put("elapsedMs", elapsed);
        if (!rowErrors.isEmpty()) {
            result.put("rowErrors", rowErrors);
        }
        return result;
    }

    // ── Batch helpers ─────────────────────────────────────────────────────────

    private void flushBatch(List<FarReport> batch) {
        farReportRepository.saveAll(batch);
        entityManager.flush();
        entityManager.clear();
        batch.clear();
    }

    private void refreshPreWarmExport() {
        try {
            log.info("[FAR Upload] Refreshing pre-warmed FAR export");
            preWarmExportJob.warmSingle("far_report", ExportFormat.EXCEL);
        } catch (Exception ex) {
            log.error("[FAR Upload] Pre-warm refresh failed: {}", ex.getMessage(), ex);
        }
    }

    // ── Entity mapping from upload row ────────────────────────────────────────

    private FarReport mapRowToEntity(Map<String, Object> row) {
        FarReport e = new FarReport();
        e.setAssetId(getStr(row, "assetId"));
        e.setBook(getStr(row, "book"));
        e.setDescription(getStr(row, "description"));
        e.setSerialNumber(getStr(row, "serialNumber"));
        e.setAssetType(getStr(row, "assetType", "asset_type"));
        e.setTagNumber(getStr(row, "tagNumber"));
        e.setPicStatus(getStr(row, "picStatus"));
        e.setAssetStatus(getStr(row, "assetStatus"));
        e.setPartNumber(getStr(row, "partNumber"));
        e.setVendorName(getStr(row, "vendorName"));
        e.setVendorNumber(getStr(row, "vendorNumber"));
        e.setMergedCode(getStr(row, "mergedCode"));
        e.setCostAccount(getStr(row, "costAccount"));
        e.setAccumulatedDepreAccount(getStr(row, "accumulatedDepreAccount"));
        e.setCipCostAccount(getStr(row, "cipCostAccount"));
        e.setExpenseCostCenter(getStr(row, "expenseCostCenter"));
        e.setExpenseAccount(getStr(row, "expenseAccount"));
        e.setCategory(getStr(row, "category"));
        e.setCategoryDescription(getStr(row, "categoryDescription"));
        e.setLocationSegment1(getStr(row, "locationSegment1"));
        e.setLocationSegment2(getStr(row, "locationSegment2"));
        e.setLocationSegment3(getStr(row, "locationSegment3"));
        e.setLocationSegment4(getStr(row, "locationSegment4"));
        e.setLocations(getStr(row, "locations"));
        e.setStatusFlag(getStr(row, "statusFlag"));
        e.setNodeType(getStr(row, "nodeType"));
        e.setCreatedBy(getStr(row, "createdBy"));
        e.setUpdatedBy(getStr(row, "updatedBy"));
        e.setLinkId(getStr(row, "linkId"));
        e.setAcceptanceNumber(getStr(row, "acceptanceNumber"));
        e.setDepreciateFlag(getStr(row, "depreciateFlag"));
        e.setCipEu(getStr(row, "cipEu"));
        e.setInvoiceNumber(getStr(row, "invoiceNumber"));
        e.setPoNumber(getStr(row, "poNumber"));
        e.setPoLineNumber(getStr(row, "poLineNumber"));
        e.setUplLine(getStr(row, "uplLine"));
        e.setTransferToNewFar(getStr(row, "transferToNewFar"));
        e.setInsertedBy(getStr(row, "insertedBy"));
        e.setFinancialApproval(getStr(row, "financialApproval"));
        e.setChangedBy(getStr(row, "changedBy"));

        e.setQuantity(getInt(row, "quantity"));
        e.setLife(getInt(row, "life", "Life"));
        e.setSequenceNumber(getInt(row, "sequenceNumber"));

        e.setCost(getDbl(row, "cost"));
        e.setNbv(getDbl(row, "nbv"));
        e.setValue(getDbl(row, "value"));
        e.setDepreciationAmount(getDbl(row, "depreciationAmount"));
        e.setYtdDepreciation(getDbl(row, "ytdDepreciation"));
        e.setDepreciationReserve(getDbl(row, "depreciationReserve"));
        e.setSalvageValue(getDbl(row, "salvageValue"));

        e.setCreationDate(getDate(row, "creationDate"));
        e.setDatePlacedInService(getDate(row, "datePlacedInService"));
        e.setPicDate(getDate(row, "picDate"));
        e.setCipDeliveryDate(getDate(row, "cipDeliveryDate"));
        e.setCreatedDate(getDate(row, "createdDate"));
        e.setUpdatedDate(getDate(row, "updatedDate"));
        e.setChangedDate(getDate(row, "changedDate"));

        return e;
    }

    private void copyFieldsForUpdate(FarReport source, FarReport target) {
        target.setBook(source.getBook());
        target.setDescription(source.getDescription());
        target.setSerialNumber(source.getSerialNumber());
        target.setAssetType(source.getAssetType());
        target.setTagNumber(source.getTagNumber());
        target.setPicStatus(source.getPicStatus());
        target.setAssetStatus(source.getAssetStatus());
        target.setPartNumber(source.getPartNumber());
        target.setVendorName(source.getVendorName());
        target.setVendorNumber(source.getVendorNumber());
        target.setMergedCode(source.getMergedCode());
        target.setCostAccount(source.getCostAccount());
        target.setAccumulatedDepreAccount(source.getAccumulatedDepreAccount());
        target.setCipCostAccount(source.getCipCostAccount());
        target.setExpenseCostCenter(source.getExpenseCostCenter());
        target.setExpenseAccount(source.getExpenseAccount());
        target.setCategory(source.getCategory());
        target.setCategoryDescription(source.getCategoryDescription());
        target.setLocationSegment1(source.getLocationSegment1());
        target.setLocationSegment2(source.getLocationSegment2());
        target.setLocationSegment3(source.getLocationSegment3());
        target.setLocationSegment4(source.getLocationSegment4());
        target.setLocations(source.getLocations());
        target.setStatusFlag(source.getStatusFlag());
        target.setNodeType(source.getNodeType());
        target.setQuantity(source.getQuantity());
        target.setLife(source.getLife());
        target.setCost(source.getCost());
        target.setNbv(source.getNbv());
        target.setValue(source.getValue());
        target.setDepreciationAmount(source.getDepreciationAmount());
        target.setYtdDepreciation(source.getYtdDepreciation());
        target.setDepreciationReserve(source.getDepreciationReserve());
        target.setSalvageValue(source.getSalvageValue());
        target.setDatePlacedInService(source.getDatePlacedInService());
        target.setLinkId(source.getLinkId());
        target.setAcceptanceNumber(source.getAcceptanceNumber());
        target.setDepreciateFlag(source.getDepreciateFlag());
        target.setCipEu(source.getCipEu());
        target.setInvoiceNumber(source.getInvoiceNumber());
        target.setPoNumber(source.getPoNumber());
        target.setPoLineNumber(source.getPoLineNumber());
        target.setUplLine(source.getUplLine());
        target.setTransferToNewFar(source.getTransferToNewFar());
        target.setChangedBy(source.getChangedBy());
        target.setFinancialApproval(source.getFinancialApproval());
        // createdBy and createdDate are intentionally NOT copied on update
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

private LocalDateTime parseDate(String dateStr, boolean startOfDay) {

    if (dateStr == null || dateStr.isBlank()) {
        return null;
    }

    String trimmed = dateStr.trim();

    for (DateTimeFormatter formatter : DATE_PARSE_FORMATS) {

        try {
            LocalDateTime dt = LocalDateTime.parse(trimmed, formatter);
            return dt;
        }
        catch (DateTimeParseException ignored) {}

        try {
            LocalDate date = LocalDate.parse(trimmed, formatter);
            return startOfDay
                    ? date.atStartOfDay()
                    : date.atTime(23,59,59);
        }
        catch (DateTimeParseException ignored) {}
    }

    log.warn("[FarReportService] Could not parse date: {}", dateStr);
    return null;
}

private String validateColumn(String input) {
    if (input == null) {
        return null;
    }

    String normalized = input.trim();

    for (String allowed : ALLOWED_COLUMNS) {
        if (allowed.equalsIgnoreCase(normalized)) {
            return allowed;
        }
    }

    log.warn("[FarReportService] Invalid column requested: {}", input);
    return null;
}

private static BigDecimal toBigDecimal(Object value) {

    if (value == null) {
        return BigDecimal.ZERO;
    }

    if (value instanceof BigDecimal) {
        return (BigDecimal) value;
    }

    if (value instanceof Number) {
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    try {
        return new BigDecimal(value.toString());
    } catch (Exception e) {
        return BigDecimal.ZERO;
    }
}

    // ── Row-mapping helpers ───────────────────────────────────────────────────

    private String getStr(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object val = row.get(key);
            if (val != null) {
                String s = val.toString().trim();
                return s.isEmpty() ? null : s;
            }
        }
        return null;
    }

private Integer getInt(Map<String,Object> row, String... keys) {

    String val = getStr(row, keys);

    if (val == null) {
        return null;
    }

    try {
        return Integer.valueOf(val);
    }
    catch (Exception e) {
        try {
            return (int) Double.parseDouble(val);
        }
        catch (Exception ex) {
            return null;
        }
    }
}

private Double getDbl(Map<String,Object> row, String... keys) {

    String val = getStr(row, keys);

    if (val == null) {
        return null;
    }

    try {
        val = val.replace(",", "");
        return Double.parseDouble(val);
    }
    catch (Exception e) {
        return null;
    }
}

    /**
     * Parses a date field from an upload row map.
     * Uses thread-safe DateTimeFormatter instead of SimpleDateFormat.
     */
private Date getDate(Map<String,Object> row, String... keys) {

    Object raw = null;

    for (String key : keys) {
        raw = row.get(key);
        if (raw != null) break;
    }

    if (raw == null) {
        return null;
    }

    if (raw instanceof Date) {
        return (Date) raw;
    }

    if (raw instanceof LocalDate) {
        return java.sql.Date.valueOf((LocalDate) raw);
    }

    if (raw instanceof LocalDateTime) {
        return java.sql.Timestamp.valueOf((LocalDateTime) raw);
    }

    String val = raw.toString().trim();

    if (val.isEmpty()) {
        return null;
    }

    for (DateTimeFormatter formatter : DATE_PARSE_FORMATS) {

        try {
            LocalDateTime dt = LocalDateTime.parse(val, formatter);
            return java.sql.Timestamp.valueOf(dt);
        }
        catch (DateTimeParseException ignored) {}

        try {
            LocalDate date = LocalDate.parse(val, formatter);
            return java.sql.Date.valueOf(date);
        }
        catch (DateTimeParseException ignored) {}
    }

    log.warn("[FAR Upload] Invalid date value '{}'", val);

    return null;
}

}