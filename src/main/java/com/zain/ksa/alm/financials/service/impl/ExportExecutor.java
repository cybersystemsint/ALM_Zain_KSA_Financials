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
 * Executes export jobs on a bounded thread pool and streams results to disk.
 *
 * ── Changes from previous version ───────────────────────────────────────────
 *
 *  ① CallerRunsPolicy → AbortPolicy + graceful RejectedExecutionException.
 *
 *    PROBLEM: CallerRunsPolicy runs the task on the HTTP request thread when
 *    the queue is full (queue=10, maxThreads=3 → overflows at 14 concurrent
 *    requests). A 3M-row export on the HTTP thread blocks it for 3–10 minutes,
 *    exhausting Tomcat's worker pool and causing a server-wide hang.
 *
 *    FIX: AbortPolicy throws RejectedExecutionException immediately. The
 *    execute() method catches it and returns a structured "server busy" error
 *    to the client via resultCallback, which becomes a clean HTTP 429-style
 *    response. No HTTP thread is ever tied up for more than microseconds.
 *
 *  ② Semaphore REMOVED.
 *
 *    PROBLEM: The Semaphore(maxThreads) duplicated the thread pool's own
 *    concurrency limit. The ThreadPoolExecutor with maximumPoolSize=maxThreads
 *    already limits how many tasks run simultaneously. Layering a semaphore on
 *    top added a 30-second tryAcquire timeout that ran on the export worker
 *    thread (not the HTTP thread), wasting a pool slot while waiting.
 *
 *    FIX: Removed entirely. The thread pool's queue is the backpressure
 *    mechanism. If the queue is full, the client gets an immediate error.
 *
 *  ③ Queue size raised 10 → 20.
 *
 *    With the semaphore gone, the queue is now the only buffer. Raising it
 *    to 20 allows more exports to wait without being rejected, at the cost of
 *    slightly more memory per queued job metadata (negligible).
 *
 *  ④ Everything else unchanged — column definitions, WHERE builders, streaming
 *    prepared statement, export dispatch.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Component
public class ExportExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExportExecutor.class);

    // ══════════════════════════════════════════════════════════════════════════
    // FAR REPORT COLUMNS
    // ══════════════════════════════════════════════════════════════════════════

private static final String[] FAR_COLUMNS = {
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
    "statusFlag", "financialApproval", "nodeType",
    "mapped"           
};


private static final String[] FAR_HEADERS = {
    "Book", "Asset ID", "Quantity",
    "Description", "Creation Date", "Serial Number", "Tag Number",
    "PIC Status", "PIC Date", "CIP Delivery Date", "Link ID", "Acceptance Number",
    "Depreciate Flag", "CIP EU", "Invoice Number", "PO Number", "PO Line Number",
    "UPL Line", "Transfer To New FAR", "Asset Status", "Part Number",
    "Vendor Name", "Vendor Number", "Merged Code", "Cost Account",
    "CIP Cost Account", "Expense Cost Center",
    "Expense Account", "Life", "Date Placed In Service", "Cost", "NBV",
    "Depreciation Amount", "YTD Depreciation", "Depreciation Reserve",
    "Salvage Value", "Category", "Category Description",
    "Location Segment 1", "Location Segment 2", "Location Segment 3", "Location Segment 4", "Locations",
    "Created By", "Created Date", "Updated By", "Updated Date",
    "Monthly Depreciation Amt", "Depreciation Date", "Net Cost",
    "Status Flag", "Financial Approval", "Node Type",
    "Mapped"
};
    private static final Set<String> FAR_DATE_COLUMNS = Set.of(
        "creationDate", "picDate", "cipDeliveryDate",
        "datePlacedInService", "createdDate", "updatedDate", "depreciationDate"
    );

    private static final Set<String> FAR_NUMERIC_COLUMNS = Set.of(
        "cost", "nbv", "depreciationAmount", "ytdDepreciation",
        "depreciationReserve", "salvageValue", "monthlyDepreciationAmt", "netCost"
    );

    private static final Set<String> FAR_INTEGER_COLUMNS = Set.of("quantity", "Life");

    private static final Set<String> FAR_ALLOWED_COLUMNS;
    static { FAR_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(FAR_COLUMNS)); }

    // ══════════════════════════════════════════════════════════════════════════
    // DEPRECIATION COLUMNS
    // ══════════════════════════════════════════════════════════════════════════
private static final String[] DEP_COLUMNS = {
    "d.recordNo", "d.depreciationPeriod", "d.monthlyDepreciationAmt",
    "d.accumulatedDepreciationAmt", "d.netCost", "d.depreciationDate",
    "d.assetId",
    "f.mapped",       
    "f.book", "f.description", "f.serialNumber", "f.assetType",
    "f.category", "f.categoryDescription", "f.cost", "f.salvageValue",
    "f.Life", "f.datePlacedInService", "f.costAccount",
    "f.accumulatedDepreAccount", "f.expenseAccount",
    "f.quantity", "f.value",
    "d.createdBy", "d.changedBy", "d.recordDatetime"
};
private static final String[] DEP_OUTPUT_COLUMNS = {
    "recordNo", "depreciationPeriod", "monthlyDepreciationAmt",
    "accumulatedDepreciationAmt", "netCost", "depreciationDate",
    "assetId",
    "mapped",    
    "book", "description", "serialNumber", "assetType",
    "category", "categoryDescription", "cost", "salvageValue",
    "life", "datePlacedInService", "costAccount",
    "accumulatedDepreAccount", "expenseAccount",
    "quantity", "value",
    "createdBy", "changedBy", "recordDatetime"
};

private static final String[] DEP_HEADERS = {
    "Record No", "Depreciation Period", "Monthly Depreciation Amt",
    "Accumulated Depreciation Amt", "Net Cost", "Depreciation Date",
    "Asset ID",
    "Mapped",       
    "Book", "Description", "Serial Number", "Asset Type",
    "Category", "Category Description", "Cost", "Salvage Value",
    "Life", "Date Placed In Service", "Cost Account",
    "Accumulated Depre Account", "Expense Account",
    "Quantity", "Value",
    "Created By", "Changed By", "Record Datetime"
};

    private static final Set<String> DEP_DATE_COLUMNS    = Set.of("depreciationDate", "datePlacedInService", "recordDatetime");
    private static final Set<String> DEP_NUMERIC_COLUMNS = Set.of("monthlyDepreciationAmt", "accumulatedDepreciationAmt", "netCost", "cost", "salvageValue", "value");
    private static final Set<String> DEP_INTEGER_COLUMNS = Set.of("recordNo", "life", "quantity");

private static final Set<String> DEP_ALLOWED_COLUMNS;
static {
    DEP_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(
        "recordNo", "recordDatetime", "assetId", "depreciationPeriod",
        "monthlyDepreciationAmt", "accumulatedDepreciationAmt", "netCost",
        "depreciationDate", "createdBy", "changedBy"

    ));
}

    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED ACTIVE
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] UNMAPPED_ACTIVE_DB_COLUMNS = {
        "recordNo", "recordDateTime", "nodeId", "nodeName", "nodeType",
        "serialNumber", "model", "partNumber", "siteId", "manufacturer",
        "description", "manufacturingDate", "installationDate", "assetInsertionDate", "warranty"
    };

    private static final String[] UNMAPPED_ACTIVE_COLUMNS = {
        "sequenceNo",
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
    static { UNMAPPED_ACTIVE_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(UNMAPPED_ACTIVE_DB_COLUMNS)); }

    private static final Set<String> UNMAPPED_ACTIVE_DATE_COLUMNS = Set.of(
        "recordDateTime", "manufacturingDate", "installationDate", "assetInsertionDate"
    );

    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED PASSIVE
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
    static { UNMAPPED_PASSIVE_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(UNMAPPED_PASSIVE_DB_COLUMNS)); }

    private static final Set<String> UNMAPPED_PASSIVE_DATE_COLUMNS = Set.of("recordDateTime");

    // ══════════════════════════════════════════════════════════════════════════
    // UNMAPPED IT
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
    static { UNMAPPED_IT_ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(UNMAPPED_IT_DB_COLUMNS)); }

    private static final Set<String> UNMAPPED_IT_DATE_COLUMNS = Set.of("recordDatetime");

    // ══════════════════════════════════════════════════════════════════════════
    // DEPENDENCIES + CONFIG
    // ══════════════════════════════════════════════════════════════════════════

    private final JdbcTemplate       jdbcTemplate;
    private final FileExportStrategy fileExportStrategy;

    @Value("${app.export.max-threads:3}")
    private int maxThreads;

    @Value("${app.export.dir:${java.io.tmpdir}/data/app}")
    private String exportDir;

    /**
     * Queue capacity for pending export jobs.
     * Raised from 10 → 20 since the semaphore backpressure was removed.
     * At max 3 concurrent workers, this allows up to 23 simultaneous requests
     * before clients start receiving "server busy" errors.
     */
    @Value("${app.export.queue-size:20}")
    private int queueSize;

    private ExecutorService threadPool;

    public ExportExecutor(JdbcTemplate jdbcTemplate, FileExportStrategy fileExportStrategy) {
        this.jdbcTemplate      = jdbcTemplate;
        this.fileExportStrategy = fileExportStrategy;
    }

    @PostConstruct
    public void init() {
        AtomicInteger counter = new AtomicInteger(0);

        this.threadPool = new ThreadPoolExecutor(
            1,          // core threads — keeps 1 alive between bursts
            maxThreads, // max concurrent export workers
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueSize),
            r -> {
                Thread t = new Thread(r, "export-worker-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            },
            // ── AbortPolicy: reject immediately when queue is full ────────────
            // The old CallerRunsPolicy would run the export on the HTTP thread,
            // blocking Tomcat workers for minutes. AbortPolicy throws
            // RejectedExecutionException which we catch below and convert to a
            // clean "server busy" result — no thread starvation possible.
            new ThreadPoolExecutor.AbortPolicy()
        );

        File dir = new File(exportDir);
        if (!dir.exists()) dir.mkdirs();

        log.info("[ExportExecutor] Initialized: maxThreads={}, queueSize={}, exportDir={}",
                 maxThreads, queueSize, exportDir);
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
    // STREAMING PREPARED STATEMENT
    // ══════════════════════════════════════════════════════════════════════════

    private PreparedStatementCreator streamingStatement(String sql, List<Object> params) {
        return conn -> {
            var ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Integer.MIN_VALUE);   // MySQL streaming cursor
            ps.setFetchDirection(ResultSet.FETCH_FORWARD);
            int idx = 1;
            for (Object param : params) ps.setObject(idx++, param);
            return ps;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Submits an export job to the worker pool.
     *
     * If the pool queue is full, resultCallback is called immediately with a
     * "server busy" failure — the calling HTTP thread is never blocked.
     */
    public void execute(String jobId, String exportType, DynamicFilterRequest filter,
                        ExportFormat format, String filePath,
                        BiConsumer<Long, Long> progressCallback,
                        Consumer<ExportResult> resultCallback) {
        try {
            threadPool.submit(() -> {
                long startMs = System.currentTimeMillis();
                log.info("[Export:{}] Starting type={}, format={}", jobId, exportType, format);
                try {
                    switch (exportType) {
                        case "far_report"       -> exportFarReport(jobId, filter, format, filePath, progressCallback);
                        case "depreciation"     -> exportDepreciation(jobId, filter, format, filePath, progressCallback);
                        case "unmapped_active"  -> exportUnmappedActive(jobId, filter, format, filePath, progressCallback);
                        case "unmapped_passive" -> exportUnmappedPassive(jobId, filter, format, filePath, progressCallback);
                        case "unmapped_it"      -> exportUnmappedIT(jobId, filter, format, filePath, progressCallback);
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
                }
            });
        } catch (RejectedExecutionException e) {
            // Pool queue full — return structured error immediately, no thread blocked
            log.warn("[Export:{}] Rejected — export pool at capacity (maxThreads={}, queueSize={})",
                     jobId, maxThreads, queueSize);
            resultCallback.accept(ExportResult.failure(
                "Export server is busy. Too many concurrent exports. Please try again shortly."
            ));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPORT METHODS (unchanged from previous version)
    // ══════════════════════════════════════════════════════════════════════════

    private void exportFarReport(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                  String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where    = buildFarWhereClause(filter);
        long        totalRows = countWithWhere("tb_FarReport", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] FAR total rows: {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(FAR_HEADERS, FAR_COLUMNS, FAR_DATE_COLUMNS,
                FAR_NUMERIC_COLUMNS, FAR_INTEGER_COLUMNS, null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", FAR_COLUMNS)
                   + " FROM tb_FarReport" + where.sql + " ORDER BY creationDate ASC";

        fileExportStrategy.exportStreaming(FAR_HEADERS, FAR_COLUMNS, FAR_DATE_COLUMNS,
            FAR_NUMERIC_COLUMNS, FAR_INTEGER_COLUMNS,
            callback -> {
                jdbcTemplate.query(streamingStatement(sql, where.params), (ResultSet rs) -> callback.onRow(rs));
                log.info("[Export:{}] FAR streaming completed", jobId);
            },
            filePath, format, totalRows, progressCallback);
    }

    private void exportDepreciation(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                     String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where     = buildDepreciationWhereClause(filter);
        long        totalRows = countWithWhere("tb_DepreciationHistory", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] Depreciation total rows: {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(DEP_HEADERS, DEP_OUTPUT_COLUMNS, DEP_DATE_COLUMNS,
                DEP_NUMERIC_COLUMNS, DEP_INTEGER_COLUMNS, null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", DEP_COLUMNS)
                   + " FROM tb_DepreciationHistory d"
                   + " LEFT JOIN tb_FarReport f ON d.assetId = f.assetId"
                   + where.sql + " ORDER BY d.recordNo ASC";

        fileExportStrategy.exportStreaming(DEP_HEADERS, DEP_OUTPUT_COLUMNS, DEP_DATE_COLUMNS,
            DEP_NUMERIC_COLUMNS, DEP_INTEGER_COLUMNS,
            callback -> {
                jdbcTemplate.query(streamingStatement(sql, where.params), (ResultSet rs) -> callback.onRow(rs));
                log.info("[Export:{}] Depreciation streaming completed", jobId);
            },
            filePath, format, totalRows, progressCallback);
    }

    private void exportUnmappedActive(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                       String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where     = buildUnmappedActiveWhereClause(filter);
        long        totalRows = countWithWhere("tb_unmapped_active_inventory", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] Unmapped Active total rows: {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(UNMAPPED_ACTIVE_HEADERS, UNMAPPED_ACTIVE_COLUMNS,
                UNMAPPED_ACTIVE_DATE_COLUMNS, Set.of(), Set.of(), null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", UNMAPPED_ACTIVE_DB_COLUMNS)
                   + " FROM tb_unmapped_active_inventory" + where.sql + " ORDER BY recordNo ASC";

        fileExportStrategy.exportStreamingWithSequence(UNMAPPED_ACTIVE_HEADERS, UNMAPPED_ACTIVE_DB_COLUMNS,
            UNMAPPED_ACTIVE_DATE_COLUMNS, Set.of(), Set.of(),
            callback -> jdbcTemplate.query(streamingStatement(sql, where.params), (ResultSet rs) -> callback.onRow(rs)),
            new AtomicLong(0), filePath, format, totalRows, progressCallback);
    }

    private void exportUnmappedPassive(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                        String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where     = buildUnmappedPassiveWhereClause(filter);
        long        totalRows = countWithWhere("tb_unmapped_passive_inventory", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] Unmapped Passive total rows: {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(UNMAPPED_PASSIVE_HEADERS, UNMAPPED_PASSIVE_COLUMNS,
                UNMAPPED_PASSIVE_DATE_COLUMNS, Set.of(), Set.of(), null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", UNMAPPED_PASSIVE_DB_COLUMNS)
                   + " FROM tb_unmapped_passive_inventory" + where.sql + " ORDER BY id ASC";

        fileExportStrategy.exportStreamingWithSequence(UNMAPPED_PASSIVE_HEADERS, UNMAPPED_PASSIVE_DB_COLUMNS,
            UNMAPPED_PASSIVE_DATE_COLUMNS, Set.of(), Set.of(),
            callback -> jdbcTemplate.query(streamingStatement(sql, where.params), (ResultSet rs) -> callback.onRow(rs)),
            new AtomicLong(0), filePath, format, totalRows, progressCallback);
    }

    private void exportUnmappedIT(String jobId, DynamicFilterRequest filter, ExportFormat format,
                                   String filePath, BiConsumer<Long, Long> progressCallback) throws Exception {
        SqlFragment where     = buildUnmappedITWhereClause(filter);
        long        totalRows = countWithWhere("tb_unmapped_IT_Inventory", where);
        progressCallback.accept(totalRows, 0L);
        log.info("[Export:{}] Unmapped IT total rows: {}", jobId, totalRows);

        if (totalRows == 0) {
            fileExportStrategy.exportStreaming(UNMAPPED_IT_HEADERS, UNMAPPED_IT_COLUMNS,
                UNMAPPED_IT_DATE_COLUMNS, Set.of(), Set.of(), null, filePath, format, totalRows, progressCallback);
            return;
        }

        String sql = "SELECT " + String.join(", ", UNMAPPED_IT_DB_COLUMNS)
                   + " FROM tb_unmapped_IT_Inventory" + where.sql + " ORDER BY id ASC";

        fileExportStrategy.exportStreamingWithSequence(UNMAPPED_IT_HEADERS, UNMAPPED_IT_DB_COLUMNS,
            UNMAPPED_IT_DATE_COLUMNS, Set.of(), Set.of(),
            callback -> jdbcTemplate.query(streamingStatement(sql, where.params), (ResultSet rs) -> callback.onRow(rs)),
            new AtomicLong(0), filePath, format, totalRows, progressCallback);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WHERE CLAUSE BUILDERS (unchanged)
    // ══════════════════════════════════════════════════════════════════════════

    private SqlFragment buildFarWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql    = new StringBuilder(" WHERE 1=1");
        List<Object>  params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);

        if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
            String searchTerm = filter.getSearchQuery().trim();
            if ("__global__".equals(filter.getColumnName())) {
                sql.append(" AND (").append(buildGlobalSearchOr(FAR_COLUMNS, FAR_DATE_COLUMNS,
                    FAR_NUMERIC_COLUMNS, FAR_INTEGER_COLUMNS, params, searchTerm)).append(")");
            } else {
                String col = validateColumn(filter.getColumnName(), FAR_ALLOWED_COLUMNS);
                if (col != null) appendColumnSearch(sql, params, col, searchTerm,
                    FAR_DATE_COLUMNS, FAR_NUMERIC_COLUMNS, FAR_INTEGER_COLUMNS);
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), FAR_ALLOWED_COLUMNS);
                    if (col != null) { sql.append(" AND ").append(col).append(" = ?"); params.add(entry.getValue()); }
                }
            }
        }

        if (notBlank(filter.getDateFrom())) {
            LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
            if (from != null) { sql.append(" AND creationDate >= ?"); params.add(Timestamp.valueOf(from)); }
        }
        if (notBlank(filter.getDateTo())) {
            LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
            if (to != null) { sql.append(" AND creationDate <= ?"); params.add(Timestamp.valueOf(to)); }
        }

        return new SqlFragment(sql.toString(), params);
    }

    private SqlFragment buildDepreciationWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql    = new StringBuilder(" WHERE 1=1");
        List<Object>  params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);

        if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
            String col = validateColumn(filter.getColumnName(), DEP_ALLOWED_COLUMNS);
            if (col != null) appendColumnSearch(sql, params, "d." + col,
                filter.getSearchQuery().trim(), DEP_DATE_COLUMNS, DEP_NUMERIC_COLUMNS, DEP_INTEGER_COLUMNS);
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), DEP_ALLOWED_COLUMNS);
                    if (col != null) { sql.append(" AND d.").append(col).append(" = ?"); params.add(entry.getValue()); }
                }
            }
        }

        if (notBlank(filter.getDateFrom())) {
            LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
            if (from != null) { sql.append(" AND d.depreciationDate >= ?"); params.add(Timestamp.valueOf(from)); }
        }
        if (notBlank(filter.getDateTo())) {
            LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
            if (to != null) { sql.append(" AND d.depreciationDate <= ?"); params.add(Timestamp.valueOf(to)); }
        }

        return new SqlFragment(sql.toString(), params);
    }

    private SqlFragment buildUnmappedActiveWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql    = new StringBuilder(" WHERE 1=1");
        List<Object>  params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);

        if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
            String searchTerm = filter.getSearchQuery().trim();
            if ("__global__".equals(filter.getColumnName())) {
                sql.append(" AND (").append(buildGlobalSearchOr(UNMAPPED_ACTIVE_DB_COLUMNS,
                    UNMAPPED_ACTIVE_DATE_COLUMNS, Set.of(), Set.of(), params, searchTerm)).append(")");
            } else {
                String col = validateColumn(filter.getColumnName(), UNMAPPED_ACTIVE_ALLOWED_COLUMNS);
                if (col != null) appendColumnSearch(sql, params, col, searchTerm, UNMAPPED_ACTIVE_DATE_COLUMNS, Set.of(), Set.of());
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), UNMAPPED_ACTIVE_ALLOWED_COLUMNS);
                    if (col != null) { sql.append(" AND ").append(col).append(" = ?"); params.add(entry.getValue()); }
                }
            }
        }

        if (notBlank(filter.getDateFrom())) {
            LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
            if (from != null) { sql.append(" AND recordDateTime >= ?"); params.add(Timestamp.valueOf(from)); }
        }
        if (notBlank(filter.getDateTo())) {
            LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
            if (to != null) { sql.append(" AND recordDateTime <= ?"); params.add(Timestamp.valueOf(to)); }
        }
        if (notBlank(filter.getSiteId())) { sql.append(" AND siteId = ?"); params.add(filter.getSiteId()); }

        return new SqlFragment(sql.toString(), params);
    }

    private SqlFragment buildUnmappedPassiveWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql    = new StringBuilder(" WHERE 1=1");
        List<Object>  params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);

        if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
            String searchTerm = filter.getSearchQuery().trim();
            if ("__global__".equals(filter.getColumnName())) {
                sql.append(" AND (").append(buildGlobalSearchOr(UNMAPPED_PASSIVE_DB_COLUMNS,
                    UNMAPPED_PASSIVE_DATE_COLUMNS, Set.of(), Set.of(), params, searchTerm)).append(")");
            } else {
                String col = validateColumn(filter.getColumnName(), UNMAPPED_PASSIVE_ALLOWED_COLUMNS);
                if (col != null) appendColumnSearch(sql, params, col, searchTerm, UNMAPPED_PASSIVE_DATE_COLUMNS, Set.of(), Set.of());
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), UNMAPPED_PASSIVE_ALLOWED_COLUMNS);
                    if (col != null) { sql.append(" AND ").append(col).append(" = ?"); params.add(entry.getValue()); }
                }
            }
        }

        if (notBlank(filter.getDateFrom())) {
            LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
            if (from != null) { sql.append(" AND recordDateTime >= ?"); params.add(Timestamp.valueOf(from)); }
        }
        if (notBlank(filter.getDateTo())) {
            LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
            if (to != null) { sql.append(" AND recordDateTime <= ?"); params.add(Timestamp.valueOf(to)); }
        }
        if (notBlank(filter.getSiteId())) { sql.append(" AND siteId = ?"); params.add(filter.getSiteId()); }

        return new SqlFragment(sql.toString(), params);
    }

    private SqlFragment buildUnmappedITWhereClause(DynamicFilterRequest filter) {
        StringBuilder sql    = new StringBuilder(" WHERE 1=1");
        List<Object>  params = new ArrayList<>();
        if (filter == null) return new SqlFragment(sql.toString(), params);

        if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
            String searchTerm = filter.getSearchQuery().trim();
            if ("__global__".equals(filter.getColumnName())) {
                sql.append(" AND (").append(buildGlobalSearchOr(UNMAPPED_IT_DB_COLUMNS,
                    UNMAPPED_IT_DATE_COLUMNS, Set.of(), Set.of(), params, searchTerm)).append(")");
            } else {
                String col = validateColumn(filter.getColumnName(), UNMAPPED_IT_ALLOWED_COLUMNS);
                if (col != null) appendColumnSearch(sql, params, col, searchTerm, UNMAPPED_IT_DATE_COLUMNS, Set.of(), Set.of());
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (notBlank(entry.getValue())) {
                    String col = validateColumn(entry.getKey(), UNMAPPED_IT_ALLOWED_COLUMNS);
                    if (col != null) { sql.append(" AND ").append(col).append(" = ?"); params.add(entry.getValue()); }
                }
            }
        }

        if (notBlank(filter.getDateFrom())) {
            LocalDateTime from = parseFlexibleDate(filter.getDateFrom(), true);
            if (from != null) { sql.append(" AND recordDatetime >= ?"); params.add(Timestamp.valueOf(from)); }
        }
        if (notBlank(filter.getDateTo())) {
            LocalDateTime to = parseFlexibleDate(filter.getDateTo(), false);
            if (to != null) { sql.append(" AND recordDatetime <= ?"); params.add(Timestamp.valueOf(to)); }
        }
        if (notBlank(filter.getSiteId())) { sql.append(" AND siteId = ?"); params.add(filter.getSiteId()); }

        return new SqlFragment(sql.toString(), params);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SQL BUILDER HELPERS (unchanged)
    // ══════════════════════════════════════════════════════════════════════════

    private String buildGlobalSearchOr(String[] columns, Set<String> dateCols, Set<String> numericCols,
                                        Set<String> intCols, List<Object> params, String searchTerm) {
        StringBuilder clause   = new StringBuilder();
        String        likeTerm = "%" + searchTerm + "%";
        boolean       first    = true;
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
        String sql   = "SELECT COUNT(*) FROM " + table + where.sql;
        Long   count = jdbcTemplate.queryForObject(sql, Long.class, where.params.toArray());
        return count != null ? count : 0;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
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

    // ══════════════════════════════════════════════════════════════════════════
    // INNER TYPES (unchanged)
    // ══════════════════════════════════════════════════════════════════════════

    static class SqlFragment {
        final String       sql;
        final List<Object> params;
        SqlFragment(String sql, List<Object> params) { this.sql = sql; this.params = params; }
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
        public final String  filePath;
        public final String  error;

        private ExportResult(boolean success, String filePath, String error) {
            this.success  = success;
            this.filePath = filePath;
            this.error    = error;
        }
        public static ExportResult success(String filePath) { return new ExportResult(true,  filePath, null); }
        public static ExportResult failure(String error)    { return new ExportResult(false, null,     error); }
    }
}