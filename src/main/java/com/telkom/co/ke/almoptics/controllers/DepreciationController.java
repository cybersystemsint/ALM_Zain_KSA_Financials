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
import org.springframework.web.bind.annotation.RequestMapping;
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
private static final String[] DEPRECIATION_COLUMNS = {
    "recordNo","recordDatetime","assetId","description","book","quantity",
    "serialNumber","assetType","depreciationDate","cost","nbv","depreciationAmount",
    "ytdDepreciation","depreciationReserve","salvageValue","category",
    "locationSegment1","locationSegment2","locationSegment3","locationSegment4","locations",
    "sequenceNumber","monthlyDepreciationAmt","accumulatedDepreciationAmt","netCost",
    "statusFlag","changedBy","createdBy","updatedBy"
};
private static final String[] DEPRECIATION_HEADERS = {
    "Record No","Record Datetime","Asset ID","Description","Book","Quantity",
    "Serial Number","Asset Type","Depreciation Date","Cost","Net Book Value","Depreciation Amount",
    "YTD Depreciation","Depreciation Reserve","Salvage Value","Category",
    "Location Segment 1","Location Segment 2","Location Segment 3","Location Segment 4","Locations",
    "Sequence Number","Monthly Depreciation","Accumulated Depreciation","Net Cost",
    "Status","Changed By","Created By","Updated By"
};

    @PostMapping(value = "/depreciation/history/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
public StreamingResponseBody exportDepreciationHistory(
        @RequestBody InventoryRequest req,
        HttpServletResponse response) {
    logger.info("POST /exporting/depreciation-history started, request={}", req);
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=DepreciationHistory_" + System.currentTimeMillis() + ".xlsx");
    response.setHeader("Cache-Control", "no-cache");
    return outputStream -> exportService.exportToExcel(outputStream, "tb_DepreciationHistory", DEPRECIATION_COLUMNS, DEPRECIATION_HEADERS, req);
}
}