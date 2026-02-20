package com.telkom.co.ke.almoptics.serviceImplementor;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UnmappedInventoryExportService {
    private static final Logger logger = LogManager.getLogger(UnmappedInventoryExportService.class);

    private final JdbcTemplate jdbcTemplate;

    public UnmappedInventoryExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final int BATCH_SIZE = 10_000;
    private static final int MAX_ROWS_PER_SHEET = 1_000_000;
    private static final int LOG_INTERVAL = 50000;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    public void exportToExcel(OutputStream outputStream, String tableName, String[] columns, String[] displayHeaders, InventoryRequest req) {
        logger.info("Starting export of [{}], filterBy={}, searchColumn={}, searchQuery={}",
                tableName, req.getFilterBy(), req.getSearchColumn(), req.getSearchQuery());

        String selectColumns = String.join(", ", columns);
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // Multi-filter: AND of all keys
        if (req.getFilterBy() != null && !req.getFilterBy().isEmpty()) {
            for (Map.Entry<String, Object> e : req.getFilterBy().entrySet()) {
                if (e.getValue() != null && !e.getValue().toString().isEmpty()) {
                    if (where.length() > 0) where.append(" AND ");
                    where.append(e.getKey()).append(" = ?");
                    params.add(e.getValue());
                }
            }
        }

        // Single search filter (always "contains")
        if (req.getSearchColumn() != null && req.getSearchQuery() != null && !req.getSearchQuery().isEmpty()) {
            if (where.length() > 0) where.append(" AND ");
            where.append("LOWER(").append(req.getSearchColumn()).append(") LIKE LOWER(?)");
            params.add("%" + req.getSearchQuery() + "%");
        }

        String fullSql = "SELECT " + selectColumns + " FROM " + tableName +
                (where.length() > 0 ? " WHERE " + where : "");

        jdbcTemplate.setFetchSize(Integer.MIN_VALUE);
        long startTime = System.currentTimeMillis();

        try (ExcelWriter excelWriter = EasyExcel.write(outputStream).build()) {
            logger.info("Exporting [{}] with SQL: {}", tableName, fullSql);

            // displayHeaders already includes "Sequence Number" as first entry
            List<List<String>> headerRows = new ArrayList<>();
            headerRows.add(Arrays.asList(displayHeaders));

            AtomicInteger currentSheetIndex = new AtomicInteger(0);
            AtomicInteger rowsInCurrentSheet = new AtomicInteger(0);
            AtomicInteger totalRows = new AtomicInteger(0);

            class SheetHolder { WriteSheet sheet; }
            final SheetHolder currentSheetHolder = new SheetHolder();

            List<List<Object>> batch = new ArrayList<>();
            currentSheetHolder.sheet = createSheet(excelWriter, headerRows, currentSheetIndex.get());

            jdbcTemplate.query(fullSql, params.toArray(), rs -> {
                if (!rs.next()) return null;
                do {
                    int sequenceNumber = totalRows.get() + 1; // 1-based row counter

                    List<Object> row = new ArrayList<>();
                    row.add(sequenceNumber); // Prepend generated sequence number

                    for (int col = 1; col <= columns.length; col++) {
                        Object val = rs.getObject(col);
                        if (val instanceof Date) val = DATE_FORMAT.format((Date) val);
                        row.add(val);
                    }

                    batch.add(row);
                    totalRows.incrementAndGet();
                    rowsInCurrentSheet.incrementAndGet();

                    if (rowsInCurrentSheet.get() >= MAX_ROWS_PER_SHEET) {
                        if (!batch.isEmpty()) {
                            excelWriter.write(new ArrayList<>(batch), currentSheetHolder.sheet);
                            batch.clear();
                        }
                        int newSheetIndex = currentSheetIndex.incrementAndGet();
                        currentSheetHolder.sheet = createSheet(excelWriter, headerRows, newSheetIndex);
                        rowsInCurrentSheet.set(0);
                    }

                    if (batch.size() >= BATCH_SIZE) {
                        excelWriter.write(new ArrayList<>(batch), currentSheetHolder.sheet);
                        batch.clear();
                    }

                    if (totalRows.get() % LOG_INTERVAL == 0) {
                        logger.info("Processed {} rows (Current sheet: {}, Rows in sheet: {})",
                                totalRows.get(),
                                currentSheetHolder.sheet.getSheetName(),
                                rowsInCurrentSheet.get());
                    }
                } while (rs.next());
                return null;
            });

            if (!batch.isEmpty()) {
                excelWriter.write(batch, currentSheetHolder.sheet);
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Export completed [{}]: totalRows={}, duration={}ms, sheets={}",
                    tableName, totalRows.get(), duration, currentSheetIndex.get() + 1);

        } catch (Exception e) {
            logger.error("Export failed [{}]", tableName, e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        } finally {
            jdbcTemplate.setFetchSize(0);
        }
    }

    private WriteSheet createSheet(ExcelWriter excelWriter, List<List<String>> headers, int sheetIndex) {
        String sheetName = String.format("Sheet_%d", sheetIndex + 1);
        WriteSheet sheet = EasyExcel.writerSheet(sheetName).build();
        excelWriter.write(headers, sheet);
        return sheet;
    }
}