package com.zain.ksa.alm.financials.service.impl;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.service.export.FileExportStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * FIXED VERSION: Streaming export executor with unified filtering for all inventory types.
 *
 * KEY FIXES:
 * 1. Unmapped inventories now use dedicated WHERE clause builders (was missing entirely)
 * 2. Filters applied in SQL WHERE clause (before streaming)
 * 3. Sequence numbers support (1-based, continuous across sheets)
 * 4. Type-aware filtering for strings, dates, integers, numerics
 * 5. Column name validation (whitelist to prevent SQL injection)
 */
@Component
public class ExportExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExportExecutor.class);
    private static final int PROGRESS_INTERVAL = 5_000;

    // ══════════════════════════════════════════════════════════════════════════
    // FAR REPORT COLUMNS
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] FAR_COLUMNS = {
        "recordNo", "recordDatetime", "book", "assetId", "quantity",
        "description", "creationDate", "serialNumber", "tagNumber",
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

    private static final String[] FAR_HEADERS = {
        "Record No", "Record Datetime", "Book", "Asset ID", "Quantity",
        "Description", "Creation Date", "Serial Number", "Tag Number",
        "PIC Status", "PIC Date", "CIP Delivery Date", "Link ID", "Acceptance Number",
        "Depreciate Flag", "CIP EU", "Invoice Number", "PO Number", "PO Line Number",
        "UPL Line", "Transfer To New FAR", "Asset Status", "Value", "Part Number",
        "Vendor Name", "Vendor Number", "Merged Code", "Cost Account",
        "Accumulated Depre Account", "CIP Cost Account", "Expense Cost Center",
        "Expense Account", "Life", "Date Placed In Service", "Cost", "NBV",
        "Depreciation Amount", "YTD Depreciation", "Depreciation Reserve",
        "Salvage Value", "Category", "Category Description", "Location Segment 1",
        "Location Segment 2", "Location Segment 3", "Location Segment 4", "Locations",
        "Sequence Number", "Created By", "Created Date", "Updated By", "Updated Date",
        "Monthly Depreciation Amt", "Accumulated Depreciation Amt", "Depreciation Date",
        "Net Cost", "Status Flag", "Changed By", "Inserted By", "Financial Approval",
        "Changed Date", "Node Type"
    };

    private static final Set<String> FAR_DATE_COLUMNS = Set.of(
        "recordDatetime", "creationDate", "picDate", "cipDeliveryDate",
        "datePlacedInService", "createdDate", "updatedDate",
        "depreciationDate", "changedDate"
    );

    private static final Set<String> FAR_NUMERIC_COLUMNS = Set.of(
        "value", "cost", "nbv", "depreciationAmount", "ytdDepreciation",
        "depreciationReserve", "salvageValue", "monthlyDepreciationAmt",
        "accumulatedDepreciationAmt", "netCost"
    );

    private static final Set<String> FAR_INTEGER_COLUMNS = Set.of(
        "recordNo", "quantity", "Life", "sequenceNumber"
    );

    private static final Set<String> FAR_ALLOWED_COLUMNS;
    static {
        FAR_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(FAR_COLUMNS));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DEPRECIATION COLUMNS
    // ══════════════════════════════════════════════════════════════════════════

private static final String[] DEP_COLUMNS = {
    "recordNo", "recordDatetime", "book", "assetId", "depreciationPeriod",
    "quantity", "description", "serialNumber", "assetType", "tagNumber",
    "picStatus", "picDate", "cipDeliveryDate", "linkId", "acceptanceNumber",
    "depreciateFlag", "cipEu", "invoiceNumber", "poNumber", "poLineNumber",
    "uplLine", "transferToNewFar", "assetStatus", "value", "partNumber",
    "vendorName", "vendorNumber", "mergedCode", "costAccount",
    "accumulatedDepreAccount", "cipCostAccount", "expenseCostCenter",
    "expenseAccount", "life", "datePlacedInService", "cost", "nbv",
    "depreciationAmount", "ytdDepreciation", "depreciationReserve",
    "salvageValue", "category", "categoryDescription", "locationSegment1",
    "locationSegment2", "locationSegment3", "locationSegment4", "locations",
    "sequenceNumber", "monthlyDepreciationAmt", "accumulatedDepreciationAmt",
    "depreciationDate", "netCost", "statusFlag", "changedBy", "insertedBy",
    "financialApproval", "changedDate", "nodeType", "createdBy", "updatedBy", "mapped"
};

private static final String[] DEP_HEADERS = {
    "Record No", "Record Datetime", "Book", "Asset ID", "Depreciation Period",
    "Quantity", "Description", "Serial Number", "Asset Type", "Tag Number",
    "PIC Status", "PIC Date", "CIP Delivery Date", "Link ID", "Acceptance Number",
    "Depreciate Flag", "CIP EU", "Invoice Number", "PO Number", "PO Line Number",
    "UPL Line", "Transfer To New FAR", "Asset Status", "Value", "Part Number",
    "Vendor Name", "Vendor Number", "Merged Code", "Cost Account",
    "Accumulated Depre Account", "CIP Cost Account", "Expense Cost Center",
    "Expense Account", "Life", "Date Placed In Service", "Cost", "NBV",
    "Depreciation Amount", "YTD Depreciation", "Depreciation Reserve",
    "Salvage Value", "Category", "Category Description", "Location Segment 1",
    "Location Segment 2", "Location Segment 3", "Location Segment 4", "Locations",
    "Sequence Number", "Monthly Depreciation Amt", "Accumulated Depreciation Amt",
    "Depreciation Date", "Net Cost", "Status Flag", "Changed By", "Inserted By",
    "Financial Approval", "Changed Date", "Node Type", "Created By", "Updated By", "Mapped"
};

private static final Set<String> DEP_ALLOWED_COLUMNS;
static {
    DEP_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(DEP_COLUMNS));
}


    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED ACTIVE INVENTORY (FIX: Separate DB columns from output columns)
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] UNMAPPED_ACTIVE_DB_COLUMNS = {
        "recordNo", "recordDateTime", "nodeId", "nodeName", "nodeType",
        "serialNumber", "model", "partNumber", "siteId", "manufacturer",
        "description", "manufacturingDate", "installationDate", "assetInsertionDate", "warranty"
    };

    private static final String[] UNMAPPED_ACTIVE_COLUMNS = {
        "sequenceNo",  // ← Computed, position 0
        "recordNo", "recordDateTime", "nodeId", "nodeName", "nodeType",
        "serialNumber", "model", "partNumber", "siteId", "manufacturer",
        "description", "manufacturingDate", "installationDate", "assetInsertionDate", "warranty"
    };

    private static final String[] UNMAPPED_ACTIVE_HEADERS = {
        "Sequence",
        "Record No", "Record DateTime", "Node ID", "Node Name", "Node Type",
        "Serial Number", "Model", "Part Number", "Site ID", "Manufacturer",
        "Description", "Manufacturing Date", "Installation Date", "Asset Insertion Date", "Warranty"
    };

    private static final Set<String> UNMAPPED_ACTIVE_ALLOWED_COLUMNS;
    static {
        UNMAPPED_ACTIVE_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(UNMAPPED_ACTIVE_DB_COLUMNS));
    }

    private static final Set<String> UNMAPPED_ACTIVE_DATE_COLUMNS = Set.of(
        "recordDateTime", "manufacturingDate", "installationDate", "assetInsertionDate"
    );

    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED PASSIVE INVENTORY
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] UNMAPPED_PASSIVE_DB_COLUMNS = {
        "id", "recordDateTime", "inventoryId", "objectId", "parentName",
        "siteId", "itemBarCode", "serialNumber", "model", "note",
        "part", "entryUser", "entryDate", "itemStatus", "categoryInNEP",
        "scrapStatus", "inventoryType", "inventoryTypeId", "locationSubType",
        "locationClassification", "itemClassification", "itemClassification2", "notes", "prPoNo"
    };

    private static final String[] UNMAPPED_PASSIVE_COLUMNS = {
        "sequenceNo",
        "id", "recordDateTime", "inventoryId", "objectId", "parentName",
        "siteId", "itemBarCode", "serialNumber", "model", "note",
        "part", "entryUser", "entryDate", "itemStatus", "categoryInNEP",
        "scrapStatus", "inventoryType", "inventoryTypeId", "locationSubType",
        "locationClassification", "itemClassification", "itemClassification2", "notes", "prPoNo"
    };

    private static final String[] UNMAPPED_PASSIVE_HEADERS = {
        "Sequence",
        "ID", "Record DateTime", "Inventory ID", "Object ID", "Parent Name",
        "Site ID", "Item Bar Code", "Serial Number", "Model", "Note",
        "Part", "Entry User", "Entry Date", "Item Status", "Category In NEP",
        "Scrap Status", "Inventory Type", "Inventory Type ID", "Location Sub Type",
        "Location Classification", "Item Classification", "Item Classification 2", "Notes", "PR/PO No"
    };

    private static final Set<String> UNMAPPED_PASSIVE_ALLOWED_COLUMNS;
    static {
        UNMAPPED_PASSIVE_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(UNMAPPED_PASSIVE_DB_COLUMNS));
    }

    private static final Set<String> UNMAPPED_PASSIVE_DATE_COLUMNS = Set.of(
        "recordDateTime"
    );

    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED IT INVENTORY
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] UNMAPPED_IT_DB_COLUMNS = {
        "id", "recordDatetime", "objectId", "siteId", "hostSerialNumber",
        "inventoryTypeId", "inventoryType", "hostTypeName", "firstScan",
        "ipAddress", "osId", "osName", "hardwareVendorId", "hardwareVendorName",
        "model", "isVirtual", "hostTypeId", "category"
    };

    private static final String[] UNMAPPED_IT_COLUMNS = {
        "sequenceNo",
        "id", "recordDatetime", "objectId", "siteId", "hostSerialNumber",
        "inventoryTypeId", "inventoryType", "hostTypeName", "firstScan",
        "ipAddress", "osId", "osName", "hardwareVendorId", "hardwareVendorName",
        "model", "isVirtual", "hostTypeId", "category"
    };

    private static final String[] UNMAPPED_IT_HEADERS = {
        "Sequence",
        "ID", "Record Datetime", "Object ID", "Site ID", "Host Serial Number",
        "Inventory Type ID", "Inventory Type", "Host Type Name", "First Scan",
        "IP Address", "OS ID", "OS Name", "Hardware Vendor ID", "Hardware Vendor Name",
        "Model", "Is Virtual", "Host Type ID", "Category"
    };

    private static final Set<String> UNMAPPED_IT_ALLOWED_COLUMNS;
    static {
        UNMAPPED_IT_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(UNMAPPED_IT_DB_COLUMNS));
    }

    private static final Set<String> UNMAPPED_IT_DATE_COLUMNS = Set.of(
        "recordDatetime"
    );

    // ══════════════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ══════════════════════════════════════════════════════════════════════════

    private final JdbcTemplate jdbcTemplate;
    private final FileExportStrategy fileExportStrategy;

    @Value("${app.export.max-threads:3}")
    private int maxThreads;

    @Value("${app.export.dir:${java.io.tmpdir}/alm-exports}")
    private String exportDir;

    private ExecutorService threadPool;
    private Semaphore concurrencyGuard;

    public ExportExecutor(JdbcTemplate jdbcTemplate, FileExportStrategy fileExportStrategy) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileExportStrategy = fileExportStrategy;
    }

    @PostConstruct
    public void init() {
        AtomicInteger counter = new AtomicInteger(0);
        this.threadPool = new ThreadPoolExecutor(
            1, maxThreads, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10),
            r -> {
                Thread t = new Thread(r, "export-worker-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.concurrencyGuard = new Semaphore(maxThreads);

        File dir = new File(exportDir);
        if (!dir.exists()) dir.mkdirs();

        log.info("[ExportExecutor] Initialized: maxThreads={}, exportDir={}", maxThreads, exportDir);
    }

    @PreDestroy
    public void shutdown() {
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STREAMING PREPAREDSTATEMENT
    // ══════════════════════════════════════════════════════════════════════════

    private PreparedStatementCreator streamingStatement(String sql, List<Object> params) {
        return conn -> {
            var ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Integer.MIN_VALUE);
            ps.setFetchDirection(ResultSet.FETCH_FORWARD);
            int idx = 1;
            for (Object param : params) ps.setObject(idx++, param);
            return ps;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    public void execute(String jobId, String exportType, DynamicFilterRequest filter,
                        ExportFormat format, String filePath,
                        BiConsumer<Long, Long> progressCallback,
                        Consumer<ExportResult> resultCallback) {

        threadPool.submit(() -> {
            boolean acquired = false;
            try {
                acquired = concurrencyGuard.tryAcquire(30, TimeUnit.SECONDS);
                if (!acquired) {
                    resultCallback.accept(ExportResult.failure("Too many concurrent exports. Try again shortly."));
                    return;
                }

                long startMs = System.currentTimeMillis();
                log.info("[Export:{}] Starting type={}, format={}", jobId, exportType, format);

                switch (exportType) {
                    case "far_report" -> exportFarReport(jobId, filter, format, filePath, progressCallback);
                    case "depreciation" -> exportDepreciation(jobId, filter, format, filePath, progressCallback);
                    case "unmapped_active" -> exportUnmappedActive(jobId, filter, format, filePath, progressCallback);
                    case "unmapped_passive" -> exportUnmappedPassive(jobId, filter, format, filePath, progressCallback);
                    case "unmapped_it" -> exportUnmappedIT(jobId, filter, format, filePath, progressCallback);
                    default -> throw new IllegalArgumentException("Unknown export type: " + exportType);
                }

                long elapsed = System.currentTimeMillis() - startMs;
                log.info("[Export:{}] Completed in {}ms", jobId, elapsed);
                resultCallback.accept(ExportResult.success(filePath));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                resultCallback.accept(ExportResult.failure("Export interrupted: " + e.getMessage()));
            } catch (Exception e) {
                log.error("[Export:{}] Failed: {}", jobId, e.getMessage(), e);
                resultCallback.accept(ExportResult.failure(e.getMessage()));
            } finally {
                if (acquired) concurrencyGuard.release();
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FAR REPORT EXPORT
    // ══════════════════════════════════════════════════════════════════════════

    private void exportFarReport(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                  String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where = buildFarWhereClause(filter);
        long totalRows = countWithWhere("tb_FarReport", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] FAR total rows (filtered): {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(FAR_HEADERS, FAR_COLUMNS, FAR_DATE_COLUMNS,
                FAR_NUMERIC_COLUMNS, FAR_INTEGER_COLUMNS, null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", FAR_COLUMNS) + " FROM tb_FarReport" + where.sql + " ORDER BY recordNo ASC";

        fileExportStrategy.exportStreaming(FAR_HEADERS, FAR_COLUMNS, FAR_DATE_COLUMNS,
            FAR_NUMERIC_COLUMNS, FAR_INTEGER_COLUMNS,
            callback -> jdbcTemplate.query(streamingStatement(sql, where.params), 
                (ResultSet rs) -> callback.onRow(rs)),
            filePath, format, totalRows, progressCallback);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DEPRECIATION EXPORT
    // ══════════════════════════════════════════════════════════════════════════

private void exportDepreciation(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                 String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {

    SqlFragment where = buildDepreciationWhereClause(filter);
    long totalRows = countWithWhere("tb_DepreciationHistory", where);
    progressCallback.accept(totalRows, 0L);
    log.info("[Export:{}] Depreciation total rows (filtered): {}", jobId, totalRows);

    Set<String> depDateCols = Set.of("depreciationDate", "recordDatetime", "picDate", "cipDeliveryDate",
        "datePlacedInService", "changedDate");
    Set<String> depNumericCols = Set.of("cost", "nbv", "depreciationAmount",
        "ytdDepreciation", "depreciationReserve", "salvageValue",
        "accumulatedDepreciationAmt", "monthlyDepreciationAmt", "netCost", "value");
    Set<String> depIntCols = Set.of("recordNo", "quantity", "life", "sequenceNumber");

    String sql = "SELECT " + String.join(", ", DEP_COLUMNS)
               + " FROM tb_DepreciationHistory" + where.sql + " ORDER BY recordNo ASC";

    log.info("[Export:{}] Opening depreciation streaming cursor...", jobId);

    fileExportStrategy.exportStreaming(
        DEP_HEADERS, DEP_COLUMNS, depDateCols, depNumericCols, depIntCols,
        callback -> {
            jdbcTemplate.query(
                streamingStatement(sql, where.params),
                (ResultSet rs) -> { callback.onRow(rs); }
            );
            log.info("[Export:{}] Depreciation streaming cursor closed", jobId);
        },
        filePath, format, totalRows, progressCallback
    );
}
    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED ACTIVE EXPORT (FIX: With filtering + sequence)
    // ══════════════════════════════════════════════════════════════════════════

    private void exportUnmappedActive(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                       String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where = buildUnmappedActiveWhereClause(filter);
        long totalRows = countWithWhere("tb_unmapped_active_inventory", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] Unmapped Active total rows (filtered): {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(UNMAPPED_ACTIVE_HEADERS, UNMAPPED_ACTIVE_COLUMNS,
                UNMAPPED_ACTIVE_DATE_COLUMNS, Set.of(), Set.of(), null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", UNMAPPED_ACTIVE_DB_COLUMNS)
                   + " FROM tb_unmapped_active_inventory" + where.sql + " ORDER BY recordNo ASC";

        AtomicLong sequenceCounter = new AtomicLong(0);

        fileExportStrategy.exportStreamingWithSequence(UNMAPPED_ACTIVE_HEADERS, UNMAPPED_ACTIVE_DB_COLUMNS,
            UNMAPPED_ACTIVE_DATE_COLUMNS, Set.of(), Set.of(),
            callback -> jdbcTemplate.query(streamingStatement(sql, where.params),
                (ResultSet rs) -> callback.onRow(rs)),
            sequenceCounter, filePath, format, totalRows, progressCallback);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED PASSIVE EXPORT (FIX: With filtering + sequence)
    // ══════════════════════════════════════════════════════════════════════════

    private void exportUnmappedPassive(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                        String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where = buildUnmappedPassiveWhereClause(filter);
        long totalRows = countWithWhere("tb_unmapped_passive_inventory", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] Unmapped Passive total rows (filtered): {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(UNMAPPED_PASSIVE_HEADERS, UNMAPPED_PASSIVE_COLUMNS,
                UNMAPPED_PASSIVE_DATE_COLUMNS, Set.of(), Set.of(), null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", UNMAPPED_PASSIVE_DB_COLUMNS)
                   + " FROM tb_unmapped_passive_inventory" + where.sql + " ORDER BY id ASC";

        AtomicLong sequenceCounter = new AtomicLong(0);

        fileExportStrategy.exportStreamingWithSequence(UNMAPPED_PASSIVE_HEADERS, UNMAPPED_PASSIVE_DB_COLUMNS,
            UNMAPPED_PASSIVE_DATE_COLUMNS, Set.of(), Set.of(),
            callback -> jdbcTemplate.query(streamingStatement(sql, where.params),
                (ResultSet rs) -> callback.onRow(rs)),
            sequenceCounter, filePath, format, totalRows, progressCallback);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED IT EXPORT (FIX: With filtering + sequence)
    // ══════════════════════════════════════════════════════════════════════════

    private void exportUnmappedIT(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                   String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where = buildUnmappedITWhereClause(filter);
        long totalRows = countWithWhere("tb_unmapped_IT_Inventory", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] Unmapped IT total rows (filtered): {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(UNMAPPED_IT_HEADERS, UNMAPPED_IT_COLUMNS,
                UNMAPPED_IT_DATE_COLUMNS, Set.of(), Set.of(), null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", UNMAPPED_IT_DB_COLUMNS)
                   + " FROM tb_unmapped_IT_Inventory" + where.sql + " ORDER BY id ASC";

        AtomicLong sequenceCounter = new AtomicLong(0);

        fileExportStrategy.exportStreamingWithSequence(UNMAPPED_IT_HEADERS, UNMAPPED_IT_DB_COLUMNS,
            UNMAPPED_IT_DATE_COLUMNS, Set.of(), Set.of(),
            callback -> jdbcTemplate.query(streamingStatement(sql, where.params),
                (ResultSet rs) -> callback.onRow(rs)),
            sequenceCounter, filePath, format, totalRows, progressCallback);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WHERE CLAUSE BUILDERS
    // ══════════════════════════════════════════════════════════════════════════

    private SqlFragment buildFarWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);

        if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
            String searchTerm = filter.getSearchQuery().trim();
            if ("__global__".equals(filter.getColumnName())) {
                sql.append(" AND (").append(buildGlobalSearchOr(FAR_COLUMNS, FAR_DATE_COLUMNS,
                    FAR_NUMERIC_COLUMNS, FAR_INTEGER_COLUMNS, params, searchTerm)).append(")");
            } else {
                String col = validateColumn(filter.getColumnName(), FAR_ALLOWED_COLUMNS);
                if (col != null) appendColumnSearch(sql, params, col, searchTerm, FAR_DATE_COLUMNS,
                    FAR_NUMERIC_COLUMNS, FAR_INTEGER_COLUMNS);
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), FAR_ALLOWED_COLUMNS);
                    if (col != null) {
                        sql.append(" AND ").append(col).append(" = ?");
                        params.add(entry.getValue());
                    }
                }
            }
        }

if (notBlank(filter.getDateFrom())) {
    LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
    if (from != null) {
        sql.append(" AND recordDateTime >= ?");
        params.add(Timestamp.valueOf(from));
    }
}
if (notBlank(filter.getDateTo())) {
    LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
    if (to != null) {
        sql.append(" AND recordDateTime <= ?");
        params.add(Timestamp.valueOf(to));
    }
}

        return new SqlFragment(sql.toString(), params);
    }

private SqlFragment buildDepreciationWhereClause(DynamicFilterRequest filter) {
    StringBuilder sql = new StringBuilder(" WHERE 1=1");
    List<Object> params = new ArrayList<>();
    if (filter == null) return new SqlFragment(sql.toString(), params);

    // Single-column search
    if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
        String searchTerm = filter.getSearchQuery().trim();
        String col = validateColumn(filter.getColumnName(), DEP_ALLOWED_COLUMNS);
        if (col != null) {
            appendColumnSearch(sql, params, col, searchTerm,
                Set.of("depreciationDate", "recordDatetime", "picDate", "cipDeliveryDate",
                       "datePlacedInService", "changedDate", "createdDate", "updatedDate"),
                Set.of("cost", "nbv", "depreciationAmount", "ytdDepreciation",
                       "depreciationReserve", "salvageValue",
                       "accumulatedDepreciationAmt", "monthlyDepreciationAmt", "netCost", "value"),
                Set.of("recordNo", "quantity", "life", "sequenceNumber"));  // ← FIXED
        }
    }

    // Column exact filters
    if (filter.getFilterBy() != null) {
        for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
            if (notBlank(entry.getValue())) {
                String col = validateColumn(entry.getKey(), DEP_ALLOWED_COLUMNS);
                if (col != null) {
                    sql.append(" AND ").append(col).append(" = ?");
                    params.add(entry.getValue());
                }
            }
        }
    }
   if (notBlank(filter.getDateFrom())) {
        LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
        if (from != null) {
            sql.append(" AND depreciationDate >= ?");
            params.add(Timestamp.valueOf(from));
        }
    }
    if (notBlank(filter.getDateTo())) {
        LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
        if (to != null) {
            sql.append(" AND depreciationDate <= ?");
            params.add(Timestamp.valueOf(to));
        }
    }

    return new SqlFragment(sql.toString(), params);
}

    private SqlFragment buildUnmappedActiveWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);
if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
    String searchTerm = filter.getSearchQuery().trim();
    if ("__global__".equals(filter.getColumnName())) {
        Set<String> depDateCols = Set.of("depreciationDate", "recordDatetime", "picDate",
            "cipDeliveryDate", "datePlacedInService", "changedDate", "createdDate", "updatedDate");
        Set<String> depNumericCols = Set.of("cost", "nbv", "depreciationAmount",
            "ytdDepreciation", "depreciationReserve", "salvageValue",
            "accumulatedDepreciationAmt", "monthlyDepreciationAmt", "netCost", "value");
        Set<String> depIntCols = Set.of("recordNo", "quantity", "life", "sequenceNumber");
        sql.append(" AND (").append(buildGlobalSearchOr(DEP_COLUMNS, depDateCols,
            depNumericCols, depIntCols, params, searchTerm)).append(")");
    } else {
        String col = validateColumn(filter.getColumnName(), DEP_ALLOWED_COLUMNS);
        if (col != null) {
            appendColumnSearch(sql, params, col, searchTerm,
                Set.of("depreciationDate", "recordDatetime", "picDate", "cipDeliveryDate",
                       "datePlacedInService", "changedDate", "createdDate", "updatedDate"),
                Set.of("cost", "nbv", "depreciationAmount", "ytdDepreciation",
                       "depreciationReserve", "salvageValue",
                       "accumulatedDepreciationAmt", "monthlyDepreciationAmt", "netCost", "value"),
                Set.of("recordNo", "quantity", "life", "sequenceNumber"));
        }
    }
}

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), UNMAPPED_ACTIVE_ALLOWED_COLUMNS);
                    if (col != null) {
                        sql.append(" AND ").append(col).append(" = ?");
                        params.add(entry.getValue());
                    }
                }
            }
        }

       if (notBlank(filter.getDateFrom())) {
           LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
           if (from != null) {
               sql.append(" AND recordDatetime >= ?");
               params.add(Timestamp.valueOf(from));
           }
       }
       if (notBlank(filter.getDateTo())) {
           LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
           if (to != null) {
               sql.append(" AND recordDatetime <= ?");
               params.add(Timestamp.valueOf(to));
           }
       }

        if (notBlank(filter.getSiteId())) {
            sql.append(" AND siteId = ?");
            params.add(filter.getSiteId());
        }

        return new SqlFragment(sql.toString(), params);
    }

    private SqlFragment buildUnmappedPassiveWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);

        if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
            String searchTerm = filter.getSearchQuery().trim();
            if ("__global__".equals(filter.getColumnName())) {
                sql.append(" AND (").append(buildGlobalSearchOr(UNMAPPED_PASSIVE_DB_COLUMNS,
                    UNMAPPED_PASSIVE_DATE_COLUMNS, Set.of(), Set.of(), params, searchTerm)).append(")");
            } else {
                String col = validateColumn(filter.getColumnName(), UNMAPPED_PASSIVE_ALLOWED_COLUMNS);
                if (col != null) appendColumnSearch(sql, params, col, searchTerm,
                    UNMAPPED_PASSIVE_DATE_COLUMNS, Set.of(), Set.of());
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), UNMAPPED_PASSIVE_ALLOWED_COLUMNS);
                    if (col != null) {
                        sql.append(" AND ").append(col).append(" = ?");
                        params.add(entry.getValue());
                    }
                }
            }
        }

if (notBlank(filter.getDateFrom())) {
    LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
    if (from != null) {
        sql.append(" AND recordDateTime >= ?");
        params.add(Timestamp.valueOf(from));
    }
}
if (notBlank(filter.getDateTo())) {
    LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
    if (to != null) {
        sql.append(" AND recordDateTime <= ?");
        params.add(Timestamp.valueOf(to));
    }
}
        if (notBlank(filter.getSiteId())) {
            sql.append(" AND siteId = ?");
            params.add(filter.getSiteId());
        }

        return new SqlFragment(sql.toString(), params);
    }

    private SqlFragment buildUnmappedITWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);

        if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
            String searchTerm = filter.getSearchQuery().trim();
            if ("__global__".equals(filter.getColumnName())) {
                sql.append(" AND (").append(buildGlobalSearchOr(UNMAPPED_IT_DB_COLUMNS,
                    UNMAPPED_IT_DATE_COLUMNS, Set.of(), Set.of(), params, searchTerm)).append(")");
            } else {
                String col = validateColumn(filter.getColumnName(), UNMAPPED_IT_ALLOWED_COLUMNS);
                if (col != null) appendColumnSearch(sql, params, col, searchTerm,
                    UNMAPPED_IT_DATE_COLUMNS, Set.of(), Set.of());
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), UNMAPPED_IT_ALLOWED_COLUMNS);
                    if (col != null) {
                        sql.append(" AND ").append(col).append(" = ?");
                        params.add(entry.getValue());
                    }
                }
            }
        }
if (notBlank(filter.getDateFrom())) {
    LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
    if (from != null) {
        sql.append(" AND recordDateTime >= ?");
        params.add(Timestamp.valueOf(from));
    }
}
if (notBlank(filter.getDateTo())) {
    LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
    if (to != null) {
        sql.append(" AND recordDateTime <= ?");
        params.add(Timestamp.valueOf(to));
    }
}

        if (notBlank(filter.getSiteId())) {
            sql.append(" AND siteId = ?");
            params.add(filter.getSiteId());
        }

        return new SqlFragment(sql.toString(), params);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SQL BUILDER HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private String buildGlobalSearchOr(String[] columns, Set<String> dateCols, Set<String> numericCols,
                                        Set<String> intCols, List<Object> params, String searchTerm) {
        StringBuilder clause = new StringBuilder();
        String likeTerm = "%" + searchTerm + "%";
        boolean first = true;

        for (String col : columns) {
            if (!first) clause.append(" OR ");
            first = false;

            if (numericCols.contains(col) || intCols.contains(col)) {
                clause.append("CAST(").append(col).append(" AS CHAR) LIKE ?");
                params.add(likeTerm);
            } else if (dateCols.contains(col)) {
                clause.append("DATE_FORMAT(").append(col).append(", '%Y-%m-%d %H:%i:%s') LIKE ?");
                params.add(likeTerm);
            } else {
                clause.append("LOWER(").append(col).append(") LIKE LOWER(?)");
                params.add(likeTerm);
            }
        }

        return clause.toString();
    }

    private void appendColumnSearch(StringBuilder sql, List<Object> params, String col,
                                     String searchTerm, Set<String> dateCols,
                                     Set<String> numericCols, Set<String> intCols) {
        if (numericCols.contains(col) || intCols.contains(col)) {
            sql.append(" AND CAST(").append(col).append(" AS CHAR) LIKE ?");
            params.add("%" + searchTerm + "%");
        } else if (dateCols.contains(col)) {
            sql.append(" AND DATE_FORMAT(").append(col).append(", '%Y-%m-%d %H:%i:%s') LIKE ?");
            params.add("%" + searchTerm + "%");
        } else {
            sql.append(" AND LOWER(").append(col).append(") LIKE LOWER(?)");
            params.add("%" + searchTerm + "%");
        }
    }

    private String validateColumn(String input, Set<String> allowed) {
        if (input == null) return null;
        String trimmed = input.trim();
        for (String col : allowed) {
            if (col.equalsIgnoreCase(trimmed)) return col;
        }
        log.warn("[ExportExecutor] Rejected unknown column: {}", trimmed);
        return null;
    }

    private long countWithWhere(String table, SqlFragment where) {
        String sql = "SELECT COUNT(*) FROM " + table + where.sql;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, where.params.toArray());
        return count != null ? count : 0;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INNER TYPES
    // ══════════════════════════════════════════════════════════════════════════

    static class SqlFragment {
        final String sql;
        final List<Object> params;
        SqlFragment(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    @FunctionalInterface
    public interface RowCallback {
        void onRow(ResultSet rs);
    }

    @FunctionalInterface
    public interface StreamingQueryExecutor {
        void execute(RowCallback callback) throws Exception;
    }

    public static class ExportResult {
        public final boolean success;
        public final String filePath;
        public final String error;

        private ExportResult(boolean success, String filePath, String error) {
            this.success = success;
            this.filePath = filePath;
            this.error = error;
        }

        public static ExportResult success(String filePath) {
            return new ExportResult(true, filePath, null);
        }

        public static ExportResult failure(String error) {
            return new ExportResult(false, null, error);
        }
    }

    private LocalDateTime parseFlexibleDate(String dateStr, boolean startOfDay) {
    if (dateStr == null || dateStr.isBlank()) return null;
    try {
        return LocalDateTime.parse(dateStr);
    } catch (Exception e) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (Exception e2) {
            log.warn("Unparseable date: {}", dateStr);
            return null;
        }
    }
}
}