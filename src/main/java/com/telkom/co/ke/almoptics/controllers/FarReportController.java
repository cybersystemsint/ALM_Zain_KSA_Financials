package com.telkom.co.ke.almoptics.controllers;
import com.telkom.co.ke.almoptics.dto.FarReportExportRequest;
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

    /**
     * Filter FAR Reports with advanced multi-criteria support
     * POST /api/far-reports/filterFarReports
     */
    @PostMapping("/filterFarReports")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterFarReports(
            @RequestBody FarReportExportRequest request,
            @RequestParam(defaultValue = "recordNo") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        try {
            LOGGER.info("=== FAR Report Filter Request ===");
            LOGGER.info("Request: {}", request);

            // Transform simple format to advanced format
            transformSimpleToAdvancedFormat(request);
            // Extract pagination from request body (not query params)
            int page = request.getPage() != null ? request.getPage() : 0;
            int size = request.getSize() != null ? request.getSize() : 100;

            // Validate request
            validateFilterRequest(request, page, size, sortBy);

            if (request.getFilterBy() != null && !request.getFilterBy().isEmpty()) {
                LOGGER.info("Filters applied: {} filter(s)", request.getFilterBy().size());
                request.getFilterBy().forEach((column, criteria) ->
                        LOGGER.info("  - {}: {} {}", column, criteria.getOperator(), criteria.getValue())
                );
            } else {
                LOGGER.info("No filters applied - returning all records");
            }

            // Call service
            Map<String, Object> response = farReportService.filterFarReportsAdvanced(
                    request, page, size, sortBy, sortDir);

            // Even safer - handles null gracefully
            Object dataObj = response.get("data");
            int recordCount = (dataObj instanceof List) ? ((List<?>) dataObj).size() : 0;
            LOGGER.info("Filter completed. {} records returned", recordCount);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid filter request: {}", e.getMessage());
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Invalid filter parameters: " + e.getMessage()),
                    HttpStatus.BAD_REQUEST
            );
        } catch (Exception e) {
            LOGGER.error("Error filtering FAR reports", e);
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Error filtering FAR reports: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
     * Validate filter request parameters
     */
    private void validateFilterRequest(FarReportExportRequest request, int page, int size, String sortBy) {

        // Validate pagination
        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }

        if (size <= 0 || size > 10000) {
            throw new IllegalArgumentException("Page size must be between 1 and 10,000");
        }

        // Validate sort column
        if (!Arrays.asList(FarReportService.EXPECTED_FIELDS).contains(sortBy) && !"recordNo".equals(sortBy)) {
            throw new IllegalArgumentException("Invalid sort column: " + sortBy);
        }

        // Validate filters
        if (request.getFilterBy() != null && !request.getFilterBy().isEmpty()) {

            if (request.getFilterBy().size() > 20) {
                throw new IllegalArgumentException(
                        "Maximum 20 filters allowed. Current: " + request.getFilterBy().size()
                );
            }

            for (Map.Entry<String, FarReportExportRequest.FilterCriteria> entry :
                    request.getFilterBy().entrySet()) {

                String column = entry.getKey();
                FarReportExportRequest.FilterCriteria criteria = entry.getValue();

                // Validate column name
                if (!Arrays.asList(FarReportService.EXPECTED_FIELDS).contains(column)) {
                    throw new IllegalArgumentException("Invalid column name: " + column);
                }

                // Validate operator
                if (criteria.getOperator() != null && !isValidOperator(criteria.getOperator())) {
                    throw new IllegalArgumentException(
                            "Invalid operator: " + criteria.getOperator() + " for column: " + column
                    );
                }

                // Validate value length
                if (criteria.getValue() != null && criteria.getValue().length() > 500) {
                    throw new IllegalArgumentException(
                            "Filter value too long for column: " + column + " (max 500 characters)"
                    );
                }
            }
        }
    }
    /**
     * Transform simple payload format to advanced filterBy format
     * Supports backward compatibility with legacy frontend
     */
    private void transformSimpleToAdvancedFormat(FarReportExportRequest request) {

        // Initialize filterBy if null
        if (request.getFilterBy() == null) {
            request.setFilterBy(new HashMap<>());
        }

        // ========================================================================
        // 1. TRANSFORM dateFrom/dateTo to datePlacedInService filter
        // ========================================================================
        String dateFrom = request.getDateFrom();
        String dateTo = request.getDateTo();

        if ((dateFrom != null && !dateFrom.trim().isEmpty()) ||
                (dateTo != null && !dateTo.trim().isEmpty())) {

            // Don't override if datePlacedInService already exists in filterBy
            if (!request.getFilterBy().containsKey("datePlacedInService")) {

                if (dateFrom != null && !dateFrom.trim().isEmpty() &&
                        dateTo != null && !dateTo.trim().isEmpty()) {
                    // Both dates exist - use BETWEEN
                    LOGGER.info("Transforming dateFrom/dateTo to BETWEEN: {} to {}", dateFrom, dateTo);
                    request.getFilterBy().put("datePlacedInService",
                            new FarReportExportRequest.FilterCriteria("between", dateFrom + "," + dateTo));

                } else if (dateFrom != null && !dateFrom.trim().isEmpty()) {
                    // Only dateFrom - use GTE
                    LOGGER.info("Transforming dateFrom to GTE: {}", dateFrom);
                    request.getFilterBy().put("datePlacedInService",
                            new FarReportExportRequest.FilterCriteria("gte", dateFrom));

                } else if (dateTo != null && !dateTo.trim().isEmpty()) {
                    // Only dateTo - use LTE
                    LOGGER.info("Transforming dateTo to LTE: {}", dateTo);
                    request.getFilterBy().put("datePlacedInService",
                            new FarReportExportRequest.FilterCriteria("lte", dateTo));
                }
            }
        }

        // ========================================================================
        // 2. TRANSFORM columnName/searchQuery to filterBy (legacy support)
        // ========================================================================
        if (request.getColumnName() != null && !request.getColumnName().trim().isEmpty() &&
                request.getSearchQuery() != null && !request.getSearchQuery().trim().isEmpty()) {

            String column = request.getColumnName().trim();
            String query = request.getSearchQuery().trim();

            // Validate column name
            if (Arrays.asList(FarReportService.EXPECTED_FIELDS).contains(column)) {
                // Don't override if column already exists in filterBy
                if (!request.getFilterBy().containsKey(column)) {
                    LOGGER.info("Transforming columnName/searchQuery to CONTAINS: {} = {}", column, query);
                    request.getFilterBy().put(column,
                            new FarReportExportRequest.FilterCriteria("contains", query));
                }
            } else {
                LOGGER.warn("Invalid columnName ignored: {}", column);
            }
        }

        // ========================================================================
        // 3. TRANSFORM assetId to filterBy
        // ========================================================================
        if (request.getAssetId() != null && !request.getAssetId().trim().isEmpty()) {
            // Don't override if assetId already exists in filterBy
            if (!request.getFilterBy().containsKey("assetId")) {
                LOGGER.info("Transforming assetId to EQUALS: {}", request.getAssetId());
                request.getFilterBy().put("assetId",
                        new FarReportExportRequest.FilterCriteria("equals", request.getAssetId().trim()));
            }
        }

        LOGGER.info("After transformation, filterBy contains: {}", request.getFilterBy());
    }

    /**
     * Validate operator (reuse from export controller or create shared utility)
     */
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
                "isNull", "isnull", "isNotNull", "isnotnull",
                "between"
        };

        return Arrays.asList(validOperators).contains(operator);
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