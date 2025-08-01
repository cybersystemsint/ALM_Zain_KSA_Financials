package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.services.FarExportService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class FarExportServiceImpl implements FarExportService {

    private final Logger LOGGER = LogManager.getLogger(FarExportServiceImpl.class.getName());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public InputStreamResource exportFarReportToExcel(Map<String, Object> requestParams) {
        try {
            LOGGER.info("Starting FAR Report Excel export with POI 4.1.2 STREAMING approach");

            long totalRecords = getTotalFilteredRecords(requestParams);
            LOGGER.info("Total filtered records to export: " + totalRecords);

            if (totalRecords == 0) {
                throw new RuntimeException("No records found to export");
            }

            // POI 4.1.2: SXSSFWorkbook with 100 rows in memory
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {

                String whereClause = buildWhereClause(requestParams);
                List<Object> params = buildParameters(requestParams);

                // Single query for all data (like the working example)
                String sql = "SELECT recordNo, recordDatetime, book, assetId, quantity, description, " +
                        "creationDate, serialNumber, tagNumber, picStatus, picDate, cipDeliveryDate, " +
                        "linkId, acceptanceNumber, depreciateFlag, cipEu, invoiceNumber, poNumber, " +
                        "poLineNumber, uplLine, transferToNewFar, assetStatus, value, partNumber, " +
                        "vendorName, vendorNumber, mergedCode, costAccount, accumulatedDepreAccount, " +
                        "cipCostAccount, expenseCostCenter, expenseAccount, Life, datePlacedInService, " +
                        "cost, nbv, depreciationAmount, ytdDepreciation, depreciationReserve, " +
                        "salvageValue, category, categoryDescription, locationSegment1, locationSegment2, " +
                        "locationSegment3, locationSegment4, locations, sequenceNumber, " +
                        "createdBy, createdDate, updatedBy, updatedDate, monthlyDepreciationAmt, " +
                        "accumulatedDepreciationAmt, depreciationDate, netCost " +
                        "FROM tb_FarReport " + whereClause;

                LOGGER.info("Executing streaming query for all records");

                final int[] rowNum = {0};
                final int[] sheetNum = {0};
                final Sheet[] currentSheet = {workbook.createSheet("FAR_Report_Sheet_" + (++sheetNum[0]))};

                // Headers array
                String[] headers = {
                        "Record No", "Record DateTime", "Book", "Asset ID", "Quantity", "Description",
                        "Creation Date", "Serial Number", "Tag Number", "PIC Status", "PIC Date",
                        "CIP Delivery Date", "Link ID", "Acceptance Number", "Depreciate Flag",
                        "CIP EU", "Invoice Number", "PO Number", "PO Line Number", "UPL Line",
                        "Transfer To New FAR", "Asset Status", "Value", "Part Number", "Vendor Name",
                        "Vendor Number", "Merged Code", "Cost Account", "Accumulated Depre Account",
                        "CIP Cost Account", "Expense Cost Center", "Expense Account", "Life",
                        "Date Placed In Service", "Cost", "NBV", "Depreciation Amount",
                        "YTD Depreciation", "Depreciation Reserve", "Salvage Value", "Category",
                        "Category Description", "Location Segment 1", "Location Segment 2",
                        "Location Segment 3", "Location Segment 4", "Locations", "Sequence Number",
                        "Created By", "Created Date", "Updated By", "Updated Date",
                        "Monthly Depreciation Amount", "Accumulated Depreciation Amount",
                        "Depreciation Date", "Net Cost"
                };

                // Create first header row
                createHeaderRow(currentSheet[0], headers, rowNum);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");

                LOGGER.info("Starting to process records...");

                // Process all records in streaming fashion
                jdbcTemplate.query(sql, rs -> {
                    try {
                        // Check if we need a new sheet (Excel limit ~1M rows)
                        if (rowNum[0] >= 1000000) {
                            currentSheet[0] = workbook.createSheet("FAR_Report_Sheet_" + (++sheetNum[0]));
                            rowNum[0] = 0;
                            createHeaderRow(currentSheet[0], headers, rowNum);
                            LOGGER.info("Created new sheet: " + sheetNum[0]);
                        }

                        Row dataRow = currentSheet[0].createRow(rowNum[0]++);
                        populateDataRow(dataRow, rs, dateFormat, dateOnlyFormat);

                        // Log progress every 50k records
                        if (rowNum[0] % 50000 == 0) {
                            LOGGER.info("Processed " + ((sheetNum[0] - 1) * 1000000 + rowNum[0]) + " total records");
                        }

                    } catch (SQLException e) {
                        LOGGER.error("Error processing row: " + e.getMessage(), e);
                    }
                }, params.toArray());

                LOGGER.info("Completed processing. Total sheets: " + sheetNum[0] + ", Final sheet rows: " + rowNum[0]);

                // Convert to byte array and return
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    LOGGER.info("Writing workbook to byte array...");
                    workbook.write(baos);

                    // POI 4.1.2: dispose() to cleanup temp files
                    workbook.dispose();

                    ByteArrayInputStream inputStream = new ByteArrayInputStream(baos.toByteArray());
                    LOGGER.info("FAR Report Excel export completed successfully. File size: " + baos.size() + " bytes");
                    return new InputStreamResource(inputStream);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error during FAR Report Excel export: " + e.getMessage(), e);
            throw new RuntimeException("Failed to export FAR Report to Excel: " + e.getMessage(), e);
        }
    }
    @Override
    public byte[] exportFarReportToCsv(Map<String, Object> requestParams) {
        try {
            LOGGER.info("Starting FAR Report CSV export");

            StringWriter writer = new StringWriter();
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                    .withHeader("Record No", "Record DateTime", "Book", "Asset ID", "Quantity", "Description",
                            "Creation Date", "Serial Number", "Tag Number", "PIC Status", "PIC Date",
                            "CIP Delivery Date", "Link ID", "Acceptance Number", "Depreciate Flag",
                            "CIP EU", "Invoice Number", "PO Number", "PO Line Number", "UPL Line",
                            "Transfer To New FAR", "Asset Status", "Value", "Part Number", "Vendor Name",
                            "Vendor Number", "Merged Code", "Cost Account", "Accumulated Depre Account",
                            "CIP Cost Account", "Expense Cost Center", "Expense Account", "Life",
                            "Date Placed In Service", "Cost", "NBV", "Depreciation Amount",
                            "YTD Depreciation", "Depreciation Reserve", "Salvage Value", "Category",
                            "Category Description", "Location Segment 1", "Location Segment 2",
                            "Location Segment 3", "Location Segment 4", "Locations", "Sequence Number",
                            "Created By", "Created Date", "Updated By", "Updated Date",
                            "Monthly Depreciation Amount", "Accumulated Depreciation Amount",
                            "Depreciation Date", "Net Cost"));

            String whereClause = buildWhereClause(requestParams);
            List<Object> params = buildParameters(requestParams);

            String sql = "SELECT recordNo, recordDatetime, book, assetId, quantity, description, " +
                    "creationDate, serialNumber, tagNumber, picStatus, picDate, cipDeliveryDate, " +
                    "linkId, acceptanceNumber, depreciateFlag, cipEu, invoiceNumber, poNumber, " +
                    "poLineNumber, uplLine, transferToNewFar, assetStatus, value, partNumber, " +
                    "vendorName, vendorNumber, mergedCode, costAccount, accumulatedDepreAccount, " +
                    "cipCostAccount, expenseCostCenter, expenseAccount, Life, datePlacedInService, " +
                    "cost, nbv, depreciationAmount, ytdDepreciation, depreciationReserve, " +
                    "salvageValue, category, categoryDescription, locationSegment1, locationSegment2, " +
                    "locationSegment3, locationSegment4, locations, sequenceNumber, " +
                    "createdBy, createdDate, updatedBy, updatedDate, monthlyDepreciationAmt, " +
                    "accumulatedDepreciationAmt, depreciationDate, netCost " +
                    "FROM tb_FarReport " + whereClause;

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");

            jdbcTemplate.query(sql, rs -> {
                try {
                    csvPrinter.printRecord(
                            rs.getObject("recordNo"),
                            rs.getTimestamp("recordDatetime") != null ? dateFormat.format(rs.getTimestamp("recordDatetime")) : "",
                            rs.getString("book"),
                            rs.getString("assetId"),
                            rs.getObject("quantity"),
                            rs.getString("description"),
                            rs.getDate("creationDate") != null ? dateOnlyFormat.format(rs.getDate("creationDate")) : "",
                            rs.getString("serialNumber"),
                            rs.getString("tagNumber"),
                            rs.getString("picStatus"),
                            rs.getDate("picDate") != null ? dateOnlyFormat.format(rs.getDate("picDate")) : "",
                            rs.getDate("cipDeliveryDate") != null ? dateOnlyFormat.format(rs.getDate("cipDeliveryDate")) : "",
                            rs.getString("linkId"),
                            rs.getString("acceptanceNumber"),
                            rs.getString("depreciateFlag"),
                            rs.getString("cipEu"),
                            rs.getString("invoiceNumber"),
                            rs.getString("poNumber"),
                            rs.getString("poLineNumber"),
                            rs.getString("uplLine"),
                            rs.getString("transferToNewFar"),
                            rs.getString("assetStatus"),
                            rs.getObject("value"),
                            rs.getString("partNumber"),
                            rs.getString("vendorName"),
                            rs.getString("vendorNumber"),
                            rs.getString("mergedCode"),
                            rs.getString("costAccount"),
                            rs.getString("accumulatedDepreAccount"),
                            rs.getString("cipCostAccount"),
                            rs.getString("expenseCostCenter"),
                            rs.getString("expenseAccount"),
                            rs.getObject("Life"),
                            rs.getDate("datePlacedInService") != null ? dateOnlyFormat.format(rs.getDate("datePlacedInService")) : "",
                            rs.getObject("cost"),
                            rs.getObject("nbv"),
                            rs.getObject("depreciationAmount"),
                            rs.getObject("ytdDepreciation"),
                            rs.getObject("depreciationReserve"),
                            rs.getObject("salvageValue"),
                            rs.getString("category"),
                            rs.getString("categoryDescription"),
                            rs.getString("locationSegment1"),
                            rs.getString("locationSegment2"),
                            rs.getString("locationSegment3"),
                            rs.getString("locationSegment4"),
                            rs.getString("locations"),
                            rs.getObject("sequenceNumber"),
                            rs.getString("createdBy"),
                            rs.getDate("createdDate") != null ? dateOnlyFormat.format(rs.getDate("createdDate")) : "",
                            rs.getString("updatedBy"),
                            rs.getDate("updatedDate") != null ? dateOnlyFormat.format(rs.getDate("updatedDate")) : "",
                            rs.getObject("monthlyDepreciationAmt"),
                            rs.getObject("accumulatedDepreciationAmt"),
                            rs.getDate("depreciationDate") != null ? dateOnlyFormat.format(rs.getDate("depreciationDate")) : "",
                            rs.getObject("netCost")
                    );
                } catch (Exception e) {
                    LOGGER.error("Error writing CSV row: " + e.getMessage(), e);
                }
            }, params.toArray());

            csvPrinter.flush();
            return writer.toString().getBytes();

        } catch (Exception e) {
            LOGGER.error("Error during CSV export: " + e.getMessage(), e);
            throw new RuntimeException("Failed to export FAR Report to CSV: " + e.getMessage(), e);
        }
    }
    private void createHeaderRow(Sheet sheet, String[] headers, int[] rowNum) {
        Row headerRow = sheet.createRow(rowNum[0]++);

        // POI 4.1.2 compatible header styling
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        Font headerFont = sheet.getWorkbook().createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void populateDataRow(Row dataRow, ResultSet rs, SimpleDateFormat dateFormat, SimpleDateFormat dateOnlyFormat) throws SQLException {
        int cellNum = 0;

        // Populate all cells - optimized for speed (no styling)
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("recordNo"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getTimestamp("recordDatetime"), dateFormat);
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("book"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("assetId"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("quantity"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("description"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getDate("creationDate"), dateOnlyFormat);
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("serialNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("tagNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("picStatus"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getDate("picDate"), dateOnlyFormat);
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getDate("cipDeliveryDate"), dateOnlyFormat);
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("linkId"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("acceptanceNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("depreciateFlag"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("cipEu"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("invoiceNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("poNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("poLineNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("uplLine"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("transferToNewFar"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("assetStatus"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("value"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("partNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("vendorName"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("vendorNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("mergedCode"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("costAccount"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("accumulatedDepreAccount"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("cipCostAccount"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("expenseCostCenter"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("expenseAccount"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("Life"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getDate("datePlacedInService"), dateOnlyFormat);
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("cost"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("nbv"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("depreciationAmount"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("ytdDepreciation"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("depreciationReserve"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("salvageValue"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("category"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("categoryDescription"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("locationSegment1"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("locationSegment2"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("locationSegment3"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("locationSegment4"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("locations"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("sequenceNumber"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("createdBy"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getDate("createdDate"), dateOnlyFormat);
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getString("updatedBy"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getDate("updatedDate"), dateOnlyFormat);
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("monthlyDepreciationAmt"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("accumulatedDepreciationAmt"));
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getDate("depreciationDate"), dateOnlyFormat);
        setCellValueSafe(dataRow.createCell(cellNum++), rs.getObject("netCost"));
    }

    // Safe cell value setter - handles nulls gracefully
    private void setCellValueSafe(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    // Date-specific setter
    private void setCellValueSafe(Cell cell, Date date, SimpleDateFormat format) {
        if (date != null && format != null) {
            cell.setCellValue(format.format(date));
        } else {
            cell.setCellValue("");
        }
    }

    @Override
    public long getTotalFilteredRecords(Map<String, Object> requestParams) {
        try {
            String whereClause = buildWhereClause(requestParams);
            List<Object> params = buildParameters(requestParams);

            String countSql = "SELECT COUNT(*) FROM tb_FarReport " + whereClause;
            LOGGER.info("Count SQL: " + countSql);

            Long result = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
            return result != null ? result : 0L;

        } catch (Exception e) {
            LOGGER.error("Error getting total records: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get record count: " + e.getMessage(), e);
        }
    }

    private String buildWhereClause(Map<String, Object> requestParams) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");

        String assetId = (String) requestParams.get("assetId");
        String columnName = (String) requestParams.get("columnName");
        String searchQuery = (String) requestParams.get("searchQuery");
        String dateFrom = (String) requestParams.get("dateFrom");
        String dateTo = (String) requestParams.get("dateTo");

        if (assetId != null && !assetId.trim().isEmpty()) {
            whereClause.append(" AND assetId = ? ");
        }

        if (columnName != null && !columnName.trim().isEmpty() &&
                searchQuery != null && !searchQuery.trim().isEmpty() &&
                !columnName.equalsIgnoreCase("recordDatetime")) {
            whereClause.append(" AND ").append(columnName).append(" LIKE ? ");
        }

        if (dateFrom != null && !dateFrom.trim().isEmpty()) {
            whereClause.append(" AND recordDatetime >= ? ");
        }

        if (dateTo != null && !dateTo.trim().isEmpty()) {
            whereClause.append(" AND recordDatetime <= ? ");
        }

        return whereClause.toString();
    }

    private List<Object> buildParameters(Map<String, Object> requestParams) {
        List<Object> params = new ArrayList<>();

        String assetId = (String) requestParams.get("assetId");
        String columnName = (String) requestParams.get("columnName");
        String searchQuery = (String) requestParams.get("searchQuery");
        String dateFrom = (String) requestParams.get("dateFrom");
        String dateTo = (String) requestParams.get("dateTo");

        if (assetId != null && !assetId.trim().isEmpty()) {
            params.add(assetId);
        }

        if (columnName != null && !columnName.trim().isEmpty() &&
                searchQuery != null && !searchQuery.trim().isEmpty() &&
                !columnName.equalsIgnoreCase("recordDatetime")) {
            params.add("%" + searchQuery + "%");
        }

        if (dateFrom != null && !dateFrom.trim().isEmpty()) {
            params.add(dateFrom);
        }

        if (dateTo != null && !dateTo.trim().isEmpty()) {
            params.add(dateTo);
        }

        return params;
    }
}