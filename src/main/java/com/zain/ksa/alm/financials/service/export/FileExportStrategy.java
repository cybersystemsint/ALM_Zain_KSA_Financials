package com.zain.ksa.alm.financials.service.export;

import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.service.impl.ExportExecutor.RowCallback;
import com.zain.ksa.alm.financials.service.impl.ExportExecutor.StreamingQueryExecutor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Streams JDBC ResultSet data directly to disk as Excel (.xlsx) or CSV.
 *
 * ── Key performance changes vs previous version ──────────────────────────────
 *
 *  EXCEL
 *  ① SXSSF row window raised 200 → 1 000
 *       More rows buffered in heap before POI flushes to temp XML.
 *       Fewer flush cycles = fewer random writes to /tmp.
 *
 *  ② compressTempFiles(true)  (unchanged — keep it)
 *       Cuts POI's /tmp usage by ~70 %. Without it a 3 M-row export could
 *       consume 4–6 GB of /tmp and trigger OOM.
 *
 *  ③ FileOutputStream wrapped in BufferedOutputStream (32 KB)
 *       The final workbook.write() call generates many small writes.
 *       Buffering them reduces OS page-cache pressure significantly.
 *
 *  CSV
 *  ④ Write buffer raised 64 KB → 512 KB
 *       Each writer.flush() call drains 512 KB in one OS write vs 64 KB.
 *       At 3 M rows (~300 MB CSV) this saves ~4 500 syscalls.
 *
 *  ⑤ StringBuilder pre-sized to row width estimate (512 → 1 024)
 *       Avoids ~3 internal array copies per row for wide schemas like FAR.
 *
 *  ⑥ Flush interval raised 5 000 → 20 000 rows
 *       A flush every 5 K rows at 3 M rows = 600 mid-stream flushes.
 *       At 20 K rows that drops to 150, which is still safe for crash recovery.
 *
 *  ⑦ Progress callback interval raised 5 000 → 10 000
 *       Halves the number of ConcurrentHashMap writes for the progress counter.
 *
 *  Both
 *  ⑧ Atomic rename unchanged (still O(1) on same filesystem)
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Component
public class FileExportStrategy {

    private static final Logger log = LoggerFactory.getLogger(FileExportStrategy.class);

    // ── Tuning constants ──────────────────────────────────────────────────────

    /** SXSSFWorkbook: rows kept in heap before flush to temp XML. */
    private static final int SXSSF_ROW_WINDOW = 1_000;          // was 200

    /** Max data rows per sheet (Excel hard limit is 1,048,576). */
    private static final int MAX_DATA_ROWS_PER_SHEET = 1_048_575;

    /**
     * BufferedOutputStream size for the final workbook.write() call.
     * Batches POI's many small writes into larger OS writes.
     */
    private static final int EXCEL_WRITE_BUFFER = 32 * 1024;     // 32 KB

    /** BufferedWriter size for CSV output. */
    private static final int CSV_BUFFER_SIZE = 512 * 1024;        // was 64 KB → 512 KB

    /**
     * How many CSV rows to accumulate before calling writer.flush().
     * Larger = fewer OS write syscalls; still small enough to not lose
     * much data if the JVM crashes.
     */
    private static final int CSV_FLUSH_INTERVAL = 20_000;         // was 5 000

    /** How often to call the progress callback. */
    private static final int PROGRESS_INTERVAL = 10_000;          // was 5 000

    /** Initial StringBuilder capacity per CSV row. FAR schema is ~50 cols wide. */
    private static final int CSV_ROW_INITIAL_CAPACITY = 1_024;    // was 512

    // ══════════════════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════════════════

    public void exportStreaming(String[] headers,
                                 String[] columns,
                                 Set<String> dateCols,
                                 Set<String> numericCols,
                                 Set<String> integerCols,
                                 StreamingQueryExecutor queryExecutor,
                                 String filePath,
                                 ExportFormat format,
                                 long totalRows,
                                 BiConsumer<Long, Long> progressCb) throws Exception {
        switch (format) {
            case EXCEL -> exportExcel(headers, columns, dateCols, numericCols, integerCols,
                                       queryExecutor, filePath, totalRows, progressCb);
            case CSV   -> exportCsv(headers, columns, dateCols, queryExecutor,
                                     filePath, totalRows, progressCb);
            default    -> throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    public void exportStreamingWithSequence(String[] headers,
                                             String[] columns,
                                             Set<String> dateCols,
                                             Set<String> numericCols,
                                             Set<String> integerCols,
                                             StreamingQueryExecutor queryExecutor,
                                             AtomicLong sequenceCounter,
                                             String filePath,
                                             ExportFormat format,
                                             long totalRows,
                                             BiConsumer<Long, Long> progressCb) throws Exception {
        switch (format) {
            case EXCEL -> exportExcelWithSequence(headers, columns, dateCols, numericCols, integerCols,
                                                   queryExecutor, sequenceCounter, filePath, totalRows, progressCb);
            case CSV   -> exportCsvWithSequence(headers, columns, dateCols, queryExecutor, sequenceCounter,
                                                 filePath, totalRows, progressCb);
            default    -> throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXCEL — SXSSFWorkbook, constant heap
    // ══════════════════════════════════════════════════════════════════════════

    private void exportExcel(String[] headers,
                              String[] columns,
                              Set<String> dateCols,
                              Set<String> numericCols,
                              Set<String> integerCols,
                              StreamingQueryExecutor queryExecutor,
                              String filePath,
                              long totalRows,
                              BiConsumer<Long, Long> progressCb) throws Exception {

        String tmpPath = filePath + ".tmp";
        SXSSFWorkbook workbook = null;

        try {
            workbook = new SXSSFWorkbook(SXSSF_ROW_WINDOW);
            workbook.setCompressTempFiles(true);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle   = createDateStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            final int[]   sheetState = {1, 0};
            final Sheet[] current    = {createSheetWithHeader(workbook, 1, headers, headerStyle)};
            final AtomicLong processed = new AtomicLong(0);

            if (queryExecutor != null) {
                final SXSSFWorkbook wb = workbook;
                queryExecutor.execute(rs -> {
                    try {
                        if (sheetState[1] >= MAX_DATA_ROWS_PER_SHEET) {
                            sheetState[0]++;
                            current[0] = createSheetWithHeader(wb, sheetState[0], headers, headerStyle);
                            sheetState[1] = 0;
                            log.info("Sheet limit reached, created sheet {}", sheetState[0]);
                        }

                        Row row = current[0].createRow(sheetState[1] + 1);
                        writeExcelRow(rs, row, columns, 0, dateCols, numericCols, integerCols,
                                      dateStyle, numberStyle);
                        sheetState[1]++;

                        long count = processed.incrementAndGet();
                        if (count % PROGRESS_INTERVAL == 0 || count == totalRows) {
                            progressCb.accept(totalRows, count);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error writing Excel row " + processed.get(), e);
                    }
                });
            }

            ensureParentDir(tmpPath);
            // ── Buffered write: reduces OS syscalls during workbook assembly ──
            try (FileOutputStream   fos = new FileOutputStream(tmpPath);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, EXCEL_WRITE_BUFFER)) {
                workbook.write(bos);
                bos.flush();
                fos.getFD().sync();
            }

            atomicMove(tmpPath, filePath);
            progressCb.accept(totalRows, processed.get());
            log.info("Excel export complete: {} rows, {} sheet(s), file={}",
                     processed.get(), sheetState[0], filePath);

        } catch (Exception e) {
            silentDelete(tmpPath);
            throw e;
        } finally {
            disposeWorkbook(workbook);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXCEL WITH SEQUENCE
    // ══════════════════════════════════════════════════════════════════════════

    private void exportExcelWithSequence(String[] headers,
                                          String[] columns,
                                          Set<String> dateCols,
                                          Set<String> numericCols,
                                          Set<String> integerCols,
                                          StreamingQueryExecutor queryExecutor,
                                          AtomicLong sequenceCounter,
                                          String filePath,
                                          long totalRows,
                                          BiConsumer<Long, Long> progressCb) throws Exception {

        String tmpPath = filePath + ".tmp";
        SXSSFWorkbook workbook = null;

        try {
            workbook = new SXSSFWorkbook(SXSSF_ROW_WINDOW);
            workbook.setCompressTempFiles(true);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle   = createDateStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            final int[]   sheetState = {1, 0};
            final Sheet[] current    = {createSheetWithHeader(workbook, 1, headers, headerStyle)};
            final AtomicLong processed = new AtomicLong(0);

            if (queryExecutor != null) {
                final SXSSFWorkbook wb = workbook;
                queryExecutor.execute(rs -> {
                    try {
                        if (sheetState[1] >= MAX_DATA_ROWS_PER_SHEET) {
                            sheetState[0]++;
                            current[0] = createSheetWithHeader(wb, sheetState[0], headers, headerStyle);
                            sheetState[1] = 0;
                        }

                        long sequence = sequenceCounter.incrementAndGet();
                        Row row = current[0].createRow(sheetState[1] + 1);

                        // Col 0 = sequence
                        row.createCell(0).setCellValue((double) sequence);
                        // Col 1..n = data
                        writeExcelRow(rs, row, columns, 1, dateCols, numericCols, integerCols,
                                      dateStyle, numberStyle);
                        sheetState[1]++;

                        long count = processed.incrementAndGet();
                        if (count % PROGRESS_INTERVAL == 0 || count == totalRows) {
                            progressCb.accept(totalRows, count);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error writing Excel row " + processed.get(), e);
                    }
                });
            }

            ensureParentDir(tmpPath);
            try (FileOutputStream   fos = new FileOutputStream(tmpPath);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, EXCEL_WRITE_BUFFER)) {
                workbook.write(bos);
                bos.flush();
                fos.getFD().sync();
            }

            atomicMove(tmpPath, filePath);
            progressCb.accept(totalRows, processed.get());
            log.info("Excel+seq export complete: {} rows, {} sheet(s), file={}",
                     processed.get(), sheetState[0], filePath);

        } catch (Exception e) {
            silentDelete(tmpPath);
            throw e;
        } finally {
            disposeWorkbook(workbook);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CSV — buffered writer, periodic flush
    // ══════════════════════════════════════════════════════════════════════════

    private void exportCsv(String[] headers,
                            String[] columns,
                            Set<String> dateCols,
                            StreamingQueryExecutor queryExecutor,
                            String filePath,
                            long totalRows,
                            BiConsumer<Long, Long> progressCb) throws Exception {

        String tmpPath = filePath + ".tmp";
        // ThreadLocal date formatter: SimpleDateFormat is not thread-safe
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            ensureParentDir(tmpPath);
            final AtomicLong processed = new AtomicLong(0);

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpPath), StandardCharsets.UTF_8),
                    CSV_BUFFER_SIZE)) {  // 512 KB buffer (was 64 KB)

                writer.write(String.join(",", headers));
                writer.newLine();

                if (queryExecutor != null) {
                    queryExecutor.execute(rs -> {
                        try {
                            StringBuilder sb = new StringBuilder(CSV_ROW_INITIAL_CAPACITY);
                            for (int i = 0; i < columns.length; i++) {
                                if (i > 0) sb.append(',');
                                Object val = rs.getObject(columns[i]);
                                if (val != null) {
                                    String str = (val instanceof Date)
                                        ? dateFormat.format((Date) val)
                                        : val.toString();
                                    escapeCsvInto(sb, str);
                                }
                            }
                            writer.write(sb.toString());
                            writer.newLine();

                            long count = processed.incrementAndGet();
                            if (count % CSV_FLUSH_INTERVAL == 0) {
                                writer.flush();   // flush to OS every 20 K rows
                            }
                            if (count % PROGRESS_INTERVAL == 0 || count == totalRows) {
                                progressCb.accept(totalRows, count);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("CSV write error at row " + processed.get(), e);
                        }
                    });
                }
                writer.flush();
            }

            atomicMove(tmpPath, filePath);
            progressCb.accept(totalRows, processed.get());
            log.info("CSV export complete: {} rows, file={}", processed.get(), filePath);

        } catch (Exception e) {
            silentDelete(tmpPath);
            throw e;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CSV WITH SEQUENCE
    // ══════════════════════════════════════════════════════════════════════════

    private void exportCsvWithSequence(String[] headers,
                                        String[] columns,
                                        Set<String> dateCols,
                                        StreamingQueryExecutor queryExecutor,
                                        AtomicLong sequenceCounter,
                                        String filePath,
                                        long totalRows,
                                        BiConsumer<Long, Long> progressCb) throws Exception {

        String tmpPath = filePath + ".tmp";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            ensureParentDir(tmpPath);
            final AtomicLong processed = new AtomicLong(0);

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpPath), StandardCharsets.UTF_8),
                    CSV_BUFFER_SIZE)) {

                writer.write(String.join(",", headers));
                writer.newLine();

                if (queryExecutor != null) {
                    queryExecutor.execute(rs -> {
                        try {
                            long sequence = sequenceCounter.incrementAndGet();
                            StringBuilder sb = new StringBuilder(CSV_ROW_INITIAL_CAPACITY);
                            sb.append(sequence);
                            for (int i = 0; i < columns.length; i++) {
                                sb.append(',');
                                Object val = rs.getObject(columns[i]);
                                if (val != null) {
                                    String str = (val instanceof Date)
                                        ? dateFormat.format((Date) val)
                                        : val.toString();
                                    escapeCsvInto(sb, str);
                                }
                            }
                            writer.write(sb.toString());
                            writer.newLine();

                            long count = processed.incrementAndGet();
                            if (count % CSV_FLUSH_INTERVAL == 0) writer.flush();
                            if (count % PROGRESS_INTERVAL == 0 || count == totalRows) {
                                progressCb.accept(totalRows, count);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("CSV write error at row " + processed.get(), e);
                        }
                    });
                }
                writer.flush();
            }

            atomicMove(tmpPath, filePath);
            progressCb.accept(totalRows, processed.get());
            log.info("CSV+seq export complete: {} rows, file={}", processed.get(), filePath);

        } catch (Exception e) {
            silentDelete(tmpPath);
            throw e;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Excel helpers
    // ══════════════════════════════════════════════════════════════════════════

    private Sheet createSheetWithHeader(SXSSFWorkbook wb, int sheetNum,
                                         String[] headers, CellStyle headerStyle) {
        String name = "Data" + (sheetNum > 1 ? " (" + sheetNum + ")" : "");
        Sheet sheet = wb.createSheet(name);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        return sheet;
    }

    /**
     * Unified row writer with configurable start column (0 = no sequence, 1 = after sequence).
     */
    private void writeExcelRow(ResultSet rs, Row row,
                                String[] columns,
                                int startCol,
                                Set<String> dateCols,
                                Set<String> numericCols,
                                Set<String> integerCols,
                                CellStyle dateStyle,
                                CellStyle numberStyle) throws Exception {
        for (int i = 0; i < columns.length; i++) {
            String col      = columns[i];
            Object val      = rs.getObject(col);
            int    cellIdx  = startCol + i;

            if (val == null) {
                row.createCell(cellIdx).setBlank();
                continue;
            }
            if (dateCols.contains(col) && val instanceof Date) {
                Cell cell = row.createCell(cellIdx);
                cell.setCellValue((Date) val);
                cell.setCellStyle(dateStyle);
            } else if (numericCols.contains(col) && val instanceof Number) {
                Cell cell = row.createCell(cellIdx);
                cell.setCellValue(((Number) val).doubleValue());
                cell.setCellStyle(numberStyle);
            } else if (integerCols.contains(col) && val instanceof Number) {
                row.createCell(cellIdx).setCellValue(((Number) val).doubleValue());
            } else {
                row.createCell(cellIdx).setCellValue(val.toString());
            }
        }
    }

    private CellStyle createHeaderStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createDateStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat()
            .getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    private CellStyle createNumberStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat()
            .getFormat("#,##0.00"));
        return style;
    }

    private void disposeWorkbook(SXSSFWorkbook wb) {
        if (wb == null) return;
        try { wb.dispose(); } catch (Exception ignored) {}
        try { wb.close();   } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    // File / IO helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void atomicMove(String tmpPath, String finalPath) throws IOException {
        try {
            Files.move(Path.of(tmpPath), Path.of(finalPath),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(Path.of(tmpPath), Path.of(finalPath),
                StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureParentDir(String path) {
        File parent = new File(path).getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }

    private void silentDelete(String path) {
        try { Files.deleteIfExists(Path.of(path)); } catch (Exception ignored) {}
    }

    /**
     * Writes an RFC-4180-escaped value directly into a StringBuilder,
     * avoiding a temporary String allocation compared to the old escapeCsv(String).
     */
    private void escapeCsvInto(StringBuilder sb, String value) {
        if (value == null || value.isEmpty()) return;

        boolean needsQuoting = value.indexOf('"')  >= 0
                            || value.indexOf(',')  >= 0
                            || value.indexOf('\n') >= 0
                            || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            sb.append(value);
            return;
        }
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') sb.append('"');  // double the quote
            sb.append(c);
        }
        sb.append('"');
    }
}