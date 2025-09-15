package com.telkom.co.ke.almoptics.controllers;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

// Reactor imports
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Service import
import com.telkom.co.ke.almoptics.serviceImplementor.OptimizedFarReportReactiveService;

import java.io.BufferedOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/far-reports")
public class FarReportReactiveController {

    private static final Logger LOGGER = LogManager.getLogger(FarReportReactiveController.class);

    @Autowired
    private OptimizedFarReportReactiveService farReportService;

    // Original reactive endpoint (has streaming issues)
    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<DataBuffer>>> exportToExcel(
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String value,
            @RequestParam(required = false, defaultValue = "equals") String operator,
            ServerHttpResponse response) {

        // Set response headers
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"far_reports.xlsx\"");

        // Create reactive data stream
        Flux<DataBuffer> excelStream = farReportService.exportToExcelReactive(column, value, operator)
                .onErrorResume(throwable -> {
                    LOGGER.error("Error during export: {}", throwable.getMessage(), throwable);
                    String errorMsg = "Error generating Excel file: " + throwable.getMessage();
                    // Use DefaultDataBufferFactory to create DataBuffer
                    DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
                    DataBuffer errorBuffer = factory.wrap(errorMsg.getBytes());
                    return Flux.just(errorBuffer);
                })
                .doOnComplete(() -> LOGGER.info("Export completed successfully"))
                .doOnCancel(() -> LOGGER.warn("Export was cancelled by client"));

        return Mono.just(ResponseEntity.ok().body(excelStream));
    }

    // NEW: Working streaming export
    @GetMapping("/export-working")
    public ResponseEntity<StreamingResponseBody> exportWorking(
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String value,
            @RequestParam(required = false, defaultValue = "equals") String operator) {

        LOGGER.info("Starting working export - column: {}, value: {}, operator: {}", column, value, operator);

        StreamingResponseBody responseBody = outputStream -> {
            try (BufferedOutputStream bos = new BufferedOutputStream(outputStream, 32 * 1024)) {
                // Use the service direct export method
                farReportService.exportToExcelDirect(bos, column, value, operator);
                bos.flush();
                LOGGER.info("Working export completed successfully");
            } catch (IOException e) {
                LOGGER.error("Error in working export: {}", e.getMessage(), e);
                try {
                    outputStream.write(("Error generating Excel file: " + e.getMessage()).getBytes());
                    outputStream.flush();
                } catch (IOException ignored) { }
            } catch (Exception e) {
                LOGGER.error("Unexpected error in working export: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"far_reports_working.xlsx\"")
                .body(responseBody);
    }

    // NEW: Limited export for testing
    @GetMapping("/export-limited")
    public ResponseEntity<StreamingResponseBody> exportLimited(
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String value,
            @RequestParam(required = false, defaultValue = "equals") String operator,
            @RequestParam(defaultValue = "50000") int limit) {

        LOGGER.info("Starting limited export - limit: {}, column: {}, value: {}", limit, column, value);

        StreamingResponseBody responseBody = outputStream -> {
            try (BufferedOutputStream bos = new BufferedOutputStream(outputStream, 32 * 1024)) {
                farReportService.exportToExcelWithLimit(bos, column, value, operator, limit);
                bos.flush();
                LOGGER.info("Limited export completed successfully");
            } catch (Exception e) {
                LOGGER.error("Error in limited export: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"far_reports_limited.xlsx\"")
                .body(responseBody);
    }
}