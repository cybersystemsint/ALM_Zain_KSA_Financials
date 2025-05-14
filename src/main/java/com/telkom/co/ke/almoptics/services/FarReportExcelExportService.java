package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

@Service
public class FarReportExcelExportService {

    @Autowired
    private FarReportRepository farReportRepository;

    private static final int PAGE_SIZE = 10000; // Fetch 10,000 records per page
    private static final int RECORDS_PER_SHEET = 1000000; // 1 million records per sheet
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public ByteArrayInputStream exportFarReportToExcel() throws IOException {
        // Create SXSSF Workbook for streaming
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Flush after 100 rows
            long totalRecords = farReportRepository.count();
            int totalPages = (int) Math.ceil((double) totalRecords / PAGE_SIZE);
            int sheetNumber = 0;
            int recordsInCurrentSheet = 0;
            Sheet sheet = null;

            // Define headers
            String[] headers = {
                    "Record No", "Record Datetime", "Book", "Asset ID", "Quantity", "Description",
                    "Creation Date", "Serial Number", "Tag Number", "PIC Status", "PIC Date",
                    "CIP Delivery Date", "Link ID", "Acceptance Number", "Depreciate Flag",
                    "CIP EU", "Invoice Number", "PO Number", "PO Line Number", "UPL Line",
                    "Transfer to New FAR", "Asset Status", "Value", "Part Number", "Vendor Name",
                    "Vendor Number", "Merged Code", "Cost Account", "Accumulated Depre Account",
                    "CIP Cost Account", "Expense Cost Center", "Expense Account", "Life",
                    "Date Placed In Service", "Cost", "NBV", "Depreciation Amount",
                    "YTD Depreciation", "Depreciation Reserve", "Salvage Value", "Category",
                    "Category Description", "Location Segment 1", "Location Segment 2",
                    "Location Segment 3", "Location Segment 4", "Locations", "Sequence Number",
                    "Monthly Depreciation Amt", "Accumulated Depreciation Amt", "Depreciation Date",
                    "Net Cost", "Status Flag", "Changed By", "Inserted By", "Financial Approval",
                    "Changed Date", "Node Type", "Inventory Status"
            };

            for (int page = 0; page < totalPages; page++) {
                Pageable pageable = PageRequest.of(page, PAGE_SIZE);
                Page<tb_FarReport> reportPage = farReportRepository.findAll(pageable);
                List<tb_FarReport> reports = reportPage.getContent();

                for (tb_FarReport report : reports) {
                    // Create new sheet if needed
                    if (sheet == null || recordsInCurrentSheet >= RECORDS_PER_SHEET) {
                        sheet = workbook.createSheet("FAR Report " + (++sheetNumber));
                        recordsInCurrentSheet = 0;

                        // Write headers
                        Row headerRow = sheet.createRow(0);
                        for (int i = 0; i < headers.length; i++) {
                            Cell cell = headerRow.createCell(i);
                            cell.setCellValue(headers[i]);
                        }
                    }

                    // Write data row
                    Row row = sheet.createRow(recordsInCurrentSheet + 1);
                    writeReportToRow(report, row);
                    recordsInCurrentSheet++;
                }
            }

            // Write workbook to byte array
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return new ByteArrayInputStream(out.toByteArray());
            }
        }
    }

    private void writeReportToRow(tb_FarReport report, Row row) {
        int cellIndex = 0;
        setCellValue(row, cellIndex++, report.getRecordNo());
        setCellValue(row, cellIndex++, report.getRecordDatetime());
        setCellValue(row, cellIndex++, report.getBook());
        setCellValue(row, cellIndex++, report.getAssetId());
        setCellValue(row, cellIndex++, report.getQuantity());
        setCellValue(row, cellIndex++, report.getDescription());
        setCellValue(row, cellIndex++, report.getCreationDate());
        setCellValue(row, cellIndex++, report.getSerialNumber());
        setCellValue(row, cellIndex++, report.getTagNumber());
        setCellValue(row, cellIndex++, report.getPicStatus());
        setCellValue(row, cellIndex++, report.getPicDate());
        setCellValue(row, cellIndex++, report.getCipDeliveryDate());
        setCellValue(row, cellIndex++, report.getLinkId());
        setCellValue(row, cellIndex++, report.getAcceptanceNumber());
        setCellValue(row, cellIndex++, report.getDepreciateFlag());
        setCellValue(row, cellIndex++, report.getCipEu());
        setCellValue(row, cellIndex++, report.getInvoiceNumber());
        setCellValue(row, cellIndex++, report.getPoNumber());
        setCellValue(row, cellIndex++, report.getPoLineNumber());
        setCellValue(row, cellIndex++, report.getUplLine());
        setCellValue(row, cellIndex++, report.getTransferToNewFar());
        setCellValue(row, cellIndex++, report.getAssetStatus());
        setCellValue(row, cellIndex++, report.getValue());
        setCellValue(row, cellIndex++, report.getPartNumber());
        setCellValue(row, cellIndex++, report.getVendorName());
        setCellValue(row, cellIndex++, report.getVendorNumber());
        setCellValue(row, cellIndex++, report.getMergedCode());
        setCellValue(row, cellIndex++, report.getCostAccount());
        setCellValue(row, cellIndex++, report.getAccumulatedDepreAccount());
        setCellValue(row, cellIndex++, report.getCipCostAccount());
        setCellValue(row, cellIndex++, report.getExpenseCostCenter());
        setCellValue(row, cellIndex++, report.getExpenseAccount());
        setCellValue(row, cellIndex++, report.getLife());
        setCellValue(row, cellIndex++, report.getDatePlacedInService());
        setCellValue(row, cellIndex++, report.getCost());
        setCellValue(row, cellIndex++, report.getNbv());
        setCellValue(row, cellIndex++, report.getDepreciationAmount());
        setCellValue(row, cellIndex++, report.getYtdDepreciation());
        setCellValue(row, cellIndex++, report.getDepreciationReserve());
        setCellValue(row, cellIndex++, report.getSalvageValue());
        setCellValue(row, cellIndex++, report.getCategory());
        setCellValue(row, cellIndex++, report.getCategoryDescription());
        setCellValue(row, cellIndex++, report.getLocationSegment1());
        setCellValue(row, cellIndex++, report.getLocationSegment2());
        setCellValue(row, cellIndex++, report.getLocationSegment3());
        setCellValue(row, cellIndex++, report.getLocationSegment4());
        setCellValue(row, cellIndex++, report.getLocations());
        setCellValue(row, cellIndex++, report.getSequenceNumber());
        setCellValue(row, cellIndex++, report.getMonthlyDepreciationAmt());
        setCellValue(row, cellIndex++, report.getAccumulatedDepreciationAmt());
        setCellValue(row, cellIndex++, report.getDepreciationDate());
        setCellValue(row, cellIndex++, report.getNetCost());
        setCellValue(row, cellIndex++, report.getStatusFlag());
        setCellValue(row, cellIndex++, report.getChangedBy());
        setCellValue(row, cellIndex++, report.getInsertedBy());
        setCellValue(row, cellIndex++, report.getFinancialApproval());
        setCellValue(row, cellIndex++, report.getChangedDate());
        setCellValue(row, cellIndex++, report.getNodeType());
        setCellValue(row, cellIndex++, report.getInventoryStatus());
    }

    private void setCellValue(Row row, int cellIndex, Object value) {
        Cell cell = row.createCell(cellIndex);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue(DATE_FORMAT.format((java.util.Date) value));
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
