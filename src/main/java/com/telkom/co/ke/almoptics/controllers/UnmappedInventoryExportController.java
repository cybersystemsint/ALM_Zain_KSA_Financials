package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.serviceImplementor.UnmappedInventoryExportService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;

@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
public class UnmappedInventoryExportController {
    private static final Logger logger = LogManager.getLogger(UnmappedInventoryExportController.class);

    @Autowired
    private UnmappedInventoryExportService exportService;

    // "rowNumber" removed — sequence number is generated in the service layer
    private static final String[] ACTIVE_COLUMNS = {
        "nodeId", "nodeName", "nodeType", "serialNumber",
        "model", "partNumber", "siteId", "manufacturer", "description",
        "manufacturingDate", "installationDate", "assetInsertionDate"
    };
    private static final String[] ACTIVE_HEADERS = {
        "Sequence Number", "Node ID", "Node Name", "Node Type", "Serial Number",
        "Model", "Part Number", "Site ID", "Manufacturer", "Description",
        "Manufacturing Date", "Installation Date", "Asset Insertion Date"
    };

    // "rowNumber" removed — sequence number is generated in the service layer
    private static final String[] PASSIVE_COLUMNS = {
        "inventoryId", "objectId", "elementType", "parentName",
        "siteId", "itemBarCode", "serialNumber", "model", "note", "part", "uom",
        "entryUser", "entryDate", "itemStatus", "categoryInNEP", "scrapStatus", "inventoryType",
        "locationSubType", "locationClassification", "itemClassification",
        "itemClassification2", "notes", "prPoNo"
    };
    private static final String[] PASSIVE_HEADERS = {
        "Sequence Number", "Inventory ID", "Object ID", "Element Type", "Parent Name",
        "Site ID", "Item Bar Code", "Serial Number", "Model", "Note", "Part", "UOM",
        "Entry User", "Entry Date", "Item Status", "Category (NEP)", "Scrap Status", "Inventory Type",
        "Location Sub Type", "Location Classification", "Item Classification",
        "Item Classification 2", "Notes", "PR/PO No"
    };

    // "rowNumber" removed — sequence number is generated in the service layer
    private static final String[] IT_COLUMNS = {
        "objectId", "siteId", "hostSerialNumber", "inventoryTypeId", "inventoryType",
        "hostTypeName", "firstScan", "ipAddress", "osId", "osName", "hardwareVendorId",
        "hardwareVendorName", "model", "virtual", "hostTypeId", "category"
    };
    private static final String[] IT_HEADERS = {
        "Sequence Number", "Object ID", "Site ID", "Host Serial Number", "Inventory Type ID", "Inventory Type",
        "Host Type Name", "First Scan", "IP Address", "OS ID", "OS Name", "Hardware Vendor ID",
        "Hardware Vendor Name", "Model", "Virtual", "Host Type ID", "Category"
    };

    @PostMapping(value = "/active-inventories/unmapped/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public StreamingResponseBody exportActive(
            @RequestBody InventoryRequest req,
            HttpServletResponse response) {
        logger.info("POST /active-inventories/unmapped/export started, request={}", req);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=UnmappedActive_" + System.currentTimeMillis() + ".xlsx");
        response.setHeader("Cache-Control", "no-cache");
        return outputStream -> {
            try {
                exportService.exportToExcel(outputStream, "tb_unmapped_active_inventory", ACTIVE_COLUMNS, ACTIVE_HEADERS, req);
            } catch (Exception e) {
                logger.error("Export failed for active inventory", e);
                response.setContentType("text/plain");
                outputStream.write(("Export failed: " + e.getMessage()).getBytes());
            }
        };
    }

    @PostMapping(value = "/passive-inventories/unmapped/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public StreamingResponseBody exportPassive(
            @RequestBody InventoryRequest req,
            HttpServletResponse response) {
        logger.info("POST /passive-inventories/unmapped/export started, request={}", req);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=UnmappedPassive_" + System.currentTimeMillis() + ".xlsx");
        response.setHeader("Cache-Control", "no-cache");
        return outputStream -> {
            try {
                exportService.exportToExcel(outputStream, "tb_unmapped_passive_inventory", PASSIVE_COLUMNS, PASSIVE_HEADERS, req);
            } catch (Exception e) {
                logger.error("Export failed for passive inventory", e);
                response.setContentType("text/plain");
                outputStream.write(("Export failed: " + e.getMessage()).getBytes());
            }
        };
    }

    @PostMapping(value = "/it-inventories/unmapped/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public StreamingResponseBody exportIT(
            @RequestBody InventoryRequest req,
            HttpServletResponse response) {
        logger.info("POST /it-inventories/unmapped/export started, request={}", req);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=UnmappedIT_" + System.currentTimeMillis() + ".xlsx");
        response.setHeader("Cache-Control", "no-cache");
        return outputStream -> {
            try {
                exportService.exportToExcel(outputStream, "tb_unmapped_IT_Inventory", IT_COLUMNS, IT_HEADERS, req);
            } catch (Exception e) {
                logger.error("Export failed for IT inventory", e);
                response.setContentType("text/plain");
                outputStream.write(("Export failed: " + e.getMessage()).getBytes());
            }
        };
    }
}