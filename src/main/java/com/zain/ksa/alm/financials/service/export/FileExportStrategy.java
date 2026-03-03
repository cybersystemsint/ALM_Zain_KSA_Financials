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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Production-grade file export strategy — streams JDBC ResultSet rows directly
 * to SXSSFWorkbook (Excel) or BufferedWriter (CSV) with constant memory.
 *
 * <h3>Key differences from the previous implementation</h3>
 * <table>
 *   <tr><th>Previous</th><th>This version</th></tr>
 *   <tr>
 *     <td>Received {@code Stream<DTO>} — required JPA entities in memory,
 *         reflection for field access, L1 cache management</td>
 *     <td>Receives raw {@code ResultSet} via callback — zero entity objects,
 *         zero reflection, zero L1 cache</td>
 *   </tr>
 *   <tr>
 *     <td>SXSSFWorkbook row window = 500</td>
 *     <td>Row window = 200 (half the heap usage, same I/O characteristics)</td>
 *   </tr>
 *   <tr>
 *     <td>Wrote directly to final path — partial file visible on failure</td>
 *     <td>Writes to {@code .tmp} then atomic rename — no partial files ever</td>
 *   </tr>
 *   <tr>
 *     <td>Generic headers from reflection</td>
 *     <td>Explicit human-friendly headers + typed cells (date style, number style)</td>
 *   </tr>
 * </table>
 *
 * <h3>Memory model</h3>
 * <pre>
 *   SXSSFWorkbook keeps 200 rows in heap    ≈  0.5 MB
 *   Style/font objects (created once)       ≈  0.1 MB
 *   Temp XML files (gzip compressed, on disk, NOT heap)
 *   CSV BufferedWriter buffer               ≈  64 KB
 *   ────────────────────────────────────────────────────
 *   Total heap per export                   ≈  0.6 MB  (constant)
 * </pre>
 *
 * <h3>Sheet splitting</h3>
 * <p>Excel's hard limit is 1,048,576 rows per sheet. This implementation creates
 * a new sheet with headers every {@value MAX_DATA_ROWS_PER_SHEET} data rows.</p>
 */
@Component
public class FileExportStrategy {

    private static final Logger log = LoggerFactory.getLogger(FileExportStrategy.class);

    /** SXSSFWorkbook row access window — heap footprint is proportional to this. */
    private static final int SXSSF_ROW_WINDOW = 200;

    /** Max data rows per sheet (excluding header). Below Excel's 1,048,576 limit. */
    private static final int MAX_DATA_ROWS_PER_SHEET = 1_048_575;

    /** CSV buffer size for BufferedWriter. */
    private static final int CSV_BUFFER_SIZE = 64 * 1024;

    /** CSV flush interval — prevents unbounded OS page cache buildup. */
    private static final int CSV_FLUSH_INTERVAL = 5_000;

    /** Progress reporting interval. */
    private static final int PROGRESS_INTERVAL = 5_000;

    // ══════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Streams data from a JDBC query directly to a file on disk.
     */
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

    // ══════════════════════════════════════════════════════════════════════════
    //  EXCEL EXPORT — SXSSFWorkbook with constant memory
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
            // ── Create workbook ───────────────────────────────────────────────
            workbook = new SXSSFWorkbook(SXSSF_ROW_WINDOW);
            workbook.setCompressTempFiles(true);  // gzip temp XML → 70% less disk

            // ── Create reusable styles (once per workbook) ────────────────────
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle   = createDateStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            // ── Sheet state (mutable, captured by lambda) ─────────────────────
            final int[]   sheetState = {1, 0};    // [sheetNumber, dataRowsInCurrentSheet]
            final Sheet[] current    = {createSheetWithHeader(workbook, 1, headers, headerStyle)};
            final AtomicLong processed = new AtomicLong(0);

            // ── Execute streaming query (or skip if empty) ────────────────────
            if (queryExecutor != null) {
                final SXSSFWorkbook wb = workbook;  // effectively final for lambda

                queryExecutor.execute(rs -> {
                    try {
                        // Sheet boundary — roll over
                        if (sheetState[1] >= MAX_DATA_ROWS_PER_SHEET) {
                            sheetState[0]++;
                            current[0] = createSheetWithHeader(wb, sheetState[0], headers, headerStyle);
                            sheetState[1] = 0;
                            log.info("Sheet limit reached, created sheet {}", sheetState[0]);
                        }

                        // Write data row
                        Row row = current[0].createRow(sheetState[1] + 1); // +1 for header
                        writeExcelRow(rs, row, columns, dateCols, numericCols, integerCols,
                                      dateStyle, numberStyle);
                        sheetState[1]++;

                        // Progress
                        long count = processed.incrementAndGet();
                        if (count % PROGRESS_INTERVAL == 0 || count == totalRows) {
                            progressCb.accept(totalRows, count);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error writing Excel row " + processed.get(), e);
                    }
                });
            }

            // ── Write to tmp file ─────────────────────────────────────────────
            ensureParentDir(tmpPath);
            try (FileOutputStream fos = new FileOutputStream(tmpPath)) {
                workbook.write(fos);
                fos.getFD().sync();  // fsync before rename
            }

            // ── Atomic rename .tmp → final ────────────────────────────────────
            atomicMove(tmpPath, filePath);

            progressCb.accept(totalRows, processed.get());
            log.info("Excel export complete: {} rows, {} sheet(s), file={}",
                     processed.get(), sheetState[0], filePath);

        } catch (Exception e) {
            silentDelete(tmpPath);
            throw e;
        } finally {
            if (workbook != null) {
                try { workbook.dispose(); } catch (Exception ignored) {}  // CRITICAL: deletes temp XML
                try { workbook.close(); }   catch (Exception ignored) {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CSV EXPORT — BufferedWriter with periodic flush
    // ══════════════════════════════════════════════════════════════════════════

    private void exportCsv(String[] headers,
                            String[] columns,
                            Set<String> dateCols,
                            StreamingQueryExecutor queryExecutor,
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

                // Header row
                writer.write(String.join(",", headers));
                writer.newLine();

                // Stream data rows
                if (queryExecutor != null) {
                    queryExecutor.execute(rs -> {
                        try {
                            StringBuilder sb = new StringBuilder(512);
                            for (int i = 0; i < columns.length; i++) {
                                if (i > 0) sb.append(',');
                                Object val = rs.getObject(columns[i]);
                                if (val != null) {
                                    String str = (val instanceof Date)
                                        ? dateFormat.format((Date) val)
                                        : val.toString();
                                    sb.append(escapeCsv(str));
                                }
                            }
                            writer.write(sb.toString());
                            writer.newLine();

                            long count = processed.incrementAndGet();
                            if (count % CSV_FLUSH_INTERVAL == 0) {
                                writer.flush();  // periodic flush to OS
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
    //  EXCEL HELPERS
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
     * Writes one ResultSet row to an Excel Row with typed cells.
     *
     * - Date columns → date-formatted cells (Excel recognizes them as dates)
     * - Numeric columns → double cells with number format (enables SUM, etc.)
     * - Integer columns → numeric cells without decimal format
     * - Everything else → string cells
     * - NULL → blank cell
     */
    private void writeExcelRow(ResultSet rs, Row row,
                                String[] columns,
                                Set<String> dateCols,
                                Set<String> numericCols,
                                Set<String> integerCols,
                                CellStyle dateStyle,
                                CellStyle numberStyle) throws Exception {
        for (int i = 0; i < columns.length; i++) {
            String col = columns[i];
            Object val = rs.getObject(col);

            if (val == null) {
                row.createCell(i).setBlank();
                continue;
            }

            if (dateCols.contains(col) && val instanceof Date) {
                Cell cell = row.createCell(i);
                cell.setCellValue((Date) val);
                cell.setCellStyle(dateStyle);
            } else if (numericCols.contains(col) && val instanceof Number) {
                Cell cell = row.createCell(i);
                cell.setCellValue(((Number) val).doubleValue());
                cell.setCellStyle(numberStyle);
            } else if (integerCols.contains(col) && val instanceof Number) {
                row.createCell(i).setCellValue(((Number) val).doubleValue());
            } else {
                row.createCell(i).setCellValue(val.toString());
            }
        }
    }

    private CellStyle createHeaderStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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

    // ══════════════════════════════════════════════════════════════════════════
    //  FILE SAFETY HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Atomic move: renames .tmp to final path.
     * Same-filesystem rename is O(1) and atomic on Linux (ext4, xfs).
     * Falls back to copy+delete if cross-filesystem.
     */
    private void atomicMove(String tmpPath, String finalPath) throws IOException {
        try {
            Files.move(Path.of(tmpPath), Path.of(finalPath),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Cross-filesystem fallback
            Files.move(Path.of(tmpPath), Path.of(finalPath),
                StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureParentDir(String path) {
        File parent = new File(path).getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private void silentDelete(String path) {
        try { Files.deleteIfExists(Path.of(path)); }
        catch (Exception ignored) {}
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains("\"") || value.contains(",")
                || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }


/**
 * Exports data with sequence number column (first position).
 * Sequence counter increments before each row write, never resets across sheets.
 * Safe for multi-sheet exports exceeding 1,048,576 rows.
 */
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

// ═══════════════════════════════════════════════════════════════════════════
// EXCEL WITH SEQUENCE
// ═══════════════════════════════════════════════════════════════════════════

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
                    // Sheet boundary check
                    if (sheetState[1] >= MAX_DATA_ROWS_PER_SHEET) {
                        sheetState[0]++;
                        current[0] = createSheetWithHeader(wb, sheetState[0], headers, headerStyle);
                        sheetState[1] = 0;
                        log.info("Sheet limit reached, created sheet {}", sheetState[0]);
                    }

                    // Increment sequence BEFORE writing row
                    long sequence = sequenceCounter.incrementAndGet();
                    
                    Row row = current[0].createRow(sheetState[1] + 1);
                    
                    // Cell 0: sequence number
                    Cell seqCell = row.createCell(0);
                    seqCell.setCellValue((double) sequence);
                    
                    // Cells 1..n: data from ResultSet (offset by 1 for sequence column)
                    writeExcelRowWithOffset(rs, row, columns, 1, dateCols, numericCols, integerCols,
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
        try (FileOutputStream fos = new FileOutputStream(tmpPath)) {
            workbook.write(fos);
            fos.getFD().sync();
        }

        atomicMove(tmpPath, filePath);
        progressCb.accept(totalRows, processed.get());
        log.info("Excel export with sequences complete: {} rows, {} sheet(s), file={}",
                 processed.get(), sheetState[0], filePath);

    } catch (Exception e) {
        silentDelete(tmpPath);
        throw e;
    } finally {
        if (workbook != null) {
            try { workbook.dispose(); } catch (Exception ignored) {}
            try { workbook.close(); }   catch (Exception ignored) {}
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CSV WITH SEQUENCE
// ═══════════════════════════════════════════════════════════════════════════

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

            // Header row
            writer.write(String.join(",", headers));
            writer.newLine();

            if (queryExecutor != null) {
                queryExecutor.execute(rs -> {
                    try {
                        // Increment sequence BEFORE writing row
                        long sequence = sequenceCounter.incrementAndGet();
                        
                        StringBuilder sb = new StringBuilder(512);
                        
                        // First column: sequence
                        sb.append(sequence);
                        
                        // Remaining columns: data from ResultSet
                        for (int i = 0; i < columns.length; i++) {
                            sb.append(',');
                            Object val = rs.getObject(columns[i]);
                            if (val != null) {
                                String str = (val instanceof Date)
                                    ? dateFormat.format((Date) val)
                                    : val.toString();
                                sb.append(escapeCsv(str));
                            }
                        }
                        
                        writer.write(sb.toString());
                        writer.newLine();

                        long count = processed.incrementAndGet();
                        if (count % CSV_FLUSH_INTERVAL == 0) {
                            writer.flush();
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
        log.info("CSV export with sequences complete: {} rows, file={}", processed.get(), filePath);

    } catch (Exception e) {
        silentDelete(tmpPath);
        throw e;
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPER: Write Excel row with column offset (for sequence column)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Writes data from ResultSet to Excel row, starting at specified column offset.
 * Used when sequence number occupies column 0, data starts at column 1.
 */
private void writeExcelRowWithOffset(ResultSet rs, Row row,
                                      String[] columns,
                                      int startCol,  // Usually 1 (after sequence column 0)
                                      Set<String> dateCols,
                                      Set<String> numericCols,
                                      Set<String> integerCols,
                                      CellStyle dateStyle,
                                      CellStyle numberStyle) throws Exception {
    for (int i = 0; i < columns.length; i++) {
        String col = columns[i];
        Object val = rs.getObject(col);
        int cellIndex = startCol + i;

        if (val == null) {
            row.createCell(cellIndex).setBlank();
            continue;
        }

        if (dateCols.contains(col) && val instanceof Date) {
            Cell cell = row.createCell(cellIndex);
            cell.setCellValue((Date) val);
            cell.setCellStyle(dateStyle);
        } else if (numericCols.contains(col) && val instanceof Number) {
            Cell cell = row.createCell(cellIndex);
            cell.setCellValue(((Number) val).doubleValue());
            cell.setCellStyle(numberStyle);
        } else if (integerCols.contains(col) && val instanceof Number) {
            row.createCell(cellIndex).setCellValue(((Number) val).doubleValue());
        } else {
            row.createCell(cellIndex).setCellValue(val.toString());
        }
    }
}
}