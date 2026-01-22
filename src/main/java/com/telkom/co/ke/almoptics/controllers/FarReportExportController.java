package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.dto.FarReportExportRequest;
import com.telkom.co.ke.almoptics.serviceImplementor.FarReportExportService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/exporting")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FarReportExportController {

    private static final Logger logger = LogManager.getLogger(FarReportExportController.class);

    @Autowired
    private FarReportExportService exportService;

    // ============================================================================
    // POST ENDPOINT - Advanced Filtering with JSON
    // ============================================================================
    @PostMapping(value = "/far-report", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public StreamingResponseBody exportFarReportPost(
            @RequestBody FarReportExportRequest request,
            HttpServletResponse response) {

        long startTime = System.currentTimeMillis();

        // Validate request
        validateExportRequest(request);

        // Generate secure filename
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("FarReport_%s.xlsx", timestamp);

        // Log request details
        logger.info("=== FAR Report Export Request ===");
        logger.info("Timestamp: {}", timestamp);
        logger.info("Request: {}", request);

        if (request.getFilterBy() != null && !request.getFilterBy().isEmpty()) {
            logger.info("Filters applied: {} filter(s)", request.getFilterBy().size());
            request.getFilterBy().forEach((column, criteria) ->
                    logger.info("  - {}: {} {}", column, criteria.getOperator(), criteria.getValue())
            );
        } else {
            logger.info("No filters applied - exporting ALL records");
        }

        if (request.getPage() != null && request.getSize() != null) {
            logger.info("Pagination: page={}, size={}", request.getPage(), request.getSize());
        } else {
            logger.info("No pagination - exporting complete dataset");
        }

        // Set response headers
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Download-Options", "noopen");
        response.setHeader("X-Frame-Options", "DENY");
        response.setBufferSize(65536);

        return outputStream -> {
            try {
                logger.info("Starting Excel generation...");

                exportService.exportToExcelWithAdvancedFilters(outputStream, request);

                long duration = System.currentTimeMillis() - startTime;
                double durationSeconds = duration / 1000.0;

                logger.info("=== Export Completed Successfully ===");
                // Use String.format instead of Python-style formatting
                logger.info("Duration: {} seconds ({} ms)",
                        String.format("%.2f", durationSeconds), duration);
                logger.info("File: {}", fileName);
                logger.info("=====================================");

            } catch (IllegalArgumentException e) {
                logger.error("Invalid request parameters: {}", e.getMessage());
                throw new RuntimeException("Invalid export parameters: " + e.getMessage(), e);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Export failed after {} ms: {}", duration, e.getMessage(), e);
                throw new RuntimeException("Export failed: " + e.getMessage(), e);
            }
        };
    }

    // ============================================================================
    // GET ENDPOINT - Legacy Simple Filtering
    // ============================================================================
    @GetMapping(value = "/far-report", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public StreamingResponseBody exportFarReport(
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String value,
            @RequestParam(defaultValue = "equals") String operator,
            HttpServletResponse response) {

        long startTime = System.currentTimeMillis();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("FarReport_%s.xlsx", timestamp);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Download-Options", "noopen");
        response.setBufferSize(65536);

        logger.info("Starting FAR Report export (GET) - Filter: column={}, value={}, operator={}",
                column, value, operator);

        return outputStream -> {
            try {
                exportService.exportToExcelOptimized(outputStream, column, value, operator);

                long duration = System.currentTimeMillis() - startTime;
                logger.info("Export completed in {:.2f} seconds", duration / 1000.0);

            } catch (Exception e) {
                logger.error("Export failed for filter {}={} using {}: {}",
                        column, value, operator, e.getMessage(), e);
                throw new RuntimeException("Export failed: " + e.getMessage(), e);
            }
        };
    }

    // ============================================================================
    // VALIDATION METHODS
    // ============================================================================

    private void validateExportRequest(FarReportExportRequest request) {

        // Validate pagination
        if (request.getSize() != null) {
            if (request.getSize() <= 0) {
                throw new IllegalArgumentException("Page size must be greater than 0");
            }
            if (request.getSize() > 1_000_000) {
                throw new IllegalArgumentException(
                        "Page size cannot exceed 1,000,000 rows. Current: " + request.getSize()
                );
            }
        }

        if (request.getPage() != null && request.getPage() < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }

        // Validate filters
        if (request.getFilterBy() != null && !request.getFilterBy().isEmpty()) {

            if (request.getFilterBy().size() > 10) {
                throw new IllegalArgumentException(
                        "Maximum 10 filters allowed. Current: " + request.getFilterBy().size()
                );
            }

            for (Map.Entry<String, FarReportExportRequest.FilterCriteria> entry :
                    request.getFilterBy().entrySet()) {

                String column = entry.getKey();
                FarReportExportRequest.FilterCriteria criteria = entry.getValue();

                if (!isValidColumnName(column)) {
                    throw new IllegalArgumentException("Invalid column name: " + column);
                }

                if (criteria.getOperator() != null && !isValidOperator(criteria.getOperator())) {
                    throw new IllegalArgumentException(
                            "Invalid operator: " + criteria.getOperator() + " for column: " + column
                    );
                }

                if (criteria.getValue() != null && criteria.getValue().length() > 500) {
                    throw new IllegalArgumentException(
                            "Filter value too long for column: " + column + " (max 500 characters)"
                    );
                }
            }
        }

        // Validate format
        if (request.getFormat() != null && !request.getFormat().equalsIgnoreCase("excel")) {
            throw new IllegalArgumentException(
                    "Unsupported format: " + request.getFormat() + ". Only 'excel' is supported."
            );
        }
    }

    private boolean isValidColumnName(String column) {
        if (column == null || column.trim().isEmpty()) {
            return false;
        }
        return Arrays.asList(FarReportExportService.EXPECTED_FIELDS).contains(column);
    }

    private boolean isValidOperator(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            return false;
        }

        String[] validOperators = {
                "equals", "like", "contains", "startsWith", "startswith", "endsWith", "endswith",
                "greaterThan", "greaterthan", "gt", "lessThan", "lessthan", "lt",
                "greaterThanOrEqual", "greaterthanorequal", "gte",
                "lessThanOrEqual", "lessthanorequal", "lte",
                "notEquals", "notequals", "ne",
                "in", "notIn", "notin",
                "isNull", "isnull", "isNotNull", "isnotnull"
        };

        return Arrays.asList(validOperators).contains(operator);
    }
}