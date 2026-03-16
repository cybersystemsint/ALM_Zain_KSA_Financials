package com.zain.ksa.alm.financials.controller;

import com.zain.ksa.alm.financials.dto.response.ApiResponse;
import com.zain.ksa.alm.financials.service.ExportJobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Export job status polling and file download controller.
 *
 * ── Performance fixes vs old version ────────────────────────────────────────
 *  1. StreamingResponseBody   → Spring never buffers the file in heap;
 *                               bytes flow directly from disk → socket.
 *  2. GZIP on-the-fly         → CSV compresses 80-90 %, xlsx 5-10 %.
 *                               100 K CSV: ~10 MB → ~1 MB on wire.
 *  3. 256 KB copy buffer      → far fewer JVM→OS syscalls than the old 8 KB.
 *  4. Content-Length sent     → browser progress bar works for non-gzip path.
 *  5. Range-request support   → lets clients resume interrupted downloads.
 * ────────────────────────────────────────────────────────────────────────────
 */
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("exports")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    /** Copy buffer: 256 KB → sweet spot between heap pressure and syscall count. */
    private static final int COPY_BUFFER_SIZE = 256 * 1024;

    /**
     * Files larger than this threshold get GZIP compression when the client
     * accepts it.  Below ~100 KB the compression overhead isn't worth it.
     */
    private static final long GZIP_THRESHOLD_BYTES = 100 * 1024L;

    private final ExportJobService exportJobService;

    public ExportController(ExportJobService exportJobService) {
        this.exportJobService = exportJobService;
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @GetMapping("/status/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(
            @PathVariable String jobId) {

        Map<String, Object> status = exportJobService.getStatus(jobId);
        if (status == null) {
            log.warn("[Export] Status: job not found jobId={}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Export job not found: " + jobId));
        }
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Streams an export file to the client.
     *
     * Decision tree:
     *   ① Client sends "Accept-Encoding: gzip" AND file > GZIP_THRESHOLD
     *        → stream GZIP-compressed on the fly (no temp file, constant heap)
     *   ② Client sends "Range: bytes=X-Y"
     *        → partial content (resume support)
     *   ③ Otherwise
     *        → plain buffered stream with Content-Length
     */
    @GetMapping("/download/{jobId}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String jobId,
            HttpServletRequest request) {

        // ── Resolve job → file ────────────────────────────────────────────────
        String filePath = exportJobService.getFilePath(jobId);
        if (filePath == null) {
            log.warn("[Export] Download: job not COMPLETED jobId={}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            log.error("[Export] Download: file missing/unreadable path={}", filePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String fileName    = exportJobService.getFileName(jobId);
        String contentType = exportJobService.getContentType(jobId);
        long   fileSize    = file.length();
        if (contentType == null) contentType = "application/octet-stream";
        if (fileName    == null) fileName    = "export";

        log.info("[Export] Download start: jobId={} file={} size={} bytes", jobId, fileName, fileSize);

        // ── Decide transfer mode ──────────────────────────────────────────────
        String rangeHeader   = request.getHeader(HttpHeaders.RANGE);
        boolean clientGzip   = acceptsGzip(request);
        boolean shouldGzip   = clientGzip && fileSize > GZIP_THRESHOLD_BYTES
                               && !isAlreadyCompressed(contentType);

        if (rangeHeader != null && !shouldGzip) {
            return rangeResponse(file, fileName, contentType, fileSize, rangeHeader);
        }

        if (shouldGzip) {
            return gzipResponse(file, fileName, contentType);
        }

        return plainResponse(file, fileName, contentType, fileSize);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Transfer strategies
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Plain buffered stream — fastest path when no compression is needed.
     * Content-Length is set so browsers show real progress.
     */
    private ResponseEntity<StreamingResponseBody> plainResponse(
            File file, String fileName, String contentType, long fileSize) {

        StreamingResponseBody body = out -> {
            byte[] buf = new byte[COPY_BUFFER_SIZE];
            try (InputStream in = new BufferedInputStream(new FileInputStream(file), COPY_BUFFER_SIZE)) {
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.flush();
            } catch (IOException e) {
                log.warn("[Export] Client disconnected during plain download: {}", e.getMessage());
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(body);
    }

    /**
     * On-the-fly GZIP stream — no temp file, constant heap.
     *
     * Why this is fast for CSV:
     *   CSV text compresses ~85 %.  A 500 MB CSV becomes ~75 MB on the wire.
     *   At 100 Mbps that's 6 s instead of 40 s.  The CPU cost of GZIP is
     *   negligible compared to the network saving.
     *
     * xlsx already uses ZIP internally so gains are small (~5 %), but it still
     * avoids needlessly saturating the network link.
     */
    private ResponseEntity<StreamingResponseBody> gzipResponse(
            File file, String fileName, String contentType) {

        StreamingResponseBody body = out -> {
            byte[] buf = new byte[COPY_BUFFER_SIZE];
            // Level 1 = fastest compression; good ratio for structured text data
            try (InputStream  in   = new BufferedInputStream(new FileInputStream(file), COPY_BUFFER_SIZE);
                 GZIPOutputStream gz = new GZIPOutputStream(out, COPY_BUFFER_SIZE) {{
                     // Set compression level via reflection-free approach
                     def.setLevel(java.util.zip.Deflater.BEST_SPEED);
                 }}) {
                int read;
                while ((read = in.read(buf)) != -1) {
                    gz.write(buf, 0, read);
                }
                gz.finish();
            } catch (IOException e) {
                log.warn("[Export] Client disconnected during gzip download: {}", e.getMessage());
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName))
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                // No Content-Length — size is unknown until compression finishes
                .body(body);
    }

    /**
     * HTTP Range (partial content) — enables resumable downloads.
     *
     * Supports single-range only ("bytes=X-Y" or "bytes=X-").
     * Multi-range is rarely used by browsers and adds complexity.
     */
    private ResponseEntity<StreamingResponseBody> rangeResponse(
            File file, String fileName, String contentType,
            long fileSize, String rangeHeader) {

        long[] range = parseRange(rangeHeader, fileSize);
        if (range == null) {
            // Malformed Range header → serve full file
            return plainResponse(file, fileName, contentType, fileSize);
        }

        long start        = range[0];
        long end          = range[1];
        long rangeLength  = end - start + 1;

        log.info("[Export] Range request: bytes={}-{}/{}", start, end, fileSize);

        StreamingResponseBody body = out -> {
            byte[] buf = new byte[COPY_BUFFER_SIZE];
            try (InputStream in = new BufferedInputStream(new FileInputStream(file), COPY_BUFFER_SIZE)) {
                long skipped = in.skip(start);
                if (skipped != start) {
                    log.warn("[Export] Range skip mismatch: wanted={} got={}", start, skipped);
                    return;
                }
                long remaining = rangeLength;
                int  read;
                while (remaining > 0
                       && (read = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                    out.write(buf, 0, read);
                    remaining -= read;
                }
                out.flush();
            } catch (IOException e) {
                log.warn("[Export] Client disconnected during range download: {}", e.getMessage());
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(rangeLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName))
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + end + "/" + fileSize)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(body);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Utility helpers
    // ══════════════════════════════════════════════════════════════════════════

    private boolean acceptsGzip(HttpServletRequest request) {
        String ae = request.getHeader(HttpHeaders.ACCEPT_ENCODING);
        return ae != null && ae.toLowerCase().contains("gzip");
    }

    /**
     * xlsx and zip-based formats are already compressed — GZIP on top adds
     * overhead without meaningful size reduction.
     */
    private boolean isAlreadyCompressed(String contentType) {
        return contentType != null && (
               contentType.contains("spreadsheetml")   // xlsx
            || contentType.contains("zip")
            || contentType.contains("gzip"));
    }

    /**
     * Parses "bytes=X-Y" or "bytes=X-" into [start, end].
     * Returns null on parse failure (caller falls back to full download).
     */
    private long[] parseRange(String rangeHeader, long fileSize) {
        try {
            if (!rangeHeader.startsWith("bytes=")) return null;
            String spec = rangeHeader.substring(6);
            String[] parts = spec.split("-", 2);
            long start = Long.parseLong(parts[0].trim());
            long end   = (parts.length > 1 && !parts[1].isBlank())
                         ? Long.parseLong(parts[1].trim())
                         : fileSize - 1;

            if (start < 0 || end >= fileSize || start > end) return null;
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String contentDisposition(String fileName) {
        return "attachment; filename=\"" + fileName + "\"";
    }
}