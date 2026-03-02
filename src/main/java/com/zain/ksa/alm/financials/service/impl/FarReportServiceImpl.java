package com.zain.ksa.alm.financials.service.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
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
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
/**
 * Production-ready FAR report service.
 *
 * <h3>Upload workflow</h3>
 * <ol>
 *   <li>Validate rows, skip invalid</li>
 *   <li>Batch upsert in chunks of {@value UPLOAD_BATCH_SIZE}</li>
 *   <li>Evict FAR list cache</li>
 *   <li>Trigger pre-warm export refresh (async) so next download is instant</li>
 * </ol>
 *
 * <h3>Pre-warm trigger points</h3>
 * <p>FAR data only changes when:</p>
 * <ul>
 *   <li>A bulk upload is processed → {@link #processUpload} triggers pre-warm</li>
 *   <li>Depreciation scheduler runs → {@code DepreciationScheduler} triggers pre-warm</li>
 * </ul>
 */
@Service
public class FarReportServiceImpl implements FarReportService {

    private static final Logger log = LoggerFactory.getLogger(FarReportServiceImpl.class);

    /** Rows flushed per batch during upload. Matches Hibernate's jdbc.batch_size. */
    private static final int UPLOAD_BATCH_SIZE = 500;

    private final FarReportRepository farReportRepository;
    private final JdbcTemplate        jdbcTemplate;
    private final InventoryMapper     mapper;
    private final PreWarmExportJob    preWarmExportJob;

    @PersistenceContext
    private EntityManager entityManager;


            private static final GenericSpecificationBuilder<FarReport> SPEC_BUILDER =
        new GenericSpecificationBuilder<>("recordDatetime");

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

// ── List with Summary ─────────────────────────────────────────────────────


    @Override
@Cacheable(value = "far-report:list",
           key = "T(java.util.Objects).hash(#filter.columnName, #filter.searchQuery, " +
                 "#filter.filterBy, #filter.dateFrom, #filter.dateTo, " +
                 "#filter.isMapped, #filter.siteId, " +
                 "#pageable.pageNumber, #pageable.pageSize, #pageable.sort)")
@Transactional(readOnly = true)
public Map<String, Object> findAllWithSummary(DynamicFilterRequest filter, Pageable pageable) {

    // ── Build WHERE clause + params (mirrors FarReportServiceHelper exactly) ──
    StringBuilder where  = new StringBuilder(" WHERE 1=1");
    List<Object>  params = new ArrayList<>();

    if (filter.getColumnName() != null && !filter.getColumnName().isBlank()
            && filter.getSearchQuery() != null && !filter.getSearchQuery().isBlank()) {
        where.append(" AND LOWER(").append(filter.getColumnName()).append(") LIKE LOWER(?)");
        params.add("%" + filter.getSearchQuery() + "%");
    }
    if (filter.getFilterBy() != null) {
        for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                where.append(" AND ").append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
            }
        }
    }
    if (filter.getDateFrom() != null) {
        where.append(" AND recordDatetime >= ?");
        params.add(java.sql.Timestamp.valueOf(filter.getDateFrom()));
    }
    if (filter.getDateTo() != null) {
        where.append(" AND recordDatetime <= ?");
        params.add(java.sql.Timestamp.valueOf(filter.getDateTo()));
    }

    boolean hasFilters = !params.isEmpty();

    // ── Query 1: COUNT + filtered aggregates in one round-trip ───────────────
    // NOTE: uses netCost for NBV — matches the working legacy getFAR query
    String aggregateSql =
        "SELECT COUNT(*) AS cnt," +
        "  COALESCE(SUM(cost), 0)                       AS filteredCost," +
        "  COALESCE(SUM(netCost), 0)                    AS filteredNBV," +
        "  COALESCE(SUM(accumulatedDepreciationAmt), 0)  AS filteredDepreciation" +
        " FROM tb_FarReport" + where;

    Map<String, Object> agg = jdbcTemplate.queryForMap(aggregateSql, params.toArray());

    long       totalRecords         = ((Number) agg.get("cnt")).longValue();
    BigDecimal filteredCost         = toBigDecimal(agg.get("filteredCost"));
    BigDecimal filteredNBV          = toBigDecimal(agg.get("filteredNBV"));
    BigDecimal filteredDepreciation = toBigDecimal(agg.get("filteredDepreciation"));

    // ── Query 2 (conditional): whole-table totals ─────────────────────────────
    BigDecimal totalCost;
    BigDecimal totalNBV;
    BigDecimal totalDepreciation;

    if (!hasFilters) {
        // No filter — filtered == total, skip second query
        totalCost         = filteredCost;
        totalNBV          = filteredNBV;
        totalDepreciation = filteredDepreciation;
    } else {
        Map<String, Object> totals = jdbcTemplate.queryForMap(
            "SELECT COALESCE(SUM(cost), 0)                     AS totalCost," +
            "  COALESCE(SUM(netCost), 0)                       AS totalNBV," +
            "  COALESCE(SUM(accumulatedDepreciationAmt), 0)     AS totalDepreciation" +
            " FROM tb_FarReport");
        totalCost         = toBigDecimal(totals.get("totalCost"));
        totalNBV          = toBigDecimal(totals.get("totalNBV"));
        totalDepreciation = toBigDecimal(totals.get("totalDepreciation"));
    }

    // ── Query 3: paged data via JPA ───────────────────────────────────────────
    Specification<FarReport> spec = SPEC_BUILDER.build(filter);
    Page<FarReportDTO> page = farReportRepository
            .findAll(spec, pageable)
            .map(mapper::toFarReportDto);

    // ── Response ──────────────────────────────────────────────────────────────
    Map<String, Object> response = new HashMap<>();
    response.put("data",                 page.getContent());
    response.put("totalRecords",         totalRecords);
    response.put("totalPages",           (int) Math.ceil((double) totalRecords / pageable.getPageSize()));
    response.put("currentPage",          pageable.getPageNumber());
    response.put("pageSize",             pageable.getPageSize());
    response.put("filteredCost",         filteredCost);
    response.put("filteredNBV",          filteredNBV);
    response.put("filteredDepreciation", filteredDepreciation);
    response.put("totalCost",            totalCost);
    response.put("totalNBV",             totalNBV);
    response.put("totalDepreciation",    totalDepreciation);
    return response;
}

private static BigDecimal toBigDecimal(Object val) {
    if (val == null) return BigDecimal.ZERO;
    if (val instanceof BigDecimal) return (BigDecimal) val;
    return new BigDecimal(val.toString());
}
    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Processes a bulk upload of FAR records.
     *
     * <p>Strategy:</p>
     * <ul>
     *   <li>Maps each row to a FarReport entity</li>
     *   <li>Batch-persists in chunks of {@value UPLOAD_BATCH_SIZE}</li>
     *   <li>Evicts the FAR list cache</li>
     *   <li>Refreshes the pre-warmed FAR export (async)</li>
     * </ul>
     */
    @Override
    @CacheEvict(value = "far-report:list", allEntries = true)
    @Transactional
    public Map<String, Object> processUpload(List<Map<String, Object>> rows, String source) {
        log.info("[FAR Upload] Starting {} upload, rows={}", source, rows.size());
        long startMs = System.currentTimeMillis();

        int inserted = 0;
        int updated  = 0;
        int failed   = 0;

        List<FarReport> batch = new ArrayList<>(UPLOAD_BATCH_SIZE);

        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, Object> row = rows.get(i);
                FarReport entity = mapRowToEntity(row);

                // Upsert: check if asset already exists
                String assetId = entity.getAssetId();
                if (assetId != null && !assetId.isBlank()) {
                    List<FarReport> existing = farReportRepository.findByAssetId(assetId);
                    if (!existing.isEmpty()) {
                        // Update the first match
                        FarReport target = existing.get(0);
                        copyFieldsForUpdate(entity, target);
                        batch.add(target);
                        updated++;
                    } else {
                        entity.setRecordDatetime(new Date());
                        batch.add(entity);
                        inserted++;
                    }
                } else {
                    entity.setRecordDatetime(new Date());
                    batch.add(entity);
                    inserted++;
                }

                // Flush batch when full
                if (batch.size() >= UPLOAD_BATCH_SIZE) {
                    flushBatch(batch);
                }

            } catch (Exception ex) {
                failed++;
                log.warn("[FAR Upload] Row {} failed: {}", i, ex.getMessage());
            }
        }

        // Flush remaining
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info("[FAR Upload] Complete: inserted={}, updated={}, failed={}, elapsed={}ms",
                 inserted, updated, failed, elapsedMs);

        // Refresh pre-warmed export in background — async, non-blocking
        refreshPreWarmExport();

        Map<String, Object> result = new HashMap<>();
        result.put("status", failed == 0 ? "SUCCESS" : "PARTIAL");
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("total", rows.size());
        result.put("elapsedMs", elapsedMs);
        return result;
    }

    /**
     * Flushes a batch to the database and clears the persistence context
     * to prevent L1 cache growth during large uploads.
     */
    private void flushBatch(List<FarReport> batch) {
        farReportRepository.saveAll(batch);
        entityManager.flush();
        entityManager.clear();
        batch.clear();
    }

    /**
     * Refreshes the pre-warmed FAR export. Isolated in try-catch so a
     * pre-warm failure never affects the upload transaction.
     */
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

    /**
     * Copies mutable fields from source to target for update.
     * Does not overwrite recordNo or recordDatetime.
     */
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
        target.setUpdatedBy(source.getUpdatedBy());
        target.setUpdatedDate(new Date());
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
        target.setChangedDate(new Date());
    }

    // ── Legacy CSV export ─────────────────────────────────────────────────────

    private static final String[] COLUMNS = {
        "recordNo", "recordDatetime", "book", "assetId", "quantity",
        "description", "asset_type", "creationDate", "serialNumber", "tagNumber",
        "picStatus", "picDate", "cipDeliveryDate", "linkId", "acceptanceNumber",
        "depreciateFlag", "cipEu", "invoiceNumber", "poNumber", "poLineNumber",
        "uplLine", "transferToNewFar", "assetStatus", "value", "partNumber",
        "vendorName", "vendorNumber", "mergedCode", "costAccount",
        "accumulatedDepreAccount", "cipCostAccount", "expenseCostCenter",
        "expenseAccount", "Life", "datePlacedInService", "cost", "nbv",
        "depreciationAmount", "ytdDepreciation", "depreciationReserve",
        "salvageValue", "category", "categoryDescription", "locationSegment1",
        "locationSegment2", "locationSegment3", "locationSegment4", "locations",
        "sequenceNumber", "createdBy", "createdDate", "updatedBy", "updatedDate",
        "monthlyDepreciationAmt", "accumulatedDepreciationAmt", "depreciationDate",
        "netCost", "statusFlag", "changedBy", "insertedBy", "financialApproval",
        "changedDate", "nodeType"
    };

    private static final List<String> ALLOWED_COLUMNS = Arrays.asList(COLUMNS);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(readOnly = true)
    public void streamExportToCsv(PrintWriter writer, String column, String value,
                                   String operator) throws IOException {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(String.join(", ", COLUMNS));
        sql.append(" FROM tb_FarReport WHERE 1=1");

        List<Object> params = new ArrayList<>();

        if (column != null && value != null && ALLOWED_COLUMNS.contains(column)) {
            if ("contains".equalsIgnoreCase(operator)) {
                sql.append(" AND ").append(column).append(" LIKE ?");
                params.add("%" + value + "%");
            } else {
                sql.append(" AND ").append(column).append(" = ?");
                params.add(value);
            }
        }

        writer.println(String.join(",", COLUMNS));

        jdbcTemplate.query(sql.toString(), params.toArray(), new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                StringBuilder row = new StringBuilder();
                for (String col : COLUMNS) {
                    Object val = rs.getObject(col);
                    String strVal;
                    if (val == null) {
                        strVal = "";
                    } else if (val instanceof Date) {
                        strVal = dateFormat.format((Date) val);
                    } else {
                        strVal = val.toString();
                    }
                    row.append(escapeCsv(strVal)).append(",");
                }
                writer.println(row.substring(0, row.length() - 1));
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Get string value, trying multiple key variants (for column name inconsistencies). */
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

    private Integer getInt(Map<String, Object> row, String... keys) {
        String val = getStr(row, keys);
        if (val == null) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            try {
                return (int) Double.parseDouble(val); // handles "5.0"
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }

    private Double getDbl(Map<String, Object> row, String... keys) {
        String val = getStr(row, keys);
        if (val == null) return null;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Date getDate(Map<String, Object> row, String... keys) {
        String val = getStr(row, keys);
        if (val == null) return null;
        try {
            return dateFormat.parse(val);
        } catch (Exception e) {
            return null;
        }
    }
}