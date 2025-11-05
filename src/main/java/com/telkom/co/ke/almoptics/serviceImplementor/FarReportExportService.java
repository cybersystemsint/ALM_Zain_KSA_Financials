package com.telkom.co.ke.almoptics.serviceImplementor;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
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

@Service
public class FarReportExportService {

    private static final Logger logger = LogManager.getLogger(FarReportExportService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static final String[] EXPECTED_FIELDS = {
            "recordNo", "recordDatetime", "serialNumber", "tagNumber", "assetId", "assetType", "nodeType",
            "datePlacedInService", "cost", "salvageValue", "poNumber", "createdDate", "category",
            "locationSegment1", "locationSegment2", "locationSegment3", "locationSegment4",
            "accumulatedDepreAccount", "costAccount", "life",
            "vendorName", "vendorNumber", "locations", "value", "invoiceNumber", "linkId", "poLineNumber",
            "monthlyDepreciationAmt", "accumulatedDepreciationAmt", "statusFlag",
            "depreciationDate", "netCost"
    };

    private static final int LOG_INTERVAL = 50000;
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_ROWS_PER_SHEET = 1_000_000; // Leave room for header + safety margin
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public void exportToExcelOptimized(OutputStream outputStream, String column, String value, String operator) throws IOException {

        String selectColumns = String.join(", ", EXPECTED_FIELDS);
        String baseSql = "SELECT " + selectColumns + " FROM tb_FarReport";
        List<Object> params = new ArrayList<>();
        String whereClause = buildWhereClause(column, value, operator, params);

        String fullSql = baseSql + (whereClause.isEmpty() ? "" : " WHERE " + whereClause);

        jdbcTemplate.setFetchSize(Integer.MIN_VALUE);
        logger.info("Starting export with SQL: {}", fullSql);

        long startTime = System.currentTimeMillis();

        try (ExcelWriter excelWriter = EasyExcel.write(outputStream).build()) {

            List<List<String>> headers = new ArrayList<>();
            headers.add(Arrays.asList(EXPECTED_FIELDS));

            // Use AtomicInteger and holder class for effectively final variables
            AtomicInteger currentSheetIndex = new AtomicInteger(0);
            AtomicInteger rowsInCurrentSheet = new AtomicInteger(0);
            AtomicInteger totalRows = new AtomicInteger(0);

            // Holder for current sheet (since WriteSheet is mutable)
            class SheetHolder {
                WriteSheet sheet;
            }
            final SheetHolder currentSheetHolder = new SheetHolder();

            List<List<Object>> batch = new ArrayList<>();

            // Create first sheet
            currentSheetHolder.sheet = createSheet(excelWriter, headers, currentSheetIndex.get());

            jdbcTemplate.query(fullSql, params.toArray(), rs -> {
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();

                    for (int col = 1; col <= EXPECTED_FIELDS.length; col++) {
                        Object val = rs.getObject(col);
                        if (val instanceof Date) {
                            val = DATE_FORMAT.format((Date) val);
                        }
                        row.add(val);
                    }

                    batch.add(row);
                    totalRows.incrementAndGet();
                    rowsInCurrentSheet.incrementAndGet();

                    // Check if we need to switch sheets
                    if (rowsInCurrentSheet.get() >= MAX_ROWS_PER_SHEET) {
                        // Write current batch to current sheet
                        if (!batch.isEmpty()) {
                            excelWriter.write(new ArrayList<>(batch), currentSheetHolder.sheet);
                            batch.clear();
                        }

                        // Create new sheet
                        int newSheetIndex = currentSheetIndex.incrementAndGet();
                        currentSheetHolder.sheet = createSheet(excelWriter, headers, newSheetIndex);
                        rowsInCurrentSheet.set(0);

                        logger.info("Created new sheet {} (Sheet {} exceeded {} rows)",
                                currentSheetHolder.sheet.getSheetName(), newSheetIndex, MAX_ROWS_PER_SHEET);
                    }

                    // Write batch when it reaches BATCH_SIZE
                    if (batch.size() >= BATCH_SIZE) {
                        excelWriter.write(new ArrayList<>(batch), currentSheetHolder.sheet);
                        batch.clear();
                    }

                    // Log progress
                    if (totalRows.get() % LOG_INTERVAL == 0) {
                        logger.info("Processed {} rows (Current sheet: {}, Rows in sheet: {})",
                                totalRows.get(),
                                currentSheetHolder.sheet.getSheetName(),
                                rowsInCurrentSheet.get());
                    }
                }
                return null;
            });

            // Write remaining batch to current sheet
            if (!batch.isEmpty()) {
                excelWriter.write(batch, currentSheetHolder.sheet);
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Export completed. Total rows: {}, Duration: {} ms, Sheets created: {}",
                    totalRows.get(), duration, currentSheetIndex.get() + 1);

        } catch (Exception e) {
            logger.error("Export failed", e);
            throw new IOException("Failed to export data: " + e.getMessage(), e);
        } finally {
            jdbcTemplate.setFetchSize(0);
        }
    }

    private WriteSheet createSheet(ExcelWriter excelWriter, List<List<String>> headers, int sheetIndex) {
        String sheetName = String.format("FAR_Data_Sheet_%d", sheetIndex + 1);
        WriteSheet sheet = EasyExcel.writerSheet(sheetName).build();

        // Write headers for new sheet
        excelWriter.write(headers, sheet);

        return sheet;
    }

    private String buildWhereClause(String column, String value, String operator, List<Object> params) {
        if (column == null || column.isEmpty() || value == null || value.isEmpty()) {
            return "";
        }

        String normalizedColumn = column.trim();
        if (!Arrays.asList(EXPECTED_FIELDS).contains(normalizedColumn)) {
            logger.warn("Invalid column: {}", column);
            throw new IllegalArgumentException("Invalid column: " + column);
        }

        String op = operator == null ? "equals" : operator.toLowerCase();

        if ("equals".equals(op)) {
            params.add(value);
            return normalizedColumn + " = ?";
        } else if ("like".equals(op)) {
            params.add("%" + value + "%");
            return normalizedColumn + " LIKE ?";
        } else if ("contains".equals(op)) {
            params.add("%" + value + "%");
            return "LOWER(" + normalizedColumn + ") LIKE LOWER(?)";
        } else {
            throw new IllegalArgumentException("Unsupported operator: " + operator);
        }
    }
}