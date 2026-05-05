package com.zain.ksa.alm.financials.service.export;


import com.opencsv.CSVWriter;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Streams records as CSV using a server-side cursor.
 * Only one chunk is in memory at any given time.
 *
 * Design: Uses reflection to discover field names for the header row,
 * making it fully generic and zero-maintenance across DTO changes.
 */
@Component
public class CsvExportStrategy<T> implements ExportStrategy<T> {

    private static final String CONTENT_TYPE = "text/csv;charset=UTF-8";

    @Override
    public void export(Stream<T> data, HttpServletResponse response, String filename) throws Exception {
        response.setContentType(CONTENT_TYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + ".csv\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            final boolean[] headerWritten = {false};
            final Field[][] fieldsHolder = {null};

            data.forEach(record -> {
                try {
                    if (!headerWritten[0]) {
                        Field[] fields = getAllFields(record.getClass());
                        fieldsHolder[0] = fields;
                        String[] headers = Arrays.stream(fields)
                                .map(Field::getName)
                                .toArray(String[]::new);
                        writer.writeNext(headers);
                        headerWritten[0] = true;
                    }
                    String[] row = Arrays.stream(fieldsHolder[0])
                            .map(f -> {
                                try {
                                    f.setAccessible(true);
                                    Object val = f.get(record);
                                    return val != null ? val.toString() : "";
                                } catch (IllegalAccessException e) {
                                    return "";
                                }
                            })
                            .toArray(String[]::new);
                    writer.writeNext(row);
                } catch (Exception e) {
                    throw new RuntimeException("CSV write error", e);
                }
            });
        }
    }

    private Field[] getAllFields(Class<?> clazz) {
        return clazz.getDeclaredFields();
    }
}