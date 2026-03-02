package com.zain.ksa.alm.financials.controller;

import com.zain.ksa.alm.financials.dto.response.ApiResponse;
import com.zain.ksa.alm.financials.service.ExportJobService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("exports")
public class ExportController {

    private final ExportJobService exportJobService;

    public ExportController(ExportJobService exportJobService) {
        this.exportJobService = exportJobService;
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(@PathVariable String jobId) {
        Map<String, Object> status = exportJobService.getStatus(jobId);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Export job not found: " + jobId));
        }
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        String filePath = exportJobService.getFilePath(jobId);
        if (filePath == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        String fileName = exportJobService.getFileName(jobId);
        String contentType = exportJobService.getContentType(jobId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentLength(file.length())
                .body(new FileSystemResource(file));
    }
}