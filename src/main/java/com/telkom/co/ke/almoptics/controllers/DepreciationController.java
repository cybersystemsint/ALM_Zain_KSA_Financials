package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.DepreciationScheduler;
import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.entities.DepreciationHistory;
import com.telkom.co.ke.almoptics.serviceImplementor.UnmappedInventoryExportService;
import com.telkom.co.ke.almoptics.services.DepreciationHistoryService;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
public class DepreciationController {

    private static final Logger logger = LogManager.getLogger(DepreciationController.class);

    @Autowired
    private DepreciationScheduler depreciationScheduler;
    @Autowired
    private DepreciationHistoryService depreciationHistoryService;
    @Autowired
    private UnmappedInventoryExportService exportService;

    // -------------------------------------------------------------------------
    // All columns mapped 1-to-1 with DepreciationHistory entity fields.
    // -------------------------------------------------------------------------
    private static final String[] DEPRECIATION_COLUMNS = {
        "recordNo",
        "recordDatetime",
        "book",
        "assetId",
        "quantity",
        "description",
        "creationDate",
        "serialNumber",
        "assetType",
        "tagNumber",
        "picStatus",
        "picDate",
        "cipDeliveryDate",
        "linkId",
        "acceptanceNumber",
        "depreciateFlag",
        "cipEu",
        "invoiceNumber",
        "poNumber",
        "poLineNumber",
        "uplLine",
        "transferToNewFar",
        "assetStatus",
        "value",
        "partNumber",
        "vendorName",
        "vendorNumber",
        "mergedCode",
        "createdDate",
        "updatedDate",
        "costAccount",
        "accumulatedDepreAccount",
        "cipCostAccount",
        "expenseCostCenter",
        "expenseAccount",
        "life",
        "datePlacedInService",
        "cost",
        "nbv",
        "depreciationAmount",
        "ytdDepreciation",
        "depreciationReserve",
        "salvageValue",
        "category",
        "categoryDescription",
        "locationSegment1",
        "locationSegment2",
        "locationSegment3",
        "locationSegment4",
        "locations",
        "sequenceNumber",
        "monthlyDepreciationAmt",
        "accumulatedDepreciationAmt",
        "depreciationDate",
        "netCost",
        "statusFlag",
        "changedBy",
        "insertedBy",
        "financialApproval",
        "changedDate",
        "nodeType",
        "createdBy",
        "updatedBy",
        "mapped"
    };

    private static final String[] DEPRECIATION_HEADERS = {
        "Sequence No",          // generated in Java by service layer (prepended)
        "Record No",
        "Record Datetime",
        "Book",
        "Asset ID",
        "Quantity",
        "Description",
        "Creation Date",
        "Serial Number",
        "Asset Type",
        "Tag Number",
        "PIC Status",
        "PIC Date",
        "CIP Delivery Date",
        "Link ID",
        "Acceptance Number",
        "Depreciate Flag",
        "CIP EU",
        "Invoice Number",
        "PO Number",
        "PO Line Number",
        "UPL Line",
        "Transfer To New FAR",
        "Asset Status",
        "Value",
        "Part Number",
        "Vendor Name",
        "Vendor Number",
        "Merged Code",
        "Created Date",
        "Updated Date",
        "Cost Account",
        "Accumulated Depre Account",
        "CIP Cost Account",
        "Expense Cost Center",
        "Expense Account",
        "Life",
        "Date Placed In Service",
        "Cost",
        "Net Book Value",
        "Depreciation Amount",
        "YTD Depreciation",
        "Depreciation Reserve",
        "Salvage Value",
        "Category",
        "Category Description",
        "Location Segment 1",
        "Location Segment 2",
        "Location Segment 3",
        "Location Segment 4",
        "Locations",
        "Sequence Number",
        "Monthly Depreciation Amt",
        "Accumulated Depreciation Amt",
        "Depreciation Date",
        "Net Cost",
        "Status Flag",
        "Changed By",
        "Inserted By",
        "Financial Approval",
        "Changed Date",
        "Node Type",
        "Created By",
        "Updated By",
        "Mapped"
    };

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/depreciation/run")
    public ResponseEntity<String> runDepreciationManually() {
        try {
            logger.info("Manual depreciation process triggered via API.");
            depreciationScheduler.processDepreciation();
            return ResponseEntity.ok("Depreciation process completed successfully.");
        } catch (Exception e) {
            logger.error("Error during manual depreciation run: ", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Depreciation process failed: " + e.getMessage());
        }
    }

    @PostMapping("/depreciation/history")
    public Page<DepreciationHistory> getPagedDepreciationHistory(@RequestBody InventoryRequest req) {
        logger.info("FETCH /depreciation/history called, params: page={}, size={}, searchColumn={}, searchQuery={}, filterBy={}",
                req.getPage(), req.getSize(), req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy());
        try {
            Page<DepreciationHistory> result = depreciationHistoryService.fetchPaged(req);
            logger.info("Returning {} records for page {}.", result.getNumberOfElements(), req.getPage());
            return result;
        } catch (Exception e) {
            logger.error("Error in /depreciation/history endpoint: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping(value = "/depreciation/history/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public StreamingResponseBody exportDepreciationHistory(
            @RequestBody InventoryRequest req,
            HttpServletResponse response) {
        logger.info("POST /depreciation/history/export started, request={}", req);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=DepreciationHistory_" + System.currentTimeMillis() + ".xlsx");
        response.setHeader("Cache-Control", "no-cache");
        return outputStream -> {
            try {
                exportService.exportToExcel(
                    outputStream,
                    "tb_DepreciationHistory",
                    DEPRECIATION_COLUMNS,
                    DEPRECIATION_HEADERS,
                    req
                );
            } catch (Exception e) {
                logger.error("Export failed for depreciation history", e);
                response.setContentType("text/plain");
                outputStream.write(("Export failed: " + e.getMessage()).getBytes());
            }
        };
    }
}