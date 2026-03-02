package com.zain.ksa.alm.financials.service.export;

import javax.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Streams records as Excel (.xlsx) directly to an {@link HttpServletResponse}.
 *
 * <h3>Memory model</h3>
 * <p>Uses Apache POI's {@link SXSSFWorkbook} (Streaming Usermodel) which keeps only
 * {@value ROW_ACCESS_WINDOW_SIZE} rows in heap at any time. Older rows are automatically
 * flushed to compressed temp files on disk, then assembled into the final .xlsx on
 * {@code workbook.write()}. This means a 5-million-row export consumes roughly the same
 * heap as a 500-row export.</p>
 *
 * <h3>Sheet splitting</h3>
 * <p>Excel enforces a hard limit of 1,048,576 rows per sheet. This strategy automatically
 * creates a new sheet every {@value SHEET_ROW_LIMIT} <em>data</em> rows (the header row
 * is excluded from the count). Each new sheet gets its own header row.</p>
 *
 * <h3>Reflection-based headers</h3>
 * <p>Field names are discovered from the first record via reflection. This makes the
 * strategy fully generic — any DTO class works without configuration changes.</p>
 */
@Component
public class ExcelExportStrategy<T> implements ExportStrategy<T> {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportStrategy.class);

    /** Rows held in memory by SXSSFWorkbook before flushing to temp files. */
    private static final int ROW_ACCESS_WINDOW_SIZE = 500;

    /** Max data rows per sheet (excl. header). Below Excel's 1,048,576 hard limit. */
    private static final int SHEET_ROW_LIMIT = 1_000_000;

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Override
    public void export(Stream<T> data, HttpServletResponse response, String filename) throws Exception {
        response.setContentType(CONTENT_TYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + ".xlsx\"");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);

            CellStyle headerStyle = createHeaderStyle(workbook);

            // Mutable state — single-element arrays allow mutation inside lambda
            final Field[][]              fieldsHolder  = {null};
            final boolean[]              headerWritten = {false};
            final AtomicInteger          rowIdx        = new AtomicInteger(0);  // position within current sheet
            final AtomicInteger          dataRowCount  = new AtomicInteger(0);  // data rows in current sheet
            final int[]                  sheetNumber   = {1};
            final AtomicReference<Sheet> currentSheet  =
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
                    if (!headerWritten[0]) {
                        writeHeader(currentSheet.get(), fieldsHolder[0], headerStyle, rowIdx);
                        headerWritten[0] = true;
                    }

                    // ── Sheet boundary — roll over to new sheet ───────────────
                    if (dataRowCount.get() >= SHEET_ROW_LIMIT) {
                        sheetNumber[0]++;
                        log.info("Sheet row limit reached ({} data rows), creating Sheet{}",
                                 SHEET_ROW_LIMIT, sheetNumber[0]);
                        currentSheet.set(workbook.createSheet("Sheet" + sheetNumber[0]));
                        rowIdx.set(0);
                        dataRowCount.set(0);
                        headerWritten[0] = false;
                        writeHeader(currentSheet.get(), fieldsHolder[0], headerStyle, rowIdx);
                        headerWritten[0] = true;
                    }

                    // ── Write data row ────────────────────────────────────────
                    writeDataRow(currentSheet.get(), record, fieldsHolder[0], rowIdx);
                    dataRowCount.incrementAndGet();

                } catch (Exception e) {
                    throw new RuntimeException(
                            "Excel write error at row " + rowIdx.get()
                            + " on Sheet" + sheetNumber[0], e);
                }
            });

            workbook.write(response.getOutputStream());
            workbook.dispose();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeHeader(Sheet sheet, Field[] fields, CellStyle style, AtomicInteger rowIdx) {
        Row headerRow = sheet.createRow(rowIdx.getAndIncrement());
        for (int i = 0; i < fields.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(fields[i].getName());
            cell.setCellStyle(style);
        }
    }

    private void writeDataRow(Sheet sheet, T record, Field[] fields, AtomicInteger rowIdx)
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
}