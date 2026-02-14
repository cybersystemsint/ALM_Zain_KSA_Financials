package com.zain.ksa.alm.financials.controller;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.zain.ksa.alm.financials.util.FarReportServiceHelper;

import net.minidev.json.JSONObject;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/far-reports")
public class FarReportController {

	private static final Logger LOGGER = LogManager.getLogger(FarReportController.class);

	private final FarReportServiceHelper farReportServiceHelper;

	@Autowired
	public FarReportController(FarReportServiceHelper farReportService) {
		this.farReportServiceHelper = farReportService;
	}

	@PostMapping("/fetch-finance-report")
	public Map<String, Object> fetchFinanceReport(@RequestBody JSONObject request) {
		LOGGER.info("Received request: fetch-finance-report");
		return farReportServiceHelper.fetchFinanceReport(request);
	}

	@PostMapping(value = "/uploadexcel", consumes = MediaType.APPLICATION_JSON_VALUE)
	public JSONObject uploadExcel(@RequestBody List<Map<String, Object>> data) {
		LOGGER.info("Received request: uploadexcel");
		return farReportServiceHelper.processUpload(data, "Excel");
	}

	@PostMapping(value = "/uploadcsv", consumes = MediaType.APPLICATION_JSON_VALUE)
	public JSONObject uploadCsv(@RequestBody List<Map<String, Object>> data) {
		LOGGER.info("Received request: uploadcsv");
		return farReportServiceHelper.processUpload(data, "CSV");
	}

	@GetMapping("/export")
	public ResponseEntity<StreamingResponseBody> exportToExcel(@RequestParam(required = false) String column,
			@RequestParam(required = false) String value,
			@RequestParam(required = false, defaultValue = "equals") String operator) {
		LOGGER.info("Received request: export");
		StreamingResponseBody responseBody = outputStream -> {
			try (BufferedOutputStream bos = new BufferedOutputStream(outputStream, 32 * 1024)) {
				farReportServiceHelper.exportToExcel(bos, column, value, operator);
				bos.flush();
			} catch (IOException e) {
				LOGGER.error("Error exporting to Excel: {}", e.getMessage(), e);
				try {
					outputStream.write(("Error generating Excel file: " + e.getMessage()).getBytes());
					outputStream.flush();
				} catch (IOException ignored) {
				}
			}
		};

		return ResponseEntity.ok()
				.contentType(
						MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"far_reports.xlsx\"")
				.body(responseBody);
	}
}
