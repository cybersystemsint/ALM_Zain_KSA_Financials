package com.zain.ksa.alm.financials.service.export;


import javax.servlet.http.HttpServletResponse;
import java.util.stream.Stream;

/**
 * Strategy pattern for export formats.
 * Adding Excel, Parquet, or any other format means implementing this interface
 * — no changes to service layer needed.
 *
 * @param <T> the DTO type being exported
 */
public interface ExportStrategy<T> {

    /**
     * Writes the entire stream to the response output stream.
     * Implementations must flush and close the output writer themselves.
     *
     * @param data     a lazily-evaluated stream of DTOs (may be millions of rows)
     * @param response the HTTP response to write into
     * @param filename base filename (without extension)
     */
    void export(Stream<T> data, HttpServletResponse response, String filename) throws Exception;
}
