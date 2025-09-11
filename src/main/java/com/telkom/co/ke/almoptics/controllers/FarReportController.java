package com.telkom.co.ke.almoptics.controllers;
import com.telkom.co.ke.almoptics.serviceImplementor.FarReportService;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.ExecutionException;


@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/far-reports")
public class FarReportController {

    private final Logger LOGGER = LogManager.getLogger(FarReportController.class);

    @Autowired
    private FarReportService farReportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/fetch-finance-report")
    public Map<String, Object> fetchFinanceReport(@RequestBody JSONObject request) {
        String assetId = request.getAsString("assetId");
        String columnName = request.containsKey("columnName") ? request.getAsString("columnName") : "";
        String searchQuery = request.containsKey("searchQuery") ? request.getAsString("searchQuery") : "";
        String dateFrom = request.containsKey("dateFrom") ? request.getAsString("dateFrom") : "";
        String dateTo = request.containsKey("dateTo") ? request.getAsString("dateTo") : "";

        int page = Math.max(request.containsKey("page") ? Integer.parseInt(request.getAsString("page")) : 0, 0);
        int size = Math.max(request.containsKey("size") ? Integer.parseInt(request.getAsString("size")) : 100, 1);

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (!assetId.isEmpty()) {
            whereClause.append(" AND assetId = ? ");
            params.add(assetId);
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause.append(" AND LOWER(").append(columnName).append(") LIKE LOWER(?) ");
            params.add("%" + searchQuery + "%");
        }

        if (!dateFrom.isEmpty()) {
            whereClause.append(" AND recordDatetime >= ? ");
            params.add(dateFrom);
        }

        if (!dateTo.isEmpty()) {
            whereClause.append(" AND recordDatetime <= ? ");
            params.add(dateTo);
        }

        String countSql = "SELECT COUNT(*) FROM tb_FarReport" + whereClause;

        int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

        BigDecimal totalCost = getAggregate("SUM(cost)", "", new ArrayList<>());
        BigDecimal totalNBV = getAggregate("SUM(netCost)", "", new ArrayList<>());
        BigDecimal totalDepreciation = getAggregate("SUM(accumulatedDepreciationAmt)", "", new ArrayList<>());

        int offset = page * size;
        String paginationSql = " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(offset);

        String dataSql = "SELECT recordNo, recordDatetime, book, assetId, quantity, description, asset_type, creationDate, " +
                "serialNumber, tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, depreciateFlag, " +
                "cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, assetStatus, value, partNumber, " +
                "vendorName, vendorNumber, mergedCode, costAccount, accumulatedDepreAccount, cipCostAccount, expenseCostCenter, " +
                "expenseAccount, Life, datePlacedInService, cost, nbv, depreciationAmount, ytdDepreciation, depreciationReserve, " +
                "salvageValue, category, categoryDescription, locationSegment1, locationSegment2, locationSegment3, " +
                "locationSegment4, locations, sequenceNumber, createdBy, createdDate, updatedBy, updatedDate, " +
                "monthlyDepreciationAmt, accumulatedDepreciationAmt, depreciationDate, netCost, statusFlag, changedBy, " +
                "insertedBy, financialApproval, changedDate, nodeType FROM tb_FarReport" + whereClause + paginationSql;

        List<Map<String, Object>> data = jdbcTemplate.query(dataSql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
            }
            return row;
        }, dataParams.toArray());

        BigDecimal filteredCost = BigDecimal.ZERO;
        BigDecimal filteredNBV = BigDecimal.ZERO;
        BigDecimal filteredDepreciation = BigDecimal.ZERO;

        for (Map<String, Object> row : data) {
            Object costObj = row.get("cost");
            Object netCostObj = row.get("netCost");
            Object depreObj = row.get("accumulatedDepreciationAmt");

            filteredCost = filteredCost.add(costObj != null && costObj instanceof BigDecimal ? (BigDecimal) costObj : BigDecimal.ZERO);
            filteredNBV = filteredNBV.add(netCostObj != null && netCostObj instanceof BigDecimal ? (BigDecimal) netCostObj : BigDecimal.ZERO);
            filteredDepreciation = filteredDepreciation.add(depreObj != null && depreObj instanceof BigDecimal ? (BigDecimal) depreObj : BigDecimal.ZERO);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        response.put("totalRecords", totalRecords);
        response.put("totalCost", totalCost != null ? totalCost : BigDecimal.ZERO);
        response.put("totalNBV", totalNBV != null ? totalNBV : BigDecimal.ZERO);
        response.put("totalDepreciation", totalDepreciation != null ? totalDepreciation : BigDecimal.ZERO);
        response.put("filteredCost", filteredCost);
        response.put("filteredNBV", filteredNBV);
        response.put("filteredDepreciation", filteredDepreciation);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
        response.put("pageSize", size);
        response.put("currentPage", page);

        return response;
    }
    private BigDecimal getAggregate(String aggregateFunction, String whereClause, List<Object> params) {
        String sql = "SELECT COALESCE(" + aggregateFunction + ", 0) FROM tb_FarReport" + whereClause;
        return jdbcTemplate.queryForObject(sql, params.toArray(), BigDecimal.class);
    }


    @PostMapping(value = "/uploadexcel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JSONObject uploadExcel(@RequestBody List<Map<String, Object>> data) {
        return farReportService.processUpload(data, "Excel");
    }

    @PostMapping(value = "/uploadcsv", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JSONObject uploadCsv(@RequestBody List<Map<String, Object>> data) {
        return farReportService.processUpload(data, "CSV");
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportToExcel(
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String value,
            @RequestParam(required = false, defaultValue = "equals") String operator) {

        StreamingResponseBody responseBody = outputStream -> {
            try {
                // wrap in buffered stream for efficiency
                try (BufferedOutputStream bos = new BufferedOutputStream(outputStream, 32 * 1024)) {
                    farReportService.exportToExcel(bos, column, value, operator);
                    bos.flush();
                }
            } catch (IOException e) {
                LOGGER.error("Error exporting to Excel: {}", e.getMessage(), e);
                // best-effort error output (client may have already closed)
                try {
                    outputStream.write(("Error generating Excel file: " + e.getMessage()).getBytes());
                    outputStream.flush();
                } catch (IOException ignored) { }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"far_reports.xlsx\"")
                .body(responseBody);
    }


}