package com.telkom.co.ke.almoptics.serviceImplementor;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.telkom.co.ke.almoptics.dto.FarReportExportRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FarReportExportService {

    private static final Logger logger = LogManager.getLogger(FarReportExportService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ============================================================================
    // CONSTANTS
    // ============================================================================
    public static final String[] EXPECTED_FIELDS = {
            "recordNo", "recordDatetime", "serialNumber", "tagNumber", "assetId", "assetType", "nodeType",
            "datePlacedInService", "cost", "salvageValue", "poNumber", "createdDate", "category",
            "locationSegment1", "locationSegment2", "locationSegment3", "locationSegment4",
            "accumulatedDepreAccount", "costAccount", "life",
            "vendorName", "vendorNumber", "locations", "value", "invoiceNumber", "linkId", "poLineNumber",
            "monthlyDepreciationAmt", "accumulatedDepreciationAmt", "statusFlag",
            "depreciationDate", "netCost"
    };

    private static final int LOG_INTERVAL = 100_000;
    private static final int BATCH_SIZE = 5_000;
    private static final int MAX_ROWS_PER_SHEET = 1_000_000;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // ============================================================================
    // PUBLIC API - Advanced Filtering (POST with JSON)
    // ============================================================================
    /**
     * Export FAR Report with advanced filtering support
     * Supports multiple filters with different operators, pagination, and streaming
     *
     * @param outputStream The output stream to write Excel data to
     * @param request The export request containing filters and pagination
     * @throws IOException if export fails
     */
    public void exportToExcelWithAdvancedFilters(OutputStream outputStream, FarReportExportRequest request)
            throws IOException {

        // ========================================================================
        // 1. BUILD SQL QUERY
        // ========================================================================
        String selectColumns = String.join(", ", EXPECTED_FIELDS);
        String baseSql = "SELECT " + selectColumns + " FROM tb_FarReport";
        List<Object> params = new ArrayList<>();

        String whereClause = buildAdvancedWhereClause(request, params);
        String fullSql = baseSql + (whereClause.isEmpty() ? "" : " WHERE " + whereClause);

        //  Add ORDER BY for consistent pagination and reliable exports
        fullSql += " ORDER BY recordNo";

        // Add pagination if requested
        if (request.getPage() != null && request.getSize() != null && request.getSize() > 0) {
            int offset = request.getPage() * request.getSize();
            fullSql += " LIMIT ? OFFSET ?";
            params.add(request.getSize());
            params.add(offset);
        }

        logger.info("=== Starting FAR Report Export ===");
        logger.info("SQL Query: {}", fullSql);
        logger.info("Parameters: {}", params);

        // ========================================================================
        // 2. EXECUTE STREAMING EXPORT
        // ========================================================================
        long startTime = System.currentTimeMillis();
        AtomicLong processedRows = new AtomicLong(0);

        try {
            jdbcTemplate.setFetchSize(Integer.MIN_VALUE);

            executeStreamingExport(outputStream, fullSql, params.toArray(), processedRows, request);

            long duration = System.currentTimeMillis() - startTime;
            double durationSeconds = duration / 1000.0;
            double rowsPerSecond = duration > 0 ? (processedRows.get() * 1000.0 / duration) : 0;

            logger.info("=== Export Completed Successfully ===");
            logger.info("Total Rows: {}", processedRows.get());
            logger.info("Duration: {} seconds ({} ms)",
                    String.format("%.2f", durationSeconds), duration);
            logger.info("Throughput: {} rows/second",
                    String.format("%.2f", rowsPerSecond));
            logger.info("=====================================");

        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;

            // Check if it's a client disconnect - don't log as error
            if (isClientDisconnectError(e)) {
                logger.warn("Export processing completed ({} rows, {} ms) but client disconnected during file transfer",
                        processedRows.get(), duration);
                // Exit gracefully - don't throw
                return;
            }

            // Real errors
            logger.error("=== Export Failed ===");
            logger.error("Rows processed before failure: {}", processedRows.get());
            logger.error("Duration before failure: {} ms", duration);
            logger.error("Error: {}", e.getMessage(), e);
            logger.error("====================");

            throw new IOException("Failed to export FAR Report data: " + e.getMessage(), e);

        } finally {
            jdbcTemplate.setFetchSize(0);
        }
    }

        // ============================================================================
    // PUBLIC API - Legacy Simple Filtering (GET with query params)
    // ============================================================================
    /**
     * Legacy export method with single filter support
     * Converts parameters to new request format and delegates to advanced method
     *
     * @param outputStream The output stream to write Excel data to
     * @param column The column to filter on (optional)
     * @param value The value to filter by (optional)
     * @param operator The operator to use (default: equals)
     * @throws IOException if export fails
     */
    public void exportToExcelOptimized(OutputStream outputStream, String column, String value, String operator)
            throws IOException {

        // Convert legacy parameters to new request format
        FarReportExportRequest request = new FarReportExportRequest();

        if (column != null && !column.isEmpty() && value != null && !value.isEmpty()) {
            Map<String, FarReportExportRequest.FilterCriteria> filterBy = new HashMap<>();
            FarReportExportRequest.FilterCriteria criteria = new FarReportExportRequest.FilterCriteria();
            criteria.setOperator(operator != null ? operator : "equals");
            criteria.setValue(value);
            filterBy.put(column, criteria);
            request.setFilterBy(filterBy);
        }

        // Delegate to advanced method
        exportToExcelWithAdvancedFilters(outputStream, request);
    }

    // ============================================================================
    // PRIVATE METHODS - Core Export Logic
    // ============================================================================

    /**
     * Execute the streaming export with proper batching and multi-sheet support
     */
    private void executeStreamingExport(OutputStream outputStream, String sql, Object[] params,
                                        AtomicLong totalRowsCounter, FarReportExportRequest request) throws IOException {

        try (ExcelWriter excelWriter = EasyExcel.write(outputStream)
                .useDefaultStyle(false)
                .build()) {

            // Prepare headers
            List<List<String>> headers = Collections.singletonList(Arrays.asList(EXPECTED_FIELDS));

            // Sheet management
            AtomicInteger currentSheetIndex = new AtomicInteger(0);
            AtomicInteger rowsInCurrentSheet = new AtomicInteger(0);

            // Use array to hold mutable WriteSheet reference (for lambda access)
            final WriteSheet[] currentSheet = new WriteSheet[1];

            // Batch buffer
            final List<List<Object>> batch = new ArrayList<>(BATCH_SIZE);

            // Extract pagination limit from request
            final Integer maxRows = (request != null && request.getSize() != null && request.getSize() > 0)
                    ? request.getSize()
                    : null;

            // Create first sheet
            currentSheet[0] = createSheet(excelWriter, headers, currentSheetIndex.get());

            // ========================================================================
            // Stream and process rows
            // ========================================================================
            jdbcTemplate.query(sql, params, rs -> {

                // Enforce pagination limit manually (MySQL streaming can ignore LIMIT clause)
                long currentTotal = totalRowsCounter.get();
                if (maxRows != null && currentTotal >= maxRows) {
                    logger.info("Reached pagination limit of {} rows, stopping stream", maxRows);
                    return; // Stop processing more rows
                }

                // Extract row data
                List<Object> row = new ArrayList<>(EXPECTED_FIELDS.length);

                for (int col = 1; col <= EXPECTED_FIELDS.length; col++) {
                    Object val = rs.getObject(col);

                    // Format dates consistently
                    if (val instanceof Date) {
                        val = DATE_FORMAT.format((Date) val);
                    }

                    row.add(val);
                }

                // Add to batch
                batch.add(row);

                // Update counters
                long newTotal = totalRowsCounter.incrementAndGet();
                int currentSheetRows = rowsInCurrentSheet.incrementAndGet();

                // ====================================================================
                // Check if we need a new sheet (Excel 1M row limit)
                // ====================================================================
                if (currentSheetRows >= MAX_ROWS_PER_SHEET) {
                    // Write remaining batch to current sheet
                    if (!batch.isEmpty()) {
                        excelWriter.write(batch, currentSheet[0]);
                        batch.clear();
                    }

                    // Create new sheet
                    int newSheetIndex = currentSheetIndex.incrementAndGet();
                    currentSheet[0] = createSheet(excelWriter, headers, newSheetIndex);
                    rowsInCurrentSheet.set(0);

                    logger.info("Created sheet {} after processing {} total rows",
                            newSheetIndex + 1, newTotal);
                }

                // ====================================================================
                // Write batch when full (performance optimization)
                // ====================================================================
                if (batch.size() >= BATCH_SIZE) {
                    excelWriter.write(batch, currentSheet[0]);
                    batch.clear();

                    // Periodic flush to keep client connection alive
                    try {
                        outputStream.flush();
                    } catch (IOException e) {
                        logger.warn("Client disconnected during data streaming at {} rows", newTotal);
                        throw new RuntimeException("Client disconnected", e);
                    }
                }

                // ====================================================================
                // Progress logging
                // ====================================================================
                if (newTotal % LOG_INTERVAL == 0) {
                    logger.info("Progress: Processed {} rows", newTotal);
                }
            });

            // ========================================================================
            // Write final batch
            // ========================================================================
            if (!batch.isEmpty()) {
                excelWriter.write(batch, currentSheet[0]);
                batch.clear();
            }

            logger.info("Total sheets created: {}", currentSheetIndex.get() + 1);
            logger.info("Data processing complete. Finalizing Excel file...");

        } catch (Exception e) {

            // Check if this is a client disconnect (broken pipe)
            if (isClientDisconnectError(e)) {
                logger.warn("=== Client Disconnected During Export ===");
                logger.warn("Rows successfully processed: {}", totalRowsCounter.get());
                logger.warn("Client likely timed out or cancelled download during Excel finalization");
                logger.warn("This is not a server error - data processing completed successfully");
                logger.warn("=========================================");
                // Don't throw exception - export was successful, client just disconnected
                return;
            }

            // Real errors
            logger.error("Error during streaming export", e);
            throw new IOException("Streaming export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to check if exception is caused by client disconnect
     */
    private boolean isClientDisconnectError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        // Check current exception
        String message = throwable.getMessage();
        if (message != null) {
            // Common patterns for client disconnect
            if (message.contains("Broken pipe") ||
                    message.contains("Client disconnected") ||
                    message.contains("Connection reset") ||
                    message.contains("Can not close IO")) {  // EasyExcel wraps broken pipe
                return true;
            }
        }

        // Check exception type
        String className = throwable.getClass().getName();
        if (className.contains("ExcelGenerateException")) {
            // Check if root cause is broken pipe
            Throwable rootCause = getRootCause(throwable);
            if (rootCause != null) {
                String rootMessage = rootCause.getMessage();
                if (rootMessage != null &&
                        (rootMessage.contains("Broken pipe") ||
                                rootMessage.contains("Connection reset"))) {
                    return true;
                }
                // Check for NullPointerException in OutputBuffer (indicates client disconnect)
                if (rootCause instanceof NullPointerException &&
                        rootMessage != null &&
                        rootMessage.contains("OutputBuffer")) {
                    return true;
                }
            }
        }

        // Recursively check cause chain
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            return isClientDisconnectError(cause);
        }

        return false;
    }

    /**
     * Helper method to find root cause of exception
     */
    private Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    // ============================================================================
    // PRIVATE METHODS - SQL Building
    // ============================================================================

    /**
     * Build WHERE clause from advanced filter request
     * Supports both new filterBy map and legacy columnName/searchQuery
     */
    private String buildAdvancedWhereClause(FarReportExportRequest request, List<Object> params) {
        List<String> conditions = new ArrayList<>();

        // ========================================================================
        // Handle filterBy map (multiple filters with operators)
        // ========================================================================
        if (request.getFilterBy() != null && !request.getFilterBy().isEmpty()) {
            for (Map.Entry<String, FarReportExportRequest.FilterCriteria> entry :
                    request.getFilterBy().entrySet()) {

                String column = entry.getKey();
                FarReportExportRequest.FilterCriteria criteria = entry.getValue();

                // Validate column name (security check)
                if (!Arrays.asList(EXPECTED_FIELDS).contains(column)) {
                    logger.warn("Invalid column in filterBy ignored: {}", column);
                    continue;
                }

                String condition = buildCondition(column, criteria.getValue(),
                        criteria.getOperator(), params);
                if (condition != null && !condition.isEmpty()) {
                    conditions.add(condition);
                }
            }
        }

        // ========================================================================
        // Handle legacy columnName and searchQuery
        // ========================================================================
        if (request.getColumnName() != null && !request.getColumnName().isEmpty() &&
                request.getSearchQuery() != null && !request.getSearchQuery().isEmpty()) {

            if (Arrays.asList(EXPECTED_FIELDS).contains(request.getColumnName())) {
                String condition = buildCondition(request.getColumnName(),
                        request.getSearchQuery(),
                        "contains", params);
                if (condition != null && !condition.isEmpty()) {
                    conditions.add(condition);
                }
            }
        }

        return conditions.isEmpty() ? "" : String.join(" AND ", conditions);
    }

    /**
     * Build single SQL condition based on operator
     * Uses parameterized queries to prevent SQL injection
     */
    private String buildCondition(String column, String value, String operator, List<Object> params) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String op = operator == null ? "equals" : operator.toLowerCase();

        switch (op) {
            case "equals":
                params.add(value);
                return column + " = ?";

            case "like":
            case "contains":
                params.add("%" + value + "%");
                return column + " LIKE ?";

            case "startswith":
            case "startsWith":
                params.add(value + "%");
                return column + " LIKE ?";

            case "endswith":
            case "endsWith":
                params.add("%" + value);
                return column + " LIKE ?";

            case "greaterthan":
            case "greaterThan":
            case "gt":
                params.add(value);
                return column + " > ?";

            case "lessthan":
            case "lessThan":
            case "lt":
                params.add(value);
                return column + " < ?";

            case "greaterthanorequal":
            case "greaterThanOrEqual":
            case "gte":
                params.add(value);
                return column + " >= ?";

            case "lessthanorequal":
            case "lessThanOrEqual":
            case "lte":
                params.add(value);
                return column + " <= ?";

            case "notequals":
            case "notEquals":
            case "ne":
                params.add(value);
                return column + " != ?";

            case "in":
                String[] values = value.split(",");
                if (values.length == 0) {
                    return "";
                }
                String placeholders = String.join(",", Collections.nCopies(values.length, "?"));
                for (String v : values) {
                    params.add(v.trim());
                }
                return column + " IN (" + placeholders + ")";

            case "notin":
            case "notIn":
                String[] notInValues = value.split(",");
                if (notInValues.length == 0) {
                    return "";
                }
                String notInPlaceholders = String.join(",", Collections.nCopies(notInValues.length, "?"));
                for (String v : notInValues) {
                    params.add(v.trim());
                }
                return column + " NOT IN (" + notInPlaceholders + ")";

            case "isnull":
            case "isNull":
                return column + " IS NULL";

            case "isnotnull":
            case "isNotNull":
                return column + " IS NOT NULL";

            case "between":
                // Expects value format: "min,max"
                String[] betweenValues = value.split(",");
                if (betweenValues.length == 2) {
                    params.add(betweenValues[0].trim());
                    params.add(betweenValues[1].trim());
                    return column + " BETWEEN ? AND ?";
                }
                logger.warn("Invalid BETWEEN value format for column {}: {}", column, value);
                return "";

            default:
                logger.warn("Unsupported operator '{}' for column '{}', ignoring condition", operator, column);
                return "";
        }
    }

    /**
     * Create new Excel sheet with headers
     */
    private WriteSheet createSheet(ExcelWriter excelWriter, List<List<String>> headers, int sheetIndex) {
        String sheetName = String.format("FAR_Data_Sheet_%d", sheetIndex + 1);

        WriteSheet sheet = EasyExcel.writerSheet(sheetIndex, sheetName)
                .needHead(true)
                .build();

        // Write headers to the sheet
        excelWriter.write(headers, sheet);

        return sheet;
    }
}