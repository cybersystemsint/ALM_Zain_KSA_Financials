package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.serviceImplementor.FarReportExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;

@RestController

public class FarReportExportController {

    @Autowired
    private FarReportExportService exportService;

    /**
     * Streaming export endpoint - downloads start immediately
     * Example: /export/far-report?column=category&value=Computer&operator=like
     */
    @GetMapping(value = "/exporting/far-report", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public StreamingResponseBody exportFarReport(
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String value,
            @RequestParam(defaultValue = "equals") String operator,
            HttpServletResponse response) {

        // Set headers for immediate download
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=FarReport_" + System.currentTimeMillis() + ".xlsx");
        response.setHeader("Cache-Control", "no-cache");

        // Return streaming response - download starts immediately
        return outputStream -> {
            try {
                exportService.exportToExcelOptimized(outputStream, column, value, operator);
                outputStream.flush();
            } catch (Exception e) {
                throw new RuntimeException("Export failed: " + e.getMessage(), e);
            }
        };
    }
}