package com.telkom.co.ke.almoptics.controllers;
import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;
import com.telkom.co.ke.almoptics.serviceImplementor.FarReportExportService;
import com.telkom.co.ke.almoptics.serviceImplementor.FarReportService;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;


import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/far-reports")
public class FarReportController {

    private final Logger LOGGER = LogManager.getLogger(FarReportController.class);

    @Autowired
    private FarReportService farReportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FarReportExportService farReportExportService;

    @Autowired
    private FarReportRepository farReportRepository;

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

        String dataSql = "SELECT recordNo, recordDatetime, book, assetId, quantity, description, assetType, creationDate, " +
                "serialNumber, tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, depreciateFlag, " +
                "cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, assetStatus, value, partNumber, " +
                "vendorName, vendorNumber, mergedCode, costAccount, accumulatedDepreAccount, cipCostAccount, expenseCostCenter, " +
                "expenseAccount, Life, datePlacedInService, cost, nbv, depreciationAmount, ytdDepreciation, depreciationReserve, " +
                "salvageValue, category, categoryDescription, locationSegment1, locationSegment2, locationSegment3, " +
                "locationSegment4, locations, sequenceNumber, createdBy, createdDate, updatedBy, updatedDate, " +
                "monthlyDepreciationAmt, accumulatedDepreciationAmt, depreciationDate, netCost, statusFlag, changedBy, " +
                "insertedBy, financialApproval, changedDate, nodeType, mapped FROM tb_FarReport" + whereClause + paginationSql;

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

    @PostMapping("/filterFarReports")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterFarReports(
            @RequestBody Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordNo") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            // Validate page and size
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            // Initialize WHERE clause and parameters
            String whereClause = " WHERE 1=1";
            List<Object> params = new ArrayList<>();

            // Build WHERE clause for filters
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            // String filters (all string columns)
            if (filters.containsKey("book") && !filters.get("book").isEmpty()) {
                whereClause += " AND FR.book = ?";
                params.add(filters.get("book"));
            }
            if (filters.containsKey("assetId") && !filters.get("assetId").isEmpty()) {
                whereClause += " AND FR.assetId = ?";
                params.add(filters.get("assetId"));
            }
            if (filters.containsKey("description") && !filters.get("description").isEmpty()) {
                whereClause += " AND FR.description = ?";
                params.add(filters.get("description"));
            }
            if (filters.containsKey("assetType") && !filters.get("assetType").isEmpty()) {
                whereClause += " AND FR.assetType = ?";
                params.add(filters.get("assetType"));
            }
            if (filters.containsKey("serialNumber") && !filters.get("serialNumber").isEmpty()) {
                whereClause += " AND FR.serialNumber = ?";
                params.add(filters.get("serialNumber"));
            }
            if (filters.containsKey("tagNumber") && !filters.get("tagNumber").isEmpty()) {
                whereClause += " AND FR.tagNumber = ?";
                params.add(filters.get("tagNumber"));
            }
            if (filters.containsKey("picStatus") && !filters.get("picStatus").isEmpty()) {
                whereClause += " AND FR.picStatus = ?";
                params.add(filters.get("picStatus"));
            }
            if (filters.containsKey("linkId") && !filters.get("linkId").isEmpty()) {
                whereClause += " AND FR.linkId = ?";
                params.add(filters.get("linkId"));
            }
            if (filters.containsKey("acceptanceNumber") && !filters.get("acceptanceNumber").isEmpty()) {
                whereClause += " AND FR.acceptanceNumber = ?";
                params.add(filters.get("acceptanceNumber"));
            }
            if (filters.containsKey("depreciateFlag") && !filters.get("depreciateFlag").isEmpty()) {
                whereClause += " AND FR.depreciateFlag = ?";
                params.add(filters.get("depreciateFlag"));
            }
            if (filters.containsKey("cipEu") && !filters.get("cipEu").isEmpty()) {
                whereClause += " AND FR.cipEu = ?";
                params.add(filters.get("cipEu"));
            }
            if (filters.containsKey("invoiceNumber") && !filters.get("invoiceNumber").isEmpty()) {
                whereClause += " AND FR.invoiceNumber = ?";
                params.add(filters.get("invoiceNumber"));
            }
            if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                whereClause += " AND FR.poNumber = ?";
                params.add(filters.get("poNumber"));
            }
            if (filters.containsKey("poLineNumber") && !filters.get("poLineNumber").isEmpty()) {
                whereClause += " AND FR.poLineNumber = ?";
                params.add(filters.get("poLineNumber"));
            }
            if (filters.containsKey("uplLine") && !filters.get("uplLine").isEmpty()) {
                whereClause += " AND FR.uplLine = ?";
                params.add(filters.get("uplLine"));
            }
            if (filters.containsKey("transferToNewFar") && !filters.get("transferToNewFar").isEmpty()) {
                whereClause += " AND FR.transferToNewFar = ?";
                params.add(filters.get("transferToNewFar"));
            }
            if (filters.containsKey("assetStatus") && !filters.get("assetStatus").isEmpty()) {
                whereClause += " AND FR.assetStatus = ?";
                params.add(filters.get("assetStatus"));
            }
            if (filters.containsKey("partNumber") && !filters.get("partNumber").isEmpty()) {
                whereClause += " AND FR.partNumber = ?";
                params.add(filters.get("partNumber"));
            }
            if (filters.containsKey("vendorName") && !filters.get("vendorName").isEmpty()) {
                whereClause += " AND FR.vendorName = ?";
                params.add(filters.get("vendorName"));
            }
            if (filters.containsKey("vendorNumber") && !filters.get("vendorNumber").isEmpty()) {
                whereClause += " AND FR.vendorNumber = ?";
                params.add(filters.get("vendorNumber"));
            }
            if (filters.containsKey("mergedCode") && !filters.get("mergedCode").isEmpty()) {
                whereClause += " AND FR.mergedCode = ?";
                params.add(filters.get("mergedCode"));
            }
            if (filters.containsKey("costAccount") && !filters.get("costAccount").isEmpty()) {
                whereClause += " AND FR.costAccount = ?";
                params.add(filters.get("costAccount"));
            }
            if (filters.containsKey("accumulatedDepreAccount") && !filters.get("accumulatedDepreAccount").isEmpty()) {
                whereClause += " AND FR.accumulatedDepreAccount = ?";
                params.add(filters.get("accumulatedDepreAccount"));
            }
            if (filters.containsKey("cipCostAccount") && !filters.get("cipCostAccount").isEmpty()) {
                whereClause += " AND FR.cipCostAccount = ?";
                params.add(filters.get("cipCostAccount"));
            }
            if (filters.containsKey("expenseCostCenter") && !filters.get("expenseCostCenter").isEmpty()) {
                whereClause += " AND FR.expenseCostCenter = ?";
                params.add(filters.get("expenseCostCenter"));
            }
            if (filters.containsKey("expenseAccount") && !filters.get("expenseAccount").isEmpty()) {
                whereClause += " AND FR.expenseAccount = ?";
                params.add(filters.get("expenseAccount"));
            }
            if (filters.containsKey("category") && !filters.get("category").isEmpty()) {
                whereClause += " AND FR.category = ?";
                params.add(filters.get("category"));
            }
            if (filters.containsKey("categoryDescription") && !filters.get("categoryDescription").isEmpty()) {
                whereClause += " AND FR.categoryDescription = ?";
                params.add(filters.get("categoryDescription"));
            }
            if (filters.containsKey("locationSegment1") && !filters.get("locationSegment1").isEmpty()) {
                whereClause += " AND FR.locationSegment1 = ?";
                params.add(filters.get("locationSegment1"));
            }
            if (filters.containsKey("locationSegment2") && !filters.get("locationSegment2").isEmpty()) {
                whereClause += " AND FR.locationSegment2 = ?";
                params.add(filters.get("locationSegment2"));
            }
            if (filters.containsKey("locationSegment3") && !filters.get("locationSegment3").isEmpty()) {
                whereClause += " AND FR.locationSegment3 = ?";
                params.add(filters.get("locationSegment3"));
            }
            if (filters.containsKey("locationSegment4") && !filters.get("locationSegment4").isEmpty()) {
                whereClause += " AND FR.locationSegment4 = ?";
                params.add(filters.get("locationSegment4"));
            }
            if (filters.containsKey("locations") && !filters.get("locations").isEmpty()) {
                whereClause += " AND FR.locations = ?";
                params.add(filters.get("locations"));
            }
            if (filters.containsKey("createdBy") && !filters.get("createdBy").isEmpty()) {
                whereClause += " AND FR.createdBy = ?";
                params.add(filters.get("createdBy"));
            }
            if (filters.containsKey("updatedBy") && !filters.get("updatedBy").isEmpty()) {
                whereClause += " AND FR.updatedBy = ?";
                params.add(filters.get("updatedBy"));
            }
            if (filters.containsKey("statusFlag") && !filters.get("statusFlag").isEmpty()) {
                whereClause += " AND FR.statusFlag = ?";
                params.add(filters.get("statusFlag"));
            }
            if (filters.containsKey("changedBy") && !filters.get("changedBy").isEmpty()) {
                whereClause += " AND FR.changedBy = ?";
                params.add(filters.get("changedBy"));
            }
            if (filters.containsKey("insertedBy") && !filters.get("insertedBy").isEmpty()) {
                whereClause += " AND FR.insertedBy = ?";
                params.add(filters.get("insertedBy"));
            }
            if (filters.containsKey("financialApproval") && !filters.get("financialApproval").isEmpty()) {
                whereClause += " AND FR.financialApproval = ?";
                params.add(filters.get("financialApproval"));
            }
            if (filters.containsKey("nodeType") && !filters.get("nodeType").isEmpty()) {
                whereClause += " AND FR.nodeType = ?";
                params.add(filters.get("nodeType"));
            }
            if (filters.containsKey("mapped") && !filters.get("mapped").isEmpty()) {
                whereClause += " AND FR.mapped = ?";
                params.add(filters.get("mapped"));
            }

            // Integer numeric filters (whole numbers: quantity, life, sequenceNumber)
            if (filters.containsKey("quantity") && !filters.get("quantity").isEmpty()) {
                try {
                    Integer value = Integer.parseInt(filters.get("quantity"));
                    whereClause += " AND FR.quantity = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid quantity format: " + filters.get("quantity"), e);
                }
            }
            if (filters.containsKey("life") && !filters.get("life").isEmpty()) {
                try {
                    Integer value = Integer.parseInt(filters.get("life"));
                    whereClause += " AND FR.life = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid life format: " + filters.get("life"), e);
                }
            }
            if (filters.containsKey("sequenceNumber") && !filters.get("sequenceNumber").isEmpty()) {
                try {
                    Integer value = Integer.parseInt(filters.get("sequenceNumber"));
                    whereClause += " AND FR.sequenceNumber = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid sequenceNumber format: " + filters.get("sequenceNumber"), e);
                }
            }

            // Double numeric filters (all other numerics: value, cost, nbv, etc.)
            if (filters.containsKey("value") && !filters.get("value").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("value"));
                    whereClause += " AND FR.value = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid value format: " + filters.get("value"), e);
                }
            }
            if (filters.containsKey("cost") && !filters.get("cost").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("cost"));
                    whereClause += " AND FR.cost = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid cost format: " + filters.get("cost"), e);
                }
            }
            if (filters.containsKey("nbv") && !filters.get("nbv").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("nbv"));
                    whereClause += " AND FR.nbv = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid nbv format: " + filters.get("nbv"), e);
                }
            }
            if (filters.containsKey("depreciationAmount") && !filters.get("depreciationAmount").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("depreciationAmount"));
                    whereClause += " AND FR.depreciationAmount = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid depreciationAmount format: " + filters.get("depreciationAmount"), e);
                }
            }
            if (filters.containsKey("ytdDepreciation") && !filters.get("ytdDepreciation").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("ytdDepreciation"));
                    whereClause += " AND FR.ytdDepreciation = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid ytdDepreciation format: " + filters.get("ytdDepreciation"), e);
                }
            }
            if (filters.containsKey("depreciationReserve") && !filters.get("depreciationReserve").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("depreciationReserve"));
                    whereClause += " AND FR.depreciationReserve = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid depreciationReserve format: " + filters.get("depreciationReserve"), e);
                }
            }
            if (filters.containsKey("salvageValue") && !filters.get("salvageValue").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("salvageValue"));
                    whereClause += " AND FR.salvageValue = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid salvageValue format: " + filters.get("salvageValue"), e);
                }
            }
            if (filters.containsKey("monthlyDepreciationAmt") && !filters.get("monthlyDepreciationAmt").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("monthlyDepreciationAmt"));
                    whereClause += " AND FR.monthlyDepreciationAmt = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid monthlyDepreciationAmt format: " + filters.get("monthlyDepreciationAmt"), e);
                }
            }
            if (filters.containsKey("accumulatedDepreciationAmt") && !filters.get("accumulatedDepreciationAmt").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("accumulatedDepreciationAmt"));
                    whereClause += " AND FR.accumulatedDepreciationAmt = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid accumulatedDepreciationAmt format: " + filters.get("accumulatedDepreciationAmt"), e);
                }
            }
            if (filters.containsKey("netCost") && !filters.get("netCost").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("netCost"));
                    whereClause += " AND FR.netCost = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid netCost format: " + filters.get("netCost"), e);
                }
            }

            // Date range filters (for all date columns: recordDatetime, creationDate, picDate, cipDeliveryDate, createdDate, updatedDate, datePlacedInService, depreciationDate, changedDate)
            try {
                // recordDatetime
                if (filters.containsKey("recordDatetimeStart") && !filters.get("recordDatetimeStart").isEmpty()) {
                    whereClause += " AND FR.recordDatetime >= ?";
                    params.add(filters.get("recordDatetimeStart"));
                }
                if (filters.containsKey("recordDatetimeEnd") && !filters.get("recordDatetimeEnd").isEmpty()) {
                    whereClause += " AND FR.recordDatetime <= ?";
                    params.add(filters.get("recordDatetimeEnd"));
                }
                // creationDate
                if (filters.containsKey("creationDateStart") && !filters.get("creationDateStart").isEmpty()) {
                    whereClause += " AND FR.creationDate >= ?";
                    params.add(filters.get("creationDateStart"));
                }
                if (filters.containsKey("creationDateEnd") && !filters.get("creationDateEnd").isEmpty()) {
                    whereClause += " AND FR.creationDate <= ?";
                    params.add(filters.get("creationDateEnd"));
                }
                // picDate
                if (filters.containsKey("picDateStart") && !filters.get("picDateStart").isEmpty()) {
                    whereClause += " AND FR.picDate >= ?";
                    params.add(filters.get("picDateStart"));
                }
                if (filters.containsKey("picDateEnd") && !filters.get("picDateEnd").isEmpty()) {
                    whereClause += " AND FR.picDate <= ?";
                    params.add(filters.get("picDateEnd"));
                }
                // cipDeliveryDate
                if (filters.containsKey("cipDeliveryDateStart") && !filters.get("cipDeliveryDateStart").isEmpty()) {
                    whereClause += " AND FR.cipDeliveryDate >= ?";
                    params.add(filters.get("cipDeliveryDateStart"));
                }
                if (filters.containsKey("cipDeliveryDateEnd") && !filters.get("cipDeliveryDateEnd").isEmpty()) {
                    whereClause += " AND FR.cipDeliveryDate <= ?";
                    params.add(filters.get("cipDeliveryDateEnd"));
                }
                // createdDate
                if (filters.containsKey("createdDateStart") && !filters.get("createdDateStart").isEmpty()) {
                    whereClause += " AND FR.createdDate >= ?";
                    params.add(filters.get("createdDateStart"));
                }
                if (filters.containsKey("createdDateEnd") && !filters.get("createdDateEnd").isEmpty()) {
                    whereClause += " AND FR.createdDate <= ?";
                    params.add(filters.get("createdDateEnd"));
                }
                // updatedDate
                if (filters.containsKey("updatedDateStart") && !filters.get("updatedDateStart").isEmpty()) {
                    whereClause += " AND FR.updatedDate >= ?";
                    params.add(filters.get("updatedDateStart"));
                }
                if (filters.containsKey("updatedDateEnd") && !filters.get("updatedDateEnd").isEmpty()) {
                    whereClause += " AND FR.updatedDate <= ?";
                    params.add(filters.get("updatedDateEnd"));
                }
                // datePlacedInService
                if (filters.containsKey("datePlacedInServiceStart") && !filters.get("datePlacedInServiceStart").isEmpty()) {
                    whereClause += " AND FR.datePlacedInService >= ?";
                    params.add(filters.get("datePlacedInServiceStart"));
                }
                if (filters.containsKey("datePlacedInServiceEnd") && !filters.get("datePlacedInServiceEnd").isEmpty()) {
                    whereClause += " AND FR.datePlacedInService <= ?";
                    params.add(filters.get("datePlacedInServiceEnd"));
                }
                // depreciationDate
                if (filters.containsKey("depreciationDateStart") && !filters.get("depreciationDateStart").isEmpty()) {
                    whereClause += " AND FR.depreciationDate >= ?";
                    params.add(filters.get("depreciationDateStart"));
                }
                if (filters.containsKey("depreciationDateEnd") && !filters.get("depreciationDateEnd").isEmpty()) {
                    whereClause += " AND FR.depreciationDate <= ?";
                    params.add(filters.get("depreciationDateEnd"));
                }
                // changedDate
                if (filters.containsKey("changedDateStart") && !filters.get("changedDateStart").isEmpty()) {
                    whereClause += " AND FR.changedDate >= ?";
                    params.add(filters.get("changedDateStart"));
                }
                if (filters.containsKey("changedDateEnd") && !filters.get("changedDateEnd").isEmpty()) {
                    whereClause += " AND FR.changedDate <= ?";
                    params.add(filters.get("changedDateEnd"));
                }
            } catch (Exception e) {
                LOGGER.error("Error parsing date filters", e);
            }

            // Count total records
            String countSql = "SELECT COUNT(*) FROM `tb_FarReport` FR" + whereClause;
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            // Build pagination
            String paginationSql = "";
            if (size > 0) {
                int offset = page * size;
                paginationSql = " LIMIT ? OFFSET ?";
                params.add(size);
                params.add(offset);
            }

            // Build sorting
            String orderBy = "";
            if (!sortBy.isEmpty()) {
                orderBy = " ORDER BY FR." + sortBy + (sortDir.equalsIgnoreCase("asc") ? " ASC" : " DESC");
            }

            // Main query (all columns via SELECT *)
            String sql = "SELECT * FROM `tb_FarReport` FR" + whereClause + orderBy + paginationSql;

            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("reports", result); // Match frontend's expected key
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", result.size() < size || (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            LOGGER.info("Far Report Filter Query: " + sql);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("Error filtering FAR reports", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering FAR reports: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @GetMapping("/export/far-report")
    public void exportFarReport(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=FAR_Report.xlsx");

            SXSSFWorkbook workbook = new SXSSFWorkbook(100);

            final int MAX_ROWS_PER_SHEET = 1000000; // Leave buffer for header
            int pageSize = 1000;
            int pageNum = 0;
            int currentSheetNum = 1;
            int rowNumInSheet = 0;

            Sheet currentSheet = createNewSheet(workbook, currentSheetNum);
            Page<tb_FarReport> page;

            do {
                page = farReportRepository.findAll(PageRequest.of(pageNum++, pageSize));

                for (tb_FarReport report : page.getContent()) {
                    // Check if we need a new sheet
                    if (rowNumInSheet >= MAX_ROWS_PER_SHEET) {
                        currentSheetNum++;
                        currentSheet = createNewSheet(workbook, currentSheetNum);
                        rowNumInSheet = 0;
                    }

                    Row row = currentSheet.createRow(rowNumInSheet + 1); // +1 for header
                    populateRow(row, report);
                    rowNumInSheet++;
                }

            } while (page.hasNext());

            workbook.write(response.getOutputStream());
            workbook.dispose();
            workbook.close();

        } catch (Exception e) {
            throw new RuntimeException("Failed to export FAR Report", e);
        }
    }

    private Sheet createNewSheet(SXSSFWorkbook workbook, int sheetNum) {
        Sheet sheet = workbook.createSheet("FAR Report " + sheetNum);

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] columns = {
                "Record No", "Record Datetime", "Book", "Asset ID", "Quantity", "Description",
                "Asset Type", "Creation Date", "Serial Number", "Tag Number", "PIC Status",
                "PIC Date", "CIP Delivery Date", "Link ID", "Acceptance Number", "Depreciate Flag",
                "CIP EU", "Invoice Number", "PO Number", "PO Line Number", "UPL Line",
                "Transfer To New FAR", "Asset Status", "Value", "Part Number", "Vendor Name",
                "Vendor Number", "Merged Code", "Cost Account", "Accumulated Depre Account",
                "CIP Cost Account", "Expense Cost Center", "Expense Account", "Life",
                "Date Placed In Service", "Cost", "NBV", "Depreciation Amount", "YTD Depreciation",
                "Depreciation Reserve", "Salvage Value", "Category", "Category Description",
                "Location Segment 1", "Location Segment 2", "Location Segment 3", "Location Segment 4",
                "Locations", "Sequence Number", "Created By", "Created Date", "Updated By",
                "Updated Date", "Monthly Depreciation Amt", "Accumulated Depreciation Amt",
                "Depreciation Date", "Net Cost", "Status Flag", "Changed By", "Inserted By",
                "Financial Approval", "Changed Date", "Node Type"
        };

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        return sheet;
    }

    private void populateRow(Row row, tb_FarReport report) {
        int col = 0;
        createCell(row, col++, report.getRecordNo());
        createCell(row, col++, report.getRecordDatetime());
        createCell(row, col++, report.getBook());
        createCell(row, col++, report.getAssetId());
        createCell(row, col++, report.getQuantity());
        createCell(row, col++, report.getDescription());
        createCell(row, col++, report.getAssetType());
        createCell(row, col++, report.getCreationDate());
        createCell(row, col++, report.getSerialNumber());
        createCell(row, col++, report.getTagNumber());
        createCell(row, col++, report.getPicStatus());
        createCell(row, col++, report.getPicDate());
        createCell(row, col++, report.getCipDeliveryDate());
        createCell(row, col++, report.getLinkId());
        createCell(row, col++, report.getAcceptanceNumber());
        createCell(row, col++, report.getDepreciateFlag());
        createCell(row, col++, report.getCipEu());
        createCell(row, col++, report.getInvoiceNumber());
        createCell(row, col++, report.getPoNumber());
        createCell(row, col++, report.getPoLineNumber());
        createCell(row, col++, report.getUplLine());
        createCell(row, col++, report.getTransferToNewFar());
        createCell(row, col++, report.getAssetStatus());
        createCell(row, col++, report.getValue());
        createCell(row, col++, report.getPartNumber());
        createCell(row, col++, report.getVendorName());
        createCell(row, col++, report.getVendorNumber());
        createCell(row, col++, report.getMergedCode());
        createCell(row, col++, report.getCostAccount());
        createCell(row, col++, report.getAccumulatedDepreAccount());
        createCell(row, col++, report.getCipCostAccount());
        createCell(row, col++, report.getExpenseCostCenter());
        createCell(row, col++, report.getExpenseAccount());
        createCell(row, col++, report.getLife());
        createCell(row, col++, report.getDatePlacedInService());
        createCell(row, col++, report.getCost());
        createCell(row, col++, report.getNbv());
        createCell(row, col++, report.getDepreciationAmount());
        createCell(row, col++, report.getYtdDepreciation());
        createCell(row, col++, report.getDepreciationReserve());
        createCell(row, col++, report.getSalvageValue());
        createCell(row, col++, report.getCategory());
        createCell(row, col++, report.getCategoryDescription());
        createCell(row, col++, report.getLocationSegment1());
        createCell(row, col++, report.getLocationSegment2());
        createCell(row, col++, report.getLocationSegment3());
        createCell(row, col++, report.getLocationSegment4());
        createCell(row, col++, report.getLocations());
        createCell(row, col++, report.getSequenceNumber());
        createCell(row, col++, report.getCreatedBy());
        createCell(row, col++, report.getCreatedDate());
        createCell(row, col++, report.getUpdatedBy());
        createCell(row, col++, report.getUpdatedDate());
        createCell(row, col++, report.getMonthlyDepreciationAmt());
        createCell(row, col++, report.getAccumulatedDepreciationAmt());
        createCell(row, col++, report.getDepreciationDate());
        createCell(row, col++, report.getNetCost());
        createCell(row, col++, report.getStatusFlag());
        createCell(row, col++, report.getChangedBy());
        createCell(row, col++, report.getInsertedBy());
        createCell(row, col++, report.getFinancialApproval());
        createCell(row, col++, report.getChangedDate());
        createCell(row, col++, report.getNodeType());
    }

    private void createCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Date) {
            cell.setCellValue(value.toString());
        } else {
            cell.setCellValue(value.toString());
        }
    }

}