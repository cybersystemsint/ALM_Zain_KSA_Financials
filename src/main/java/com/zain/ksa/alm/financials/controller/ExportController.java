package com.zain.ksa.alm.financials.controller;

import com.zain.ksa.alm.financials.dto.response.ApiResponse;
import com.zain.ksa.alm.financials.service.ExportJobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Export job status polling and file download controller.
 * 
 * KEY FIX: Uses InputStreamResource with streaming for large files.
 * Handles connection interruptions gracefully.
 */
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("exports")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);
    
    private static final int STREAM_BUFFER_SIZE = 8192;

    private final ExportJobService exportJobService;

    public ExportController(ExportJobService exportJobService) {
        this.exportJobService = exportJobService;
    }

    /**
     * Get export job status (progress, state, etc).
     * Safe to call frequently for polling.
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(@PathVariable String jobId) {
        log.debug("[ExportController] Status request for jobId={}", jobId);
        
        Map<String, Object> status = exportJobService.getStatus(jobId);
        if (status == null) {
            log.warn("[ExportController] Job not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Export job not found: " + jobId));
        }
        
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    /**
     * Download completed export file.
     * 
     * FIX: Uses InputStreamResource for streaming download.
     * - Efficient memory usage (buffers 8KB at a time)
     * - Handles large files (>1GB) without loading into memory
     * - Client disconnections are handled gracefully by Spring/Tomcat
     * 
     * @param jobId export job ID
     * @return file stream with appropriate headers
     */
    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        log.info("[ExportController] Download request for jobId={}", jobId);
        
        String filePath = exportJobService.getFilePath(jobId);
        if (filePath == null) {
            log.warn("[ExportController] Job not in COMPLETED state: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }

        File file = new File(filePath);
        if (!file.exists()) {
            log.error("[ExportController] File not found on disk: {}", filePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }

        if (!file.canRead()) {
            log.error("[ExportController] File not readable: {}", filePath);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }

        String fileName = exportJobService.getFileName(jobId);
        String contentType = exportJobService.getContentType(jobId);
        long fileSize = file.length();

        log.info("[ExportController] Streaming file: {}, size={} bytes", fileName, fileSize);

        try {
            // Use InputStreamResource for streaming (doesn't load entire file into memory)
            InputStreamResource resource = new InputStreamResource(
                new FileInputStream(file),
                "File download stream"
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                    .contentLength(fileSize)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + (fileName != null ? fileName : "export") + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("[ExportController] Error opening file stream: {}", filePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
}
