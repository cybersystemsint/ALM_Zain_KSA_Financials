package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.services.FarReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/far-reports")
public class FarReportController {

    @Autowired
    private FarReportService farReportService;

    @GetMapping("/export")
    public void exportToCSV(
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String value,
            @RequestParam(required = false, defaultValue = "equals") String operator,
            HttpServletResponse response) throws IOException {

        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"far_reports.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            farReportService.streamExportToCsv(writer, column, value, operator);
        }
    }
}