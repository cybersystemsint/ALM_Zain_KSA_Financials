package com.zain.ksa.alm.financials.service.export;

import com.opencsv.CSVWriter;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Exports a {@code Stream<DTO>} directly to a file on disk (CSV or Excel).
 *
 * <h3>Production design principles</h3>
 * <ul>
 *   <li><b>Zero in-memory accumulation</b> — rows are flushed to disk as they arrive.
 *       SXSSFWorkbook keeps only {@value ROW_ACCESS_WINDOW_SIZE} rows in heap;
 *       CSVWriter flushes its internal buffer automatically.</li>
 *   <li><b>Automatic sheet splitting</b> — Excel files roll over to a new sheet
 *       every {@value SHEET_ROW_LIMIT} <em>data</em> rows (header excluded from count)
 *       to stay safely under Excel's 1,048,576 row-per-sheet hard limit.</li>
 *   <li><b>Single-pass streaming</b> — the input {@code Stream<T>} is consumed
 *       exactly once; no rewinding, no collection, no intermediate lists.</li>
 *   <li><b>Reflection-based header</b> — field names are discovered from the
 *       first record so the strategy is fully generic across any DTO.</li>
 * </ul>
 *
 * <h3>Caller responsibilities</h3>
 * <ul>
 *   <li>The caller must supply a <em>database-cursor-backed</em> stream
 *       (e.g. Spring Data's {@code @Query + Stream<T>}) inside a read-only
 *       transaction so rows are fetched lazily from the DB.</li>
 *   <li>The caller should {@code entityManager.detach()} and periodically
 *       {@code entityManager.clear()} to prevent JPA L1 cache growth.
 *       See {@link com.zain.ksa.alm.financials.service.impl.ExportExecutor}.</li>
 * </ul>
 */
@Component
public class FileExportStrategy {

    private static final Logger log = LoggerFactory.getLogger(FileExportStrategy.class);

    /**
     * Number of rows SXSSFWorkbook keeps in memory before flushing to disk temp files.
     * 500 is a good balance: low heap usage, minimal I/O overhead.
     */
    private static final int ROW_ACCESS_WINDOW_SIZE = 500;

    /**
     * Maximum data rows per Excel sheet. Set below Excel's hard limit of 1,048,576
     * to leave headroom for the header row and any post-processing additions.
     */
    private static final int SHEET_ROW_LIMIT = 1_000_000;

    /**
     * Interval at which CSVWriter is explicitly flushed. Prevents the OS page cache
     * from holding too much uncommitted data on very large exports.
     */
    private static final int CSV_FLUSH_INTERVAL = 5_000;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Streams all records from {@code data} into a file at {@code filePath}.
     * <p>
     * The stream is consumed exactly once and closed by the caller (try-with-resources).
     * This method blocks until the entire stream is consumed and the file is fully written.
     *
     * @param data     lazy stream of DTO records — must not be pre-collected
     * @param filePath absolute path where the output file will be written
     * @param format   CSV or EXCEL
     * @throws Exception on I/O or reflection errors
     */
    public <T> void exportToFile(Stream<T> data, String filePath, ExportFormat format) throws Exception {
        switch (format) {
            case EXCEL -> exportExcel(data, filePath);
            case CSV   -> exportCsv(data, filePath);
            default    -> throw new IllegalArgumentException("Unsupported export format: " + format);
        }
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    private <T> void exportCsv(Stream<T> data, String filePath) throws Exception {
        log.debug("Starting CSV export to {}", filePath);

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {

            final Field[][] fieldsHolder  = {null};
            final boolean[] headerWritten = {false};
            final AtomicInteger rowCount  = new AtomicInteger(0);

            data.forEach(record -> {
                try {
                    // Discover fields from the first record
                    if (fieldsHolder[0] == null) {
                        Field[] fields = record.getClass().getDeclaredFields();
                        for (Field f : fields) f.setAccessible(true);
                        fieldsHolder[0] = fields;
                    }

                    // Write header once
                    if (!headerWritten[0]) {
                        writer.writeNext(
                            Arrays.stream(fieldsHolder[0])
                                  .map(Field::getName)
                                  .toArray(String[]::new)
                        );
                        headerWritten[0] = true;
                    }

                    // Write data row
                    writer.writeNext(
                        Arrays.stream(fieldsHolder[0])
                              .map(f -> safeGetFieldValue(f, record))
                              .toArray(String[]::new)
                    );

                    // Periodic flush to avoid excessive OS page cache buildup
                    if (rowCount.incrementAndGet() % CSV_FLUSH_INTERVAL == 0) {
                        writer.flush();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("CSV write error at row " + rowCount.get(), e);
                }
            });

            writer.flush();
        }

        log.debug("CSV export complete: {}", filePath);
    }

    // ── Excel export ──────────────────────────────────────────────────────────

    private <T> void exportExcel(Stream<T> data, String filePath) throws Exception {
        log.debug("Starting Excel export to {}", filePath);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);

            // Header style — created once on the workbook, reused on every sheet
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Mutable state captured by the lambda — all single-element arrays
            // to allow mutation inside forEach (effectively final references).
            final Field[][]              fieldsHolder = {null};
            final boolean[]              headerDone   = {false};
            final AtomicInteger          rowIdx       = new AtomicInteger(0);   // row position within current sheet
            final AtomicInteger          dataRows     = new AtomicInteger(0);   // data rows in current sheet (excl. header)
            final int[]                  sheetNum     = {1};
            final AtomicReference<Sheet> currentSheet =
                    new AtomicReference<>(workbook.createSheet("Sheet1"));

            data.forEach(record -> {
                try {
                    // ── Discover fields from the first record ─────────────────
                    if (fieldsHolder[0] == null) {
                        Field[] fields = record.getClass().getDeclaredFields();
                        for (Field f : fields) f.setAccessible(true);
                        fieldsHolder[0] = fields;
                    }

                    // ── Write header on a fresh sheet ─────────────────────────
                    if (!headerDone[0]) {
                        writeHeaderRow(currentSheet.get(), fieldsHolder[0], headerStyle, rowIdx);
                        headerDone[0] = true;
                    }

                    // ── Sheet boundary — roll over to a new sheet ─────────────
                    if (dataRows.get() >= SHEET_ROW_LIMIT) {
                        sheetNum[0]++;
                        log.info("Excel sheet limit reached ({} data rows), creating Sheet{}",
                                 SHEET_ROW_LIMIT, sheetNum[0]);
                        currentSheet.set(workbook.createSheet("Sheet" + sheetNum[0]));
                        rowIdx.set(0);
                        dataRows.set(0);
                        headerDone[0] = false;  // force header on the new sheet
                        writeHeaderRow(currentSheet.get(), fieldsHolder[0], headerStyle, rowIdx);
                        headerDone[0] = true;
                    }

                    // ── Write data row ────────────────────────────────────────
                    writeDataRow(currentSheet.get(), record, fieldsHolder[0], rowIdx);
                    dataRows.incrementAndGet();

                } catch (Exception e) {
                    throw new RuntimeException(
                            "Excel write error at row " + rowIdx.get()
                            + " on Sheet" + sheetNum[0], e);
                }
            });

            // Finalize — write workbook to disk
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.dispose();   // clean up SXSSFWorkbook temp files
        }

        log.debug("Excel export complete: {}", filePath);
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeHeaderRow(Sheet sheet, Field[] fields, CellStyle style, AtomicInteger rowIdx) {
        Row row = sheet.createRow(rowIdx.getAndIncrement());
        for (int i = 0; i < fields.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(fields[i].getName());
            cell.setCellStyle(style);
        }
    }

    private <T> void writeDataRow(Sheet sheet, T record, Field[] fields, AtomicInteger rowIdx)
            throws IllegalAccessException {
        Row row = sheet.createRow(rowIdx.getAndIncrement());
        for (int i = 0; i < fields.length; i++) {
            Object val = fields[i].get(record);
            Cell cell = row.createCell(i);
            if (val == null) {
                cell.setBlank();
            } else if (val instanceof Number num) {
                cell.setCellValue(num.doubleValue());
            } else if (val instanceof Boolean bool) {
                cell.setCellValue(bool);
            } else {
                cell.setCellValue(val.toString());
            }
        }
    }

    private <T> String safeGetFieldValue(Field field, T record) {
        try {
            Object val = field.get(record);
            return val != null ? val.toString() : "";
        } catch (IllegalAccessException e) {
            return "";
        }
    }
}