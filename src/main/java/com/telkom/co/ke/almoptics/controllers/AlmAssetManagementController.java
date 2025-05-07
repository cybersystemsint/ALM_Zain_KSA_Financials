/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.controllers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.telkom.co.ke.almoptics.entities.tbLicense;
import com.telkom.co.ke.almoptics.entities.tbNode;
import com.telkom.co.ke.almoptics.entities.tbNodeType;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.entities.tbUnmappedActiveAsset;
import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.entities.tb_Asset;
import com.telkom.co.ke.almoptics.entities.tb_Asset_Allocation;
import com.telkom.co.ke.almoptics.entities.tb_Asset_Journal;
import com.telkom.co.ke.almoptics.entities.tb_Item;
import com.telkom.co.ke.almoptics.services.AssetAllocationService;
import com.telkom.co.ke.almoptics.services.AssetJournalService;
import com.telkom.co.ke.almoptics.services.AssetService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import com.telkom.co.ke.almoptics.services.FarReportService;
import com.telkom.co.ke.almoptics.services.ItemService;
import com.telkom.co.ke.almoptics.services.LicenseService;
import com.telkom.co.ke.almoptics.services.MyAsyncService;
import com.telkom.co.ke.almoptics.services.NodeTypeService;
import com.telkom.co.ke.almoptics.services.UnmappedActiveAssetService;
import com.telkom.co.ke.almoptics.services.tbNodeService;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.YearMonth;
import org.springframework.web.bind.annotation.GetMapping;

/**
 *
 * @author jgithu
 */
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping({"/"})
public class AlmAssetManagementController {

    private final Logger LOGGER = LogManager.getLogger(com.telkom.co.ke.almoptics.controllers.AlmAssetManagementController.class.getName());

    private final AssetAllocationService assetAllocationService;

    private final AssetJournalService assetJournalService;

    private final AssetService assetService;

    private final JdbcTemplate jdbcTemplate;

    private final ItemService itemservice;

    private MyAsyncService myAsyncService;

    private final FinancialReportService financialReportService;

    private final FarReportService farReportService;

    private final UnmappedActiveAssetService unmappedService;

    private final LicenseService licenseService;

    private final tbNodeService nodeService;

    private final NodeTypeService nodeTypeService;

    @Autowired
    public AlmAssetManagementController(NodeTypeService nodeTypeService, tbNodeService nodeService, LicenseService licenseService, UnmappedActiveAssetService unmappedService, JdbcTemplate jdbcTemplate, FinancialReportService financialReportService, ItemService itemservice, MyAsyncService myAsyncService, AssetAllocationService assetAllocationService, AssetJournalService assetJournalService, AssetService assetService, FarReportService farReportService) {
        this.nodeTypeService = nodeTypeService;
        this.nodeService = nodeService;
        this.licenseService = licenseService;
        this.unmappedService = unmappedService;
        this.jdbcTemplate = jdbcTemplate;
        this.financialReportService = financialReportService;
        this.assetAllocationService = assetAllocationService;
        this.assetJournalService = assetJournalService;
        this.assetService = assetService;
        this.itemservice = itemservice;
        this.myAsyncService = myAsyncService;
        this.farReportService = farReportService;
    }

    //UPLOAD LICENSES WHICH HAVE NOT BEEN DISCOVERED AT NEP
    @PostMapping(value = "uploadLicenses", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public JSONObject uploadLicenses(@RequestBody String req, HttpServletResponse httpResponse) throws ParseException {
        JSONObject jsonObjectResponse = new JSONObject();
        long recordNo = 0;
        String licenseID = "";
        String nodeId = "";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        LocalDateTime now = LocalDateTime.now();
        java.util.Date parsedDate = format.parse(now.toString());
        java.sql.Date newDate = new java.sql.Date(parsedDate.getTime());
        String ipaddress = getIPAddress();
        try {

            org.json.JSONArray jsonArray = new org.json.JSONArray(req);
            for (int i = 0; i < jsonArray.length(); i++) {
                org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
                this.LOGGER.info("RECEIVED LICENSES REQUEST" + req);
                recordNo = Integer.parseInt(jsonObject.getString("recordNo"));
                licenseID = jsonObject.getString("licenseId");
                nodeId = jsonObject.getString("nodeId");
                JSONObject assetTrack = new JSONObject();
                if (recordNo == 0) {
                    if (!licenseID.equalsIgnoreCase("")) {
                        List<tbLicense> licenseList = this.licenseService.findByLicenseId(licenseID);
                        if (!licenseList.isEmpty()) {
                            jsonObjectResponse.put("error", "Error occured, License ID already exist " + licenseID);
                        } else {
                            ///check if the node exists in Tb Nodes first , if not create it 
                            List<tbNode> nodelist = this.nodeService.findByNode(nodeId);
                            if (nodelist.isEmpty()) {
                                //Onboard the node here
                                tbNode ndeNode = new tbNode();
                                ndeNode.setRecordDateTime(newDate);
                                ndeNode.setNode(nodeId);
                                ndeNode.setSiteId(Integer.parseInt(jsonObject.getString("siteId")));
                                //get node type ID 
                                tbNodeType nodety = this.nodeTypeService.findByNodeType(jsonObject.getString("nodeType"));
                                ndeNode.setNodeTypeId(nodety.getId());
                                nodeService.save(ndeNode);
                            }
                            tbLicense license = new tbLicense();
                            license.setNodeName(jsonObject.getString("nodeId"));
                            license.setNodeName(jsonObject.getString("nodeType"));
                            license.setNeSiteName(jsonObject.getString("siteId"));
                            license.setSiteId(jsonObject.getString("siteId"));
                            license.setZone(jsonObject.getString("zone"));
                            license.setLicenseId(jsonObject.getString("licenseId"));
                            license.setLicenseDetail(jsonObject.getString("licenseDetail"));
                            license.setAllocated(jsonObject.getInt("allocated"));
                            license.setUsage(jsonObject.getInt("usage"));
                            license.setUsagePercentage(jsonObject.getDouble("usagePercentage"));
                            license.setConfig(jsonObject.getString("config"));
                            license.setUnit(jsonObject.getString("unit"));
                            String insertDate = jsonObject.getString("insertDate");
                            String lastChangeDate = jsonObject.getString("lastChangeDate");
                            java.util.Date newInsDate = format.parse(insertDate);
                            java.sql.Date newDateInsertDate = new java.sql.Date(newInsDate.getTime());
                            license.setInsertDate(newDateInsertDate);
                            java.util.Date newChDate = format.parse(lastChangeDate);
                            java.sql.Date newChangedDate = new java.sql.Date(newChDate.getTime());
                            license.setLastChangeDate(newChangedDate);
                            Double licenseValue = jsonObject.optDouble("licenseDetailValue", 0.0);
                            license.setLicenseDetailValue(licenseValue);
                            license.setTechnology(jsonObject.getString("technology"));
                            license.setManufacturer(jsonObject.getString("manufacturer"));
                            license.setCreatedById(jsonObject.getInt("createdById"));
                            license.setCreatedByName(jsonObject.getString("createdByName").trim());
                            this.licenseService.save(license);

                            assetTrack.put("serialNumber", jsonObject.getString("licenseId"));
                            assetTrack.put("siteId", jsonObject.getString("siteId"));
                            assetTrack.put("actionType", "ADD LICENCE ID " + jsonObject.getString("licenseId"));
                            assetTrack.put("username", jsonObject.getString("createdByName"));
                            org.json.JSONArray assettrackRequest = new org.json.JSONArray();
                            assettrackRequest.put(assetTrack);
                            this.myAsyncService.httpPOST("http://" + ipaddress + ":8080/alm_zain_ksa_financials/addAssetTracking", assettrackRequest.toString());

                        }
                    }
                } else {
                    //UPDATE THE LICESNE  HERE BUT CHECK THE DETAILS AS WELL  
                    if (!licenseID.equalsIgnoreCase("")) {
                        //check if record number id 0, if its zero check if the asset id is uniques
                        List<tbLicense> licenseList = this.licenseService.findByLicenseId(licenseID);
                        if (licenseList.isEmpty()) {
                            jsonObjectResponse.put("error", "Error occurred, LIcense ID not found: " + licenseID);
                        } else {
                            tbLicense licenseUpdate = licenseList.get(0);
                            licenseUpdate.setNodeName(jsonObject.getString("nodeId"));
                            licenseUpdate.setNodeName(jsonObject.getString("nodeType"));
                            licenseUpdate.setNeSiteName(jsonObject.getString("siteId"));
                            licenseUpdate.setSiteId(jsonObject.getString("siteId"));
                            licenseUpdate.setZone(jsonObject.getString("zone"));
                            licenseUpdate.setLicenseId(jsonObject.getString("licenseId"));
                            licenseUpdate.setLicenseDetail(jsonObject.getString("licenseDetail"));
                            licenseUpdate.setAllocated(jsonObject.getInt("allocated"));
                            licenseUpdate.setUsage(jsonObject.getInt("usage"));
                            licenseUpdate.setUsagePercentage(jsonObject.getDouble("usagePercentage"));
                            licenseUpdate.setConfig(jsonObject.getString("config"));
                            licenseUpdate.setUnit(jsonObject.getString("unit"));
                            String insertDate = jsonObject.getString("insertDate");
                            String lastChangeDate = jsonObject.getString("lastChangeDate");
                            java.util.Date newInsDate = format.parse(insertDate);
                            java.sql.Date newDateInsertDate = new java.sql.Date(newInsDate.getTime());
                            licenseUpdate.setInsertDate(newDateInsertDate);
                            java.util.Date newChDate = format.parse(lastChangeDate);
                            java.sql.Date newChangedDate = new java.sql.Date(newChDate.getTime());
                            licenseUpdate.setLastChangeDate(newChangedDate);
                            Double licenseValue = jsonObject.optDouble("licenseDetailValue", 0.0);
                            licenseUpdate.setLicenseDetailValue(licenseValue);
                            licenseUpdate.setTechnology(jsonObject.getString("technology"));
                            licenseUpdate.setManufacturer(jsonObject.getString("manufacturer"));
                            this.licenseService.save(licenseUpdate);

                            assetTrack.put("serialNumber", jsonObject.getString("licenseId"));
                            assetTrack.put("siteId", jsonObject.getString("siteId"));
                            assetTrack.put("actionType", "EDIT LICENCE ID " + jsonObject.getString("licenseId"));
                            assetTrack.put("username", jsonObject.getString("createdByName"));
                            org.json.JSONArray assettrackRequest = new org.json.JSONArray();
                            assettrackRequest.put(assetTrack);
                            this.myAsyncService.httpPOST("http://" + ipaddress + ":8080/alm_zain_ksa_financials/addAssetTracking", assettrackRequest.toString());

                        }

                    } else {
                        jsonObjectResponse.put("errorcode", "Missing License ID detected");

                    }

                }

            }
            if (jsonObjectResponse.containsKey("error")) {
                jsonObjectResponse.put("responseCode", "1");
                jsonObjectResponse.put("responseMessage", "Error occured, License ID already exist " + licenseID);
            }
            if (jsonObjectResponse.containsKey("errorcode")) {
                jsonObjectResponse.put("responseCode", "1");
                jsonObjectResponse.put("responseMessage", "Error occured, Missing License ID detected");
            } else {
                jsonObjectResponse.put("responseCode", "0");
                jsonObjectResponse.put("responseMessage", "License  successfully created/updated ");
            }

        } catch (ParseException ex) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", ex.getMessage());
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    // NEW ENDPOINT FOR GETTING ALL LICENSES
    @GetMapping("/getLicenses")
    public JSONArray getLicenses(HttpServletResponse httpResponse) {
        JSONArray jsonObjectResponse = new JSONArray();
        try {
            this.LOGGER.info("GET ALL LICENSES");

            List<tbLicense> allLicenses = this.licenseService.findAll();

            allLicenses.stream().map(license -> {
                JSONObject licenseJson = new JSONObject();
                licenseJson.put("recordNo", license.getId());
                //licenseJson.put("nodeId", license.getNodeId());
                licenseJson.put("nodeName", license.getNodeName());
                licenseJson.put("nodeType", license.getNodeType());
                licenseJson.put("neSiteName", license.getNeSiteName());
                licenseJson.put("siteId", license.getSiteId());
                licenseJson.put("zone", license.getZone());
                licenseJson.put("licenseId", license.getLicenseId());
                licenseJson.put("licenseDetail", license.getLicenseDetail());
                licenseJson.put("allocated", license.getAllocated());
                licenseJson.put("usage", license.getUsage());
                licenseJson.put("usagePercentage", license.getUsagePercentage());
                licenseJson.put("config", license.getConfig());
                licenseJson.put("unit", license.getUnit());
                //   licenseJson.put("expiryDate", license.getExpiryDate());
                licenseJson.put("insertDate", license.getInsertDate());
                licenseJson.put("lastChangeDate", license.getLastChangeDate());
                licenseJson.put("licenseDetailValue", license.getLicenseDetailValue());
                licenseJson.put("technology", license.getTechnology());
                licenseJson.put("manufacturer", license.getManufacturer());
                licenseJson.put("isMapped", license.getIsMapped());
                licenseJson.put("createdById", license.getCreatedById());
                licenseJson.put("createdByName", license.getCreatedByName());
                return licenseJson;
            }).forEachOrdered(licenseJson -> {
                jsonObjectResponse.add(licenseJson);
            });
        } catch (Exception ex) {
            this.LOGGER.error("Exception in getLicenses: " + ex.getMessage(), ex);
        }
        return jsonObjectResponse;
    }

    public String getIPAddress() {
        String ipAddress = "";
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();

            ipAddress = inetAddress.getHostAddress();
            System.out.println("ipAddress " + ipAddress);

        } catch (UnknownHostException ex) {
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return ipAddress;
    }

    //NEW END POINT FOR GETTING UNMAPPED ACTIVE ASSETS 
    @GetMapping({"getUnmappedActiveAssets"})
    public JSONArray getUnmappedActiveAssets(HttpServletResponse httpResponse) {
        JSONArray jsonObjectResponse = new JSONArray();
        try {
            this.LOGGER.info("GET ALL UNMAPPED ASSETS ");
            // String status = assetRequest.getAsString("assetId");
            List<tbUnmappedActiveAsset> allReport = this.unmappedService.findAll();
//            if (!status.equalsIgnoreCase("") || !status.isEmpty()) {
//                allReport = this.financialReportService.findByAssetId(status);
//            } else {
//
//            }
            for (int i = 0; i < allReport.size(); i++) {
                JSONObject singleAssetObj = new JSONObject();
                tbUnmappedActiveAsset financeRPT = allReport.get(i);

                singleAssetObj.put("recordNo", financeRPT.getRecordNo());
                singleAssetObj.put("recordDatetime", financeRPT.getRecordDateTime());
                singleAssetObj.put("nodeName", financeRPT.getNodeName());
                singleAssetObj.put("assetName", financeRPT.getAssetName());
                singleAssetObj.put("assetType", financeRPT.getAssetType());
                singleAssetObj.put("nodeType", financeRPT.getNodeType());
                singleAssetObj.put("siteId", financeRPT.getSiteId());
                singleAssetObj.put("manufacturer", financeRPT.getManufacturer());
                singleAssetObj.put("model", financeRPT.getModel());
                singleAssetObj.put("partNumber", financeRPT.getPartNumber());
                singleAssetObj.put("serialNumber", financeRPT.getSerialNumber());
                singleAssetObj.put("description", financeRPT.getDescription());
                singleAssetObj.put("manufacturingDate", financeRPT.getManufacturingDate());
                singleAssetObj.put("installationDate", financeRPT.getInstallationDate());
                singleAssetObj.put("assetUpdateDate", financeRPT.getAssetUpdateDate());
                singleAssetObj.put("warrantly", financeRPT.getWarrantly());

                jsonObjectResponse.add(singleAssetObj);
            }
        } catch (NumberFormatException ex) {
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
            return jsonObjectResponse;
        }
        return jsonObjectResponse;
    }

    //WITH AS AT DATE 
//    @PostMapping(value = "getFAR", produces = "application/json")
//    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
//    public Map<String, Object> getFARKSA(@RequestBody JSONObject assetRequest) {
//        String status = assetRequest.getAsString("assetId");
//        String columnName = assetRequest.containsKey("columnName") ? assetRequest.getAsString("columnName") : "";
//        String searchQuery = assetRequest.containsKey("searchQuery") ? assetRequest.getAsString("searchQuery") : "";
//        String asAtDate = assetRequest.containsKey("asAtDate") ? assetRequest.getAsString("asAtDate") : null;
//
//        Map<String, Object> response = new HashMap<>();
//
//        this.LOGGER.info("GET ALL ASSETS " + assetRequest);
//        this.LOGGER.info("Asset ID  " + status);
//
//        int page = Math.max(assetRequest.containsKey("page") ? assetRequest.getAsNumber("page").intValue() : 1, 1);
//        int size = Math.max(assetRequest.containsKey("size") ? assetRequest.getAsNumber("size").intValue() : 100, 1);
//
//        // Compute the last month of the previous month from asAtDate
//        String lastMonthDate = getLastMonthDate(asAtDate);
//
//        String whereClause = buildWhereClause(status, columnName, searchQuery, lastMonthDate);
//        List<Object> params = new ArrayList<>();
//
//        if (!status.isEmpty()) {
//            params.add(status);
//        }
//        if (!columnName.isEmpty() && !searchQuery.isEmpty() && !columnName.equalsIgnoreCase("recordDatetime")) {
//            params.add("%" + searchQuery + "%");
//        }
//        if (asAtDate != null) {
//            params.add(lastMonthDate);
//
//            String countSql = "SELECT COUNT(*) FROM tb_FarReport f "
//                    + "LEFT JOIN tb_Asset_Depreciation d ON f.assetId = d.assetCode "
//                    + whereClause;
//            int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
//
//            // Compute aggregated values with proper date filtering
//            BigDecimal filteredCost = getAggregateValue("SUM(f.cost)", whereClause, params);
//            BigDecimal filteredNBV = getAggregateValue("SUM(COALESCE(d.assetBookValue, 0))", whereClause, params);
//            BigDecimal filteredDepreciation = getAggregateValue("SUM(COALESCE(d.accumulatedDepreciation, 0))", whereClause, params);
//
//            // Compute total values without filtering
//            BigDecimal totalCost = getAggregateValue("SUM(f.cost)", "", new ArrayList<>());
//            BigDecimal totalNBV = getAggregateValue("SUM(COALESCE(d.assetBookValue, 0))", "", new ArrayList<>());
//            BigDecimal totalDepreciation = getAggregateValue("SUM(COALESCE(d.accumulatedDepreciation, 0))", "", new ArrayList<>());
//
//            String paginationSql = buildPaginationSql(page, size);
//            String sql = "SELECT f.recordNo, f.recordDatetime, f.book, f.assetId, f.quantity, f.description, f.creationDate, "
//                    + "f.serialNumber, f.tagNumber, f.picStatus, f.picDate, f.cipDeliveryDate, f.linkId, f.acceptanceNumber, "
//                    + "f.depreciateFlag, f.cipEu, f.invoiceNumber, f.poNumber, f.poLineNumber, f.uplLine, f.transferToNewFar, "
//                    + "f.assetStatus, f.value, f.partNumber, f.vendorName, f.vendorNumber, f.mergedCode, f.costAccount, "
//                    + "f.accumulatedDepreAccount, f.cipCostAccount, f.expenseCostCenter, f.expenseAccount, f.Life, "
//                    + "f.datePlacedInService, f.cost, f.nbv, f.depreciationAmount, f.ytdDepreciation, f.depreciationReserve, "
//                    + "f.salvageValue, f.category, f.categoryDescription, f.locationSegment1, f.locationSegment2, "
//                    + "f.locationSegment3, f.locationSegment4, f.locations, f.sequenceNumber, f.createdBy, f.createdDate, "
//                    + "f.updatedBy, f.updatedDate, f.monthlyDepreciationAmt, f.depreciationDate, "
//                    + "f.netCost,f.statusFlag,f.changedBy,f.financialApproval,f.changedDate, f.nodeType, f.inventoryStatus, "
//                    + "(SELECT (MAX(d.assetBookValue)) FROM tb_Asset_Depreciation d WHERE d.assetCode = f.assetId AND d.depreciationDate = ? LIMIT 1) AS assetBookValue, "
//                    + "(SELECT (MAX(d.accumulatedDepreciation)) FROM tb_Asset_Depreciation d WHERE d.assetCode = f.assetId AND d.depreciationDate = ? LIMIT 1) AS accumulatedDepreciation "
//                    + "FROM tb_FarReport f "
//                    + whereClause + paginationSql;
//
//            params.add(lastMonthDate);
//            params.add(lastMonthDate);
//
//            this.LOGGER.info("Executing SQL: " + sql);
//
//            List<Map<String, Object>> result = new ArrayList<>();
//            jdbcTemplate.query(sql, rs -> {
//                Map<String, Object> row = new LinkedHashMap<>();
//                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
//                    row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
//                }
//                result.add(row);
//            }, params.toArray());
//
//            response.put("data", result);
//            response.put("totalRecords", totalRecords);
//            response.put("currentPage", page);
//            response.put("pageSize", size);
//            response.put("totalCost", totalCost);
//            response.put("totalNBV", totalNBV);
//            response.put("totalDepreciation", totalDepreciation);
//            response.put("filteredCost", filteredCost);
//            response.put("filteredNBV", filteredNBV);
//            response.put("filteredDepreciation", filteredDepreciation);
//            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
//
//        } else {
//            String countSql = "SELECT COUNT(*) FROM tb_FarReport " + whereClause;
//
//        this.LOGGER.info("TOTAL  SQL: " + countSql);
//        int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
//
//        // Calculate filtered costs and other aggregates
//        BigDecimal filteredCost = getAggregateValue1("SUM(cost)", whereClause, params);
//        BigDecimal filteredNBV = getAggregateValue1("SUM(netCost)", whereClause, params);
//        BigDecimal filteredDepreciation = getAggregateValue1("SUM(accumulatedDepreciationAmt)", whereClause, params);
//
//        // Calculate total costs and other aggregates
//        BigDecimal totalCost = getAggregateValue1("SUM(cost)", "", new ArrayList<>());
//        BigDecimal totalNBV = getAggregateValue1("SUM(netCost)", "", new ArrayList<>());
//        BigDecimal totalDepreciation = getAggregateValue1("SUM(accumulatedDepreciationAmt)", "", new ArrayList<>());
//
//        String paginationSql = buildPaginationSql1(page, size);
//        String sql = "SELECT recordNo, recordDatetime, book, assetId, quantity, description, creationDate, serialNumber, "
//                + "tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, depreciateFlag, "
//                + "cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, assetStatus, value, "
//                + "partNumber, vendorName, vendorNumber, mergedCode, costAccount, accumulatedDepreAccount, "
//                + "cipCostAccount, expenseCostCenter, expenseAccount, Life, datePlacedInService, cost, nbv, "
//                + "depreciationAmount, ytdDepreciation, depreciationReserve, salvageValue, category, categoryDescription, "
//                + "locationSegment1, locationSegment2, locationSegment3, locationSegment4, locations, sequenceNumber, "
//                + "createdBy, createdDate, updatedBy, updatedDate, monthlyDepreciationAmt, accumulatedDepreciationAmt, "
//                + "depreciationDate, netCost FROM tb_FarReport " + whereClause + paginationSql;
//
//        this.LOGGER.info("Executing SQL: " + sql);
//
//        List<Map<String, Object>> result = new ArrayList<>();
//        jdbcTemplate.query(sql, rs -> {
//            Map<String, Object> row = new LinkedHashMap<>();
//            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
//                row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
//            }
//            result.add(row);
//        }, params.toArray());
//
//        response.put("data", result);
//        response.put("totalRecords", totalRecords);
//        response.put("currentPage", page);
//        response.put("pageSize", size);
//        response.put("totalCost", totalCost);
//        response.put("totalNBV", totalNBV);
//        response.put("totalDepreciation", totalDepreciation);
//        response.put("filteredCost", filteredCost);
//        response.put("filteredNBV", filteredNBV);
//        response.put("filteredDepreciation", filteredDepreciation);
//        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
//
//        }
//
//        // Count total records
//        return response;
//    }
//
//    private String buildWhereClause(String status, String columnName, String searchQuery, String asAtDate) {
//        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
//
//        if (!status.isEmpty()) {
//            whereClause.append(" AND f.assetId = ? ");
//        }
//
//        if (!columnName.isEmpty() && !searchQuery.isEmpty() && !columnName.equalsIgnoreCase("recordDatetime")) {
//            whereClause.append(" AND f.").append(columnName.toLowerCase()).append(" LIKE ? ");
//        }
//
//        if (asAtDate != null) {
//            whereClause.append(" AND d.depreciationDate = ? ");
//        }
//
//        return whereClause.toString();
//    }
//
//    private BigDecimal getAggregateValue(String aggregateFunction, String whereClause, List<Object> params) {
//        String sql = "SELECT " + aggregateFunction + " FROM tb_FarReport f "
//                + "LEFT JOIN tb_Asset_Depreciation d ON f.assetId = d.assetCode "
//                + whereClause;
//        return jdbcTemplate.queryForObject(sql, BigDecimal.class, params.toArray());
//    }
//
//    private String buildPaginationSql(int page, int size) {
//        int offset = (page - 1) * size;
//        return " LIMIT " + size + " OFFSET " + offset;
//    }
//
//    private String getLastMonthDate(String asAtDate) {
//        if (asAtDate == null || asAtDate.isEmpty()) {
//            return null;
//        }
//        LocalDate inputDate = LocalDate.parse(asAtDate);
//        YearMonth previousMonth = YearMonth.from(inputDate).minusMonths(1);
//        LocalDate lastDayOfPreviousMonth = previousMonth.atEndOfMonth();
//        return lastDayOfPreviousMonth.toString();
//    }

    @PostMapping(value = "getFAR", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getFARKSA(@RequestBody JSONObject assetRequest) {
        String status = assetRequest.getAsString("assetId");
        String columnName = assetRequest.containsKey("columnName") ? assetRequest.getAsString("columnName") : "";
        String searchQuery = assetRequest.containsKey("searchQuery") ? assetRequest.getAsString("searchQuery") : "";
        String dateFrom = assetRequest.containsKey("dateFrom") ? assetRequest.getAsString("dateFrom") : null;
        String dateTo = assetRequest.containsKey("dateTo") ? assetRequest.getAsString("dateTo") : null;

        this.LOGGER.info("GET ALL ASSETS " + assetRequest);
        this.LOGGER.info("Asset ID  " + status);

        int page = Math.max(assetRequest.containsKey("page") ? assetRequest.getAsNumber("page").intValue() : 1, 1);
        int size = Math.max(assetRequest.containsKey("size") ? assetRequest.getAsNumber("size").intValue() : 500, 1);

        String whereClause = buildWhereClause1(status, columnName, searchQuery, dateFrom, dateTo);
        List<Object> params = new ArrayList<>();

        // Add parameters based on the where clause
        if (!status.equalsIgnoreCase("")) {
            params.add(status);
        }
        if (!columnName.equalsIgnoreCase("") && !searchQuery.equalsIgnoreCase("") && !columnName.equalsIgnoreCase("recordDatetime")) {
            params.add("%" + searchQuery + "%");
        }
        if (dateFrom != null && !dateFrom.isEmpty()) {
            params.add(dateFrom);
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            params.add(dateTo);
        }

        String countSql = "SELECT COUNT(*) FROM tb_FarReport " + whereClause;

        this.LOGGER.info("TOTAL  SQL: " + countSql);
        int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());

        // Calculate filtered costs and other aggregates
        BigDecimal filteredCost = getAggregateValue1("SUM(cost)", whereClause, params);
        BigDecimal filteredNBV = getAggregateValue1("SUM(netCost)", whereClause, params);
        BigDecimal filteredDepreciation = getAggregateValue1("SUM(accumulatedDepreciationAmt)", whereClause, params);

        // Calculate total costs and other aggregates
        BigDecimal totalCost = getAggregateValue1("SUM(cost)", "", new ArrayList<>());
        BigDecimal totalNBV = getAggregateValue1("SUM(netCost)", "", new ArrayList<>());
        BigDecimal totalDepreciation = getAggregateValue1("SUM(accumulatedDepreciationAmt)", "", new ArrayList<>());

        String paginationSql = buildPaginationSql1(page, size);
        String sql = "SELECT recordNo, recordDatetime, book, assetId, quantity, description, creationDate, serialNumber, "
                + "tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, depreciateFlag, "
                + "cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, assetStatus, value, "
                + "partNumber, vendorName, vendorNumber, mergedCode, costAccount, accumulatedDepreAccount, "
                + "cipCostAccount, expenseCostCenter, expenseAccount, Life, datePlacedInService, cost, nbv, "
                + "depreciationAmount, ytdDepreciation, depreciationReserve, salvageValue, category, categoryDescription, "
                + "locationSegment1, locationSegment2, locationSegment3, locationSegment4, locations, sequenceNumber, "
                + "createdBy, createdDate, updatedBy, updatedDate, monthlyDepreciationAmt, accumulatedDepreciationAmt, "
                + "depreciationDate, netCost FROM tb_FarReport " + whereClause + paginationSql;

        this.LOGGER.info("Executing SQL: " + sql);

        List<Map<String, Object>> result = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
            }
            result.add(row);
        }, params.toArray());

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalCost", totalCost);
        response.put("totalNBV", totalNBV);
        response.put("totalDepreciation", totalDepreciation);
        response.put("filteredCost", filteredCost);
        response.put("filteredNBV", filteredNBV);
        response.put("filteredDepreciation", filteredDepreciation);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;
    }

    private String buildWhereClause1(String status, String columnName, String searchQuery, String dateFrom, String dateTo) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");

        if (!status.equalsIgnoreCase("")) {
            whereClause.append(" AND assetId = ? ");
        }

        if (!columnName.equalsIgnoreCase("") && !searchQuery.equalsIgnoreCase("") && !columnName.equalsIgnoreCase("recordDatetime")) {
            whereClause.append(" AND ").append(columnName.toLowerCase()).append(" LIKE ? ");
        }

      
        return whereClause.toString();
    }

    private BigDecimal getAggregateValue1(String aggregateFunction, String whereClause, List<Object> params) {
        String sql = "SELECT " + aggregateFunction + " FROM tb_FarReport " + whereClause;
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, params.toArray());
    }

    private String buildPaginationSql1(int page, int size) {
        int offset = (page - 1) * size;
        return " LIMIT " + size + " OFFSET " + offset;
    }

    //UPLOAD NEW FAR  REPORT PER KSA FORMART
    @RequestMapping({"uploadFAR"})
    public JSONObject uploadFAR(@RequestBody String req, HttpServletResponse httpResponse) throws ParseException {
        JSONObject jsonObjectResponse = new JSONObject();
        long recordNo = 0;
        String assetID = "";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        LocalDateTime now = LocalDateTime.now();
        java.util.Date parsedDate = format.parse(now.toString());
        java.sql.Date newDate = new java.sql.Date(parsedDate.getTime());
        try {

            org.json.JSONArray jsonArray = new org.json.JSONArray(req);
            for (int i = 0; i < jsonArray.length(); i++) {
                org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
                this.LOGGER.info("RECEIVED NEW KSA FAR REQUEST" + req);
                recordNo = Integer.parseInt(jsonObject.getString("recordNo"));
                assetID = jsonObject.getString("assetId");
                if (recordNo == 0) {
                    if (!assetID.equalsIgnoreCase("")) {
                        //check if record number id 0, if its zero check if the asset id is uniques
                        List<tb_FarReport> financeList = this.farReportService.findByAssetId(assetID);
                        if (!financeList.isEmpty()) {
                            jsonObjectResponse.put("error", "Error occured, AssetCode already exist " + assetID);
                        } else if (jsonObject.getString("datePlacedInService").isEmpty()) {
                            jsonObjectResponse.put("error", "Error occured, Date of service cannot be empty " + assetID);
                        } else if (jsonObject.getDouble("cost") < 1) {
                            jsonObjectResponse.put("error", "Error occured, Input a valid initial cost for the asset id " + assetID);
                        } else {
                            tb_FarReport newFinance = new tb_FarReport();
                            newFinance.setRecordDatetime(newDate);
                            newFinance.setBook(jsonObject.getString("book"));
                            newFinance.setAssetId(jsonObject.getString("assetId"));
                            newFinance.setQuantity(jsonObject.getInt("quantity"));
                            newFinance.setDescription(jsonObject.getString("description"));
                            String creationDate = jsonObject.getString("creationDate");
                            newFinance.setCreationDate(format.parse(creationDate));
                            newFinance.setSerialNumber(jsonObject.getString("serialNumber"));
                            newFinance.setTagNumber(jsonObject.getString("tagNumber"));
                            newFinance.setPicStatus(jsonObject.getString("picStatus"));
                            String picDate = jsonObject.getString("picDate");
                            if (!picDate.equalsIgnoreCase("")) {
                                newFinance.setPicDate(format.parse(picDate));
                            }
                            String cipDeliveryDate = jsonObject.getString("cipDeliveryDate");
                            if (!cipDeliveryDate.equalsIgnoreCase("")) {
                                newFinance.setCipDeliveryDate(format.parse(cipDeliveryDate));
                            }
                            newFinance.setLinkId(jsonObject.getString("linkId"));
                            newFinance.setAcceptanceNumber(jsonObject.getString("acceptanceNumber"));
                            newFinance.setDepreciateFlag(jsonObject.getString("depreciateFlag"));
                            newFinance.setCipEu(jsonObject.getString("cipEu"));
                            newFinance.setInvoiceNumber(jsonObject.getString("invoiceNumber"));
                            newFinance.setPoNumber(jsonObject.getString("poNumber"));
                            newFinance.setPoLineNumber(jsonObject.getString("poLineNumber"));
                            newFinance.setUplLine(jsonObject.getString("uplLine"));
                            newFinance.setTransferToNewFar(jsonObject.getString("transferToNewFar"));
                            newFinance.setAssetStatus(jsonObject.getString("assetStatus"));
                            String value = jsonObject.getString("value");
                            if (!value.equalsIgnoreCase("")) {
                                newFinance.setValue(Double.parseDouble(value));
                            }
                            newFinance.setPartNumber(jsonObject.getString("partNumber"));
                            newFinance.setVendorName(jsonObject.getString("vendorName"));
                            newFinance.setVendorNumber(jsonObject.getString("vendorNumber"));
                            newFinance.setMergedCode(jsonObject.getString("mergedCode"));
                            newFinance.setCostAccount(jsonObject.getString("costAccount"));
                            newFinance.setAccumulatedDepreAccount(jsonObject.getString("accumulatedDepreAccount"));
                            newFinance.setCipCostAccount(jsonObject.getString("cipCostAccount"));
                            newFinance.setExpenseCostCenter(jsonObject.getString("expenseCostCenter"));
                            newFinance.setExpenseAccount(jsonObject.getString("expenseAccount"));

                            newFinance.setLife(jsonObject.getInt("life"));
                            String datePlacedInService = jsonObject.getString("datePlacedInService");
                            if (!datePlacedInService.equalsIgnoreCase("")) {
                                newFinance.setDatePlacedInService(format.parse(datePlacedInService));
                            }
                            newFinance.setCost(jsonObject.getDouble("cost"));
                            newFinance.setNbv(jsonObject.getDouble("nbv"));
                            newFinance.setDepreciationAmount(jsonObject.getDouble("depreciationAmount"));
                            newFinance.setYtdDepreciation(jsonObject.getDouble("ytdDepreciation"));
                            newFinance.setDepreciationReserve(jsonObject.getDouble("depreciationReserve"));
                            newFinance.setSalvageValue(jsonObject.getDouble("salvageValue"));

                            newFinance.setCategory(jsonObject.getString("category"));
                            newFinance.setCategoryDescription(jsonObject.getString("categoryDescription"));

                            newFinance.setLocationSegment1(jsonObject.getString("locationSegment1"));
                            newFinance.setLocationSegment2(jsonObject.getString("locationSegment2"));

                            newFinance.setLocationSegment3(jsonObject.getString("locationSegment3"));
                            newFinance.setLocationSegment4(jsonObject.getString("locationSegment4"));
                            newFinance.setLocations(jsonObject.getString("locations"));
                            newFinance.setSequenceNumber(jsonObject.getInt("sequenceNumber"));
                            newFinance.setStatusFlag("New");
                            this.farReportService.save(newFinance);
                        }
                    } else {
                        jsonObjectResponse.put("errorcode", "Missing Asset Code detected");

                    }
                } else {
                    //UPDATE THE FR HERE BUT CHECK THE DETAILS AS WELL  
                    if (!assetID.equalsIgnoreCase("")) {
                        //check if record number id 0, if its zero check if the asset id is uniques
                        List<tb_FarReport> financeList = this.farReportService.findByAssetId(assetID);
                        if (financeList.isEmpty()) {
                            jsonObjectResponse.put("error", "Error occurred, Asset ID not found: " + assetID);
                        } else if (jsonObject.getString("datePlacedInService").isEmpty()) {
                            jsonObjectResponse.put("error", "Error occured, Date of service cannot be empty " + assetID);
                        } else if (jsonObject.getDouble("cost") < 1) {
                            jsonObjectResponse.put("error", "Error occured, Input a valid initial cost for the asset id " + assetID);
                        } else {
                            tb_FarReport financeUpdate = financeList.get(0);
                            financeUpdate.setBook(jsonObject.getString("book"));
                            financeUpdate.setAssetId(jsonObject.getString("assetId"));
                            financeUpdate.setQuantity(jsonObject.getInt("quantity"));
                            financeUpdate.setDescription(jsonObject.getString("description"));
                            String creationDate = jsonObject.getString("creationDate");
                            financeUpdate.setCreationDate(format.parse(creationDate));
                            financeUpdate.setSerialNumber(jsonObject.getString("serialNumber"));
                            financeUpdate.setTagNumber(jsonObject.getString("tagNumber"));
                            financeUpdate.setPicStatus(jsonObject.getString("picStatus"));
                            String picDate = jsonObject.getString("picDate");
                            if (!picDate.equalsIgnoreCase("")) {
                                financeUpdate.setPicDate(format.parse(picDate));
                            }
                            String cipDeliveryDate = jsonObject.getString("cipDeliveryDate");
                            if (!cipDeliveryDate.equalsIgnoreCase("")) {
                                financeUpdate.setCipDeliveryDate(format.parse(cipDeliveryDate));
                            }
                            financeUpdate.setLinkId(jsonObject.getString("linkId"));
                            financeUpdate.setAcceptanceNumber(jsonObject.getString("acceptanceNumber"));
                            financeUpdate.setDepreciateFlag(jsonObject.getString("depreciateFlag"));
                            financeUpdate.setCipEu(jsonObject.getString("cipEu"));
                            financeUpdate.setInvoiceNumber(jsonObject.getString("invoiceNumber"));
                            financeUpdate.setPoNumber(jsonObject.getString("poNumber"));
                            financeUpdate.setPoLineNumber(jsonObject.getString("poLineNumber"));
                            financeUpdate.setUplLine(jsonObject.getString("uplLine"));
                            financeUpdate.setTransferToNewFar(jsonObject.getString("transferToNewFar"));
                            financeUpdate.setAssetStatus(jsonObject.getString("assetStatus"));
                            String value = jsonObject.getString("value");
                            if (!value.equalsIgnoreCase("")) {
                                financeUpdate.setValue(Double.parseDouble(value));
                            }
                            financeUpdate.setPartNumber(jsonObject.getString("partNumber"));
                            financeUpdate.setVendorName(jsonObject.getString("vendorName"));
                            financeUpdate.setVendorNumber(jsonObject.getString("vendorNumber"));
                            financeUpdate.setMergedCode(jsonObject.getString("mergedCode"));
                            financeUpdate.setCostAccount(jsonObject.getString("costAccount"));
                            financeUpdate.setAccumulatedDepreAccount(jsonObject.getString("accumulatedDepreAccount"));
                            financeUpdate.setCipCostAccount(jsonObject.getString("cipCostAccount"));
                            financeUpdate.setExpenseCostCenter(jsonObject.getString("expenseCostCenter"));
                            financeUpdate.setExpenseAccount(jsonObject.getString("expenseAccount"));

                            financeUpdate.setLife(jsonObject.getInt("life"));
                            String datePlacedInService = jsonObject.getString("datePlacedInService");
                            if (!datePlacedInService.equalsIgnoreCase("")) {
                                financeUpdate.setDatePlacedInService(format.parse(datePlacedInService));
                            }

                            financeUpdate.setCost(jsonObject.getDouble("cost"));
                            financeUpdate.setNbv(jsonObject.getDouble("nbv"));
                            financeUpdate.setDepreciationAmount(jsonObject.getDouble("depreciationAmount"));
                            financeUpdate.setYtdDepreciation(jsonObject.getDouble("ytdDepreciation"));
                            financeUpdate.setDepreciationReserve(jsonObject.getDouble("depreciationReserve"));
                            financeUpdate.setSalvageValue(jsonObject.getDouble("salvageValue"));

                            financeUpdate.setCategory(jsonObject.getString("category"));
                            financeUpdate.setCategoryDescription(jsonObject.getString("categoryDescription"));

                            financeUpdate.setLocationSegment1(jsonObject.getString("locationSegment1"));
                            financeUpdate.setLocationSegment2(jsonObject.getString("locationSegment2"));

                            financeUpdate.setLocationSegment3(jsonObject.getString("locationSegment3"));
                            financeUpdate.setLocationSegment4(jsonObject.getString("locationSegment4"));
                            financeUpdate.setLocations(jsonObject.getString("locations"));
                            financeUpdate.setSequenceNumber(jsonObject.getInt("sequenceNumber"));
                            financeUpdate.setStatusFlag("Existing");
                            this.farReportService.save(financeUpdate);
                        }

                    } else {
                        jsonObjectResponse.put("errorcode", "Missing Asset Code detected");

                    }

                }

            }
            if (jsonObjectResponse.containsKey("error")) {
                jsonObjectResponse.put("responseCode", "1");
                jsonObjectResponse.put("responseMessage", "Error occured, AssetCode already exist");
            } else if (jsonObjectResponse.containsKey("errorcode")) {
                jsonObjectResponse.put("responseCode", "1");
                jsonObjectResponse.put("responseMessage", "Missing Asset Code detected");
            } else {
                jsonObjectResponse.put("responseCode", "0");
                jsonObjectResponse.put("responseMessage", "Financial Report successfully created/updated ");
            }

        } catch (ParseException ex) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", ex.getMessage());
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    //UPLOAD FINANCIAL REPORT HERE 
    @RequestMapping({"uploadFR"})
    public JSONObject uploadFR(@RequestBody String req, HttpServletResponse httpResponse
    ) {
        JSONObject jsonObjectResponse = new JSONObject();
        long recordNo = 0;
        String assetID = "";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        try {

            org.json.JSONArray jsonArray = new org.json.JSONArray(req);
            for (int i = 0; i < jsonArray.length(); i++) {
                org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
                this.LOGGER.info("RECEIVED FINANCIAL REPORT REQUEST" + req);
                recordNo = Integer.parseInt(jsonObject.getString("recordNo"));
                assetID = jsonObject.getString("assetId");
                if (recordNo == 0) {
                    if (!assetID.equalsIgnoreCase("")) {
                        //check if record number id 0, if its zero check if the asset id is uniques
                        List<tb_FinancialReport> financeList = this.financialReportService.findByAssetId(assetID);
                        if (!financeList.isEmpty()) {
                            jsonObjectResponse.put("error", "Error occured, AssetCode already exist" + assetID);
                        } else if (jsonObject.getString("dateOfService").isEmpty()) {
                            jsonObjectResponse.put("error", "Error occured, Date of service cannot be empty " + assetID);
                        } else if (jsonObject.getDouble("initialCost") < 1) {
                            jsonObjectResponse.put("error", "Error occured, Input a valid initial cost for the asset id " + assetID);
                        } else {
                            tb_FinancialReport newFinance = new tb_FinancialReport();
                            newFinance.setSerialNumber(jsonObject.getString("serialNumber"));
                            newFinance.setRfid(jsonObject.getString("rfid"));
                            newFinance.setTag(jsonObject.getString("tag"));
                            newFinance.setAssetId(jsonObject.getString("assetId"));
                            newFinance.setAssetType(jsonObject.getString("assetType"));
                            newFinance.setNodeType(jsonObject.getString("nodeType"));
                            String installationDate = jsonObject.getString("installationDate");
                            newFinance.setInstallationDate(format.parse(installationDate));
                            newFinance.setInitialCost(jsonObject.getDouble("initialCost"));
                            newFinance.setSalvageValue(jsonObject.getDouble("salvageValue"));
                            newFinance.setPoNumber(jsonObject.getString("poNumber"));
                            String poDate = jsonObject.getString("poDate");
                            newFinance.setPoDate(format.parse(poDate));
                            newFinance.setNewFACategory(jsonObject.getString("newFACategory"));
                            newFinance.setL1(jsonObject.getString("L1"));
                            newFinance.setL2(jsonObject.getString("L2"));
                            newFinance.setL3(jsonObject.getString("L3"));
                            newFinance.setL4(jsonObject.getString("L4"));
                            newFinance.setAccDepreciationCode(jsonObject.getString("accDepreciationCode"));
                            newFinance.setDepreciationCode(jsonObject.getString("depreciationCode"));
                            newFinance.setUserfulLife(jsonObject.getInt("userfulLife"));
                            newFinance.setVendorName(jsonObject.getString("vendorName"));
                            newFinance.setVendorNumber(jsonObject.getString("vendorNumber"));
                            newFinance.setProjectNumber(jsonObject.getString("projectNumber"));
                            String dateOfService = jsonObject.getString("dateOfService");
                            newFinance.setDateOfService(format.parse(dateOfService));
                            newFinance.setOldFACategory(jsonObject.getString("oldFACategory"));
                            newFinance.setCostCenter(jsonObject.getString("costCenter"));
                            newFinance.setAdjustment(jsonObject.getDouble("adjustment"));
                            newFinance.setInvoiceNumber(jsonObject.getString("invoiceNumber"));
                            newFinance.setTaskId(jsonObject.getString("taskId"));
                            newFinance.setPoLineNumber(jsonObject.getString("poLineNumber"));
                            newFinance.setApprovalStatus("created");
                            this.financialReportService.save(newFinance);
                            //   jsonObjectResponse.put("responseCode", "00");
                            // jsonObjectResponse.put("responseMessage", "Asset transfered Successfully");

                        }

                    } else {
                        jsonObjectResponse.put("errorcode", "Missing Asset Code detected");

                    }
                } else {
                    //UPDATE THE FR HERE BUT CHECK THE DETAILS AS WELL  
                    if (!assetID.equalsIgnoreCase("")) {
                        //check if record number id 0, if its zero check if the asset id is uniques
                        List<tb_FinancialReport> financeList = this.financialReportService.findByAssetId(assetID);
                        if (financeList.isEmpty()) {
                            jsonObjectResponse.put("error", "Error occurred, Asset ID not found: " + assetID);
                        } else if (jsonObject.getString("dateOfService").isEmpty()) {
                            jsonObjectResponse.put("error", "Error occured, Date of service cannot be empty " + assetID);
                        } else if (jsonObject.getDouble("initialCost") < 1) {
                            jsonObjectResponse.put("error", "Error occured, Input a valid initial cost for the asset id " + assetID);
                        } else {
                            tb_FinancialReport finance = financeList.get(0);
                            finance.setSerialNumber(jsonObject.getString("serialNumber"));
                            finance.setRfid(jsonObject.getString("rfid"));
                            finance.setTag(jsonObject.getString("tag"));
                            finance.setAssetId(jsonObject.getString("assetId"));
                            finance.setAssetType(jsonObject.getString("assetType"));
                            finance.setNodeType(jsonObject.getString("nodeType"));
                            String installationDate = jsonObject.getString("installationDate");
                            finance.setInstallationDate(format.parse(installationDate));
                            finance.setInitialCost(jsonObject.getDouble("initialCost"));
                            finance.setSalvageValue(jsonObject.getDouble("salvageValue"));
                            finance.setPoNumber(jsonObject.getString("poNumber"));
                            String poDate = jsonObject.getString("poDate");
                            finance.setPoDate(format.parse(poDate));
                            finance.setNewFACategory(jsonObject.getString("newFACategory"));
                            finance.setL1(jsonObject.getString("L1"));
                            finance.setL2(jsonObject.getString("L2"));
                            finance.setL3(jsonObject.getString("L3"));
                            finance.setL4(jsonObject.getString("L4"));
                            finance.setAccDepreciationCode(jsonObject.getString("accDepreciationCode"));
                            finance.setDepreciationCode(jsonObject.getString("depreciationCode"));
                            finance.setUserfulLife(jsonObject.getInt("userfulLife"));
                            finance.setVendorName(jsonObject.getString("vendorName"));
                            finance.setVendorNumber(jsonObject.getString("vendorNumber"));
                            finance.setProjectNumber(jsonObject.getString("projectNumber"));
                            String dateOfService = jsonObject.getString("dateOfService");
                            finance.setDateOfService(format.parse(dateOfService));
                            finance.setOldFACategory(jsonObject.getString("oldFACategory"));
                            finance.setCostCenter(jsonObject.getString("costCenter"));
                            finance.setAdjustment(jsonObject.getDouble("adjustment"));
                            finance.setInvoiceNumber(jsonObject.getString("invoiceNumber"));
                            finance.setTaskId(jsonObject.getString("taskId"));
                            finance.setPoLineNumber(jsonObject.getString("poLineNumber"));
                            finance.setApprovalStatus("updated");
                            this.financialReportService.save(finance);
                            //   jsonObjectResponse.put("responseCode", "00");
                            // jsonObjectResponse.put("responseMessage", "Asset transfered Successfully");

                        }

                    } else {
                        jsonObjectResponse.put("errorcode", "Missing Asset Code detected");

                    }

                }

            }
            if (jsonObjectResponse.containsKey("error")) {
                jsonObjectResponse.put("responseCode", "1");
                jsonObjectResponse.put("responseMessage", "Error occured, AssetCode already exist");
            } else if (jsonObjectResponse.containsKey("errorcode")) {
                jsonObjectResponse.put("responseCode", "1");
                jsonObjectResponse.put("responseMessage", "Missing Asset Code detected");
            } else {
                jsonObjectResponse.put("responseCode", "0");
                jsonObjectResponse.put("responseMessage", "Financial Report successfully created/updated ");
            }

        } catch (ParseException ex) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", ex.getMessage());
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"getFinancialReport"})
    public JSONArray getFinancialReport(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONArray jsonObjectResponse = new JSONArray();
        try {
            this.LOGGER.info("GET ALL ASSETS " + assetRequest);
            String status = assetRequest.getAsString("assetId");
            List<tb_FinancialReport> allReport = this.financialReportService.findAll();
            if (!status.equalsIgnoreCase("") || !status.isEmpty()) {
                allReport = this.financialReportService.findByAssetId(status);
            } else {

            }
            for (int i = 0; i < allReport.size(); i++) {
                JSONObject singleAssetObj = new JSONObject();
                tb_FinancialReport financeRPT = allReport.get(i);

                singleAssetObj.put("recordNo", financeRPT.getRecordNo());
                singleAssetObj.put("recordDatetime", financeRPT.getRecordDatetime());
                singleAssetObj.put("serialNumber", financeRPT.getSerialNumber());
                singleAssetObj.put("rfid", financeRPT.getRfid());
                singleAssetObj.put("tag", financeRPT.getTag());
                singleAssetObj.put("assetId", financeRPT.getAssetId());
                singleAssetObj.put("assetType", financeRPT.getAssetType());
                singleAssetObj.put("nodeType", financeRPT.getNodeType());
                singleAssetObj.put("installationDate", financeRPT.getInstallationDate());
                singleAssetObj.put("initialCost", financeRPT.getInitialCost());
                singleAssetObj.put("salvageValue", financeRPT.getSalvageValue());
                singleAssetObj.put("poNumber", financeRPT.getPoNumber());
                singleAssetObj.put("poDate", financeRPT.getPoDate());
                singleAssetObj.put("newFACategory", financeRPT.getNewFACategory());
                singleAssetObj.put("L1", financeRPT.getL1());
                singleAssetObj.put("L2", financeRPT.getL2());
                singleAssetObj.put("L3", financeRPT.getL3());
                singleAssetObj.put("L4", financeRPT.getL4());
                singleAssetObj.put("accDepreciationCode", financeRPT.getAccDepreciationCode());
                singleAssetObj.put("depreciationCode", financeRPT.getDepreciationCode());
                singleAssetObj.put("userfulLife", financeRPT.getUserfulLife());
                singleAssetObj.put("vendorName", financeRPT.getVendorName());
                singleAssetObj.put("vendorNumber", financeRPT.getVendorNumber());
                singleAssetObj.put("projectNumber", financeRPT.getProjectNumber());
                singleAssetObj.put("dateOfService", financeRPT.getDateOfService());
                singleAssetObj.put("oldFACategory", financeRPT.getOldFACategory());
                singleAssetObj.put("costCenter", financeRPT.getCostCenter());
                singleAssetObj.put("adjustment", financeRPT.getAdjustment());
                singleAssetObj.put("invoiceNumber", financeRPT.getInvoiceNumber());
                singleAssetObj.put("taskId", financeRPT.getTaskId());
                singleAssetObj.put("poLineNumber", financeRPT.getPoLineNumber());
                // Add null check for monthlyDepreciationAmt
                singleAssetObj.put("monthlyDepreciationAmt", financeRPT.getMonthlyDepreciationAmt() != null ? financeRPT.getMonthlyDepreciationAmt() : 0.0);
                singleAssetObj.put("accumulatedDepreciationAmt", financeRPT.getAccumulatedDepreciationAmt() != null ? financeRPT.getAccumulatedDepreciationAmt() : 0.0);
                singleAssetObj.put("netCost", financeRPT.getNetCost() != null ? financeRPT.getNetCost() : 0.0);

                //   //singleAssetObj.put("monthlyDepreciationAmt", financeRPT.getMonthlyDepreciationAmt());
                //singleAssetObj.put("accumulatedDepreciationAmt", financeRPT.getAccumulatedDepreciationAmt());
                singleAssetObj.put("approvalStatus", financeRPT.getApprovalStatus());
                singleAssetObj.put("depreciationDate", financeRPT.getDepreciationDate());
                //singleAssetObj.put("netCost", financeRPT.getNetCost());
                jsonObjectResponse.add(singleAssetObj);
            }
        } catch (NumberFormatException ex) {
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
            return jsonObjectResponse;
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"assetTransfer"})
    public JSONObject assetTransfer(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONObject jsonObjectResponse = new JSONObject();
        try {
            tb_Asset tbasset = new tb_Asset();
            this.LOGGER.info("RECEIVED ASSET TRANSFER REQUEST" + assetRequest);
            if (!assetRequest.isEmpty()) {
                String assetCode = assetRequest.getAsString("assetCode");
                String inventoryId = assetRequest.getAsString("inventoryId");
                String serialNumber = assetRequest.getAsString("serialNumber");
                float purchasePrice = assetRequest.getAsNumber("purchasePrice").floatValue();
                String poId = assetRequest.getAsString("poId");
                String createdBy = assetRequest.getAsString("createdBy");
                String supplierId = assetRequest.getAsString("supplierId");
                String purchaseDate = assetRequest.getAsString("purchaseDate");
                String warrantyDetails = assetRequest.getAsString("warrantyDetails");
                String warrantExpiryDate = assetRequest.getAsString("warrantExpiryDate");
                String details = assetRequest.getAsString("details");
                String status = assetRequest.getAsString("status");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                tbasset.setAssetCode(assetCode);
                tbasset.setCreatedBy(createdBy);
                tbasset.setInventoryId(inventoryId);
                tbasset.setPoId(poId);
                tbasset.setSalvageValue("0");
                tbasset.setPurchaseDate(format.parse(purchaseDate));
                tbasset.setPurchasePrice(purchasePrice);
                tbasset.setRecordDatetime(format.parse(purchaseDate));
                tbasset.setWarrantExpiryDate(warrantExpiryDate);
                tbasset.setSerialNumber(serialNumber);
                tbasset.setApproved(false);
                tbasset.setUsefulLife("0");
                tbasset.setStatus(status);
                tbasset.setSupplierId(supplierId);
                tbasset.setWarrantyDetails(warrantyDetails);
                this.assetService.save(tbasset);
                jsonObjectResponse.put("responseCode", "00");
                jsonObjectResponse.put("responseMessage", "Asset transfered Successfully");
            } else {
                jsonObjectResponse.put("responseCode", "01");
                jsonObjectResponse.put("responseMessage", "Error occured, AssetCode already exist");
            }
        } catch (Exception ex) {
            jsonObjectResponse.put("responseCode", "01");
            jsonObjectResponse.put("responseMessage", "Error occured, AssetCode already exist");
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"getApprovedAssets"})
    public JSONArray getApprovedAssets(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONArray jsonObjectResponse = new JSONArray();
        try {
            this.LOGGER.info("GET ASSETS FOR APPROVAL" + assetRequest);
            String status = assetRequest.getAsString("status");
            List<tb_Asset> assetsToApprove = this.assetService.findByStatus(status);
            for (int i = 0; i < assetsToApprove.size(); i++) {
                JSONObject singleAssetObj = new JSONObject();
                tb_Asset tbasset = assetsToApprove.get(i);
                tb_Item item = this.itemservice.findByItemCode(tbasset.getAssetCode());
                singleAssetObj.put("assetCode", tbasset.getAssetCode());
                singleAssetObj.put("inventoryId", tbasset.getInventoryId());
                singleAssetObj.put("serialNumber", tbasset.getSerialNumber());
                singleAssetObj.put("purchasePrice", Float.valueOf(tbasset.getPurchasePrice()));
                singleAssetObj.put("poId", tbasset.getPoId());
                singleAssetObj.put("createdBy", tbasset.getCreatedBy());
                singleAssetObj.put("supplierId", tbasset.getSupplierId());
                singleAssetObj.put("purchaseDate", tbasset.getPurchaseDate());
                singleAssetObj.put("warrantyDetails", tbasset.getWarrantyDetails());
                singleAssetObj.put("warrantExpiryDate", tbasset.getWarrantExpiryDate());
                if (item != null) {
                    singleAssetObj.put("depreciationMethod", item.getDepreciationMethod());
                } else {
                    singleAssetObj.put("depreciationMethod", "Not Set");
                }
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                LocalDate localDate1 = tbasset.getRecordDatetime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate localDate2 = LocalDate.now();
                Period period = Period.between(localDate1, localDate2);
                int yearDiff = period.getYears();
                double depreciationRate = 0.25D;
                double bookValue = 0.0D;
                double accumulatedDepreciation = 0.0D;
                if (item != null) {
                    if (item.getDepreciationMethod().startsWith("R") || item.getDepreciationMethod().startsWith("r")) {
                        for (int year = 1; year <= yearDiff; year++) {
                            double depreciation = (tbasset.getPurchasePrice() - accumulatedDepreciation) * depreciationRate;
                            accumulatedDepreciation += depreciation;
                            bookValue = tbasset.getPurchasePrice() - accumulatedDepreciation;
                            System.out.println("Year " + year + " - Depreciation: " + depreciation + ", Book Value: " + bookValue);
                        }
                    } else {
                        bookValue = tbasset.getPurchasePrice() - calculateTotalDepreciation(tbasset.getPurchasePrice(), Double.parseDouble(tbasset.getSalvageValue()), Integer.valueOf(tbasset.getUsefulLife()).intValue(), yearDiff);
                    }
                } else {
                    bookValue = tbasset.getPurchasePrice() - calculateTotalDepreciation(tbasset.getPurchasePrice(), Double.parseDouble(tbasset.getSalvageValue()), Integer.valueOf(tbasset.getUsefulLife()).intValue(), yearDiff);
                }
                if (bookValue < Double.parseDouble(tbasset.getSalvageValue())) {
                    bookValue = Double.parseDouble(tbasset.getSalvageValue());
                }
                singleAssetObj.put("bookValue", Integer.valueOf((int) Math.round(bookValue)));
                singleAssetObj.put("details", tbasset.getDetails());
                jsonObjectResponse.add(singleAssetObj);
            }
        } catch (Exception ex) {
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
            return jsonObjectResponse;
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"getAssetsForApproval"})
    public JSONArray getAssetsForApproval(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONArray jsonObjectResponse = new JSONArray();
        try {
            this.LOGGER.info("GET ASSETS FOR APPROVAL" + assetRequest);
            String status = assetRequest.getAsString("status");
            List<tb_Asset> assetsToApprove = this.assetService.findByStatus(status);
            for (int i = 0; i < assetsToApprove.size(); i++) {
                JSONObject singleAssetObj = new JSONObject();
                tb_Asset tbasset = assetsToApprove.get(i);
                tb_Item item = this.itemservice.findByItemCode(tbasset.getAssetCode());
                singleAssetObj.put("assetCode", tbasset.getAssetCode());
                singleAssetObj.put("inventoryId", tbasset.getInventoryId());
                singleAssetObj.put("serialNumber", tbasset.getSerialNumber());
                singleAssetObj.put("purchasePrice", Float.valueOf(tbasset.getPurchasePrice()));
                singleAssetObj.put("poId", tbasset.getPoId());
                singleAssetObj.put("createdBy", tbasset.getCreatedBy());
                singleAssetObj.put("supplierId", tbasset.getSupplierId());
                singleAssetObj.put("purchaseDate", tbasset.getPurchaseDate());
                singleAssetObj.put("warrantyDetails", tbasset.getWarrantyDetails());
                singleAssetObj.put("warrantExpiryDate", tbasset.getWarrantExpiryDate());
                if (item != null) {
                    singleAssetObj.put("depreciationMethod", item.getDepreciationMethod());
                } else {
                    singleAssetObj.put("depreciationMethod", "Not Set");
                }
                singleAssetObj.put("details", tbasset.getDetails());
                jsonObjectResponse.add(singleAssetObj);
            }
        } catch (Exception ex) {
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
            return jsonObjectResponse;
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"approveAssetTransfer"})
    public JSONObject approveAssetTransfer(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONObject jsonObjectResponse = new JSONObject();
        try {
            this.LOGGER.info("GET ASSET TRANSFER APPROVAL REQUESTL" + assetRequest);
            String serialNumber = assetRequest.getAsString("serialNumber");
            String status = assetRequest.getAsString("status");
            tb_Asset tbasset = this.assetService.findBySerialNumber(serialNumber);
            if (!assetRequest.isEmpty()) {
                String approvedBy = assetRequest.getAsString("approvedBy");
                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                Date date = new Date();
                tbasset.setApprovalDate(date);
                tbasset.setStatus(status);
                tbasset.setApprovedBy(approvedBy);
                tbasset.setApproved(true);
                this.assetService.save(tbasset);
                jsonObjectResponse.put("responseCode", "00");
                jsonObjectResponse.put("responseMessage", "Asset Transfer Approved Successfully");
            } else {
                jsonObjectResponse.put("responseCode", "01");
                jsonObjectResponse.put("responseMessage", "Status cannot be null.");
            }
        } catch (Exception ex) {
            jsonObjectResponse.put("responseCode", "01");
            jsonObjectResponse.put("responseMessage", "Error Occurred");
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"allocateAsset"})
    public JSONObject allocateAsset(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONObject jsonObjectResponse = new JSONObject();
        tb_Asset_Allocation tb_Asset_Allocation = new tb_Asset_Allocation();
        try {
            this.LOGGER.info("RECIVE ALLOCATE ASSETS REQUEST" + assetRequest);
            String assetCode = assetRequest.getAsString("assetCode");
            String locationId = assetRequest.getAsString("locationId");
            String personId = assetRequest.getAsString("personId");
            String status = assetRequest.getAsString("status");
            String details = assetRequest.getAsString("details");
            tb_Asset_Allocation.setAllocationDate(new Date());
            tb_Asset_Allocation.setAssetCode(assetCode);
            tb_Asset_Allocation.setDetails(details);
            tb_Asset_Allocation.setLocationId(locationId);
            tb_Asset_Allocation.setPersonId(personId);
            tb_Asset_Allocation.setStatus(status);
            tb_Asset_Allocation.setRecordDatetime(new Date());
            this.assetAllocationService.save(tb_Asset_Allocation);
            jsonObjectResponse.put("responseCode", "00");
            jsonObjectResponse.put("responseMessage", "Asset Allocated Succesfully");
        } catch (Exception ex) {
            jsonObjectResponse.put("responseCode", "01");
            jsonObjectResponse.put("responseMessage", "Asset Allocation Failed");
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"getUnapprovedAllocatedAsset"})
    public JSONArray getUnapprovedAllocatedAsset(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONArray jsonObjectResponse = new JSONArray();
        tb_Asset_Allocation tb_Asset_Allocation = new tb_Asset_Allocation();
        try {
            String status = assetRequest.getAsString("status");
            List<tb_Asset_Allocation> singleUnallocatedAsset = this.assetAllocationService.findBystatus(status);
            for (int i = 0; i < singleUnallocatedAsset.size(); i++) {
                JSONObject singleAssetObj = new JSONObject();
                tb_Asset_Allocation = singleUnallocatedAsset.get(i);
                tb_Item item = this.itemservice.findByItemCode(tb_Asset_Allocation.getAssetCode());
                singleAssetObj.put("assetCode", tb_Asset_Allocation.getAssetCode());
                singleAssetObj.put("locationId", tb_Asset_Allocation.getLocationId());
                singleAssetObj.put("personId", tb_Asset_Allocation.getPersonId());
                singleAssetObj.put("details", tb_Asset_Allocation.getDetails());
                if (item != null) {
                    singleAssetObj.put("depreciationMethod", item.getDepreciationMethod());
                } else {
                    singleAssetObj.put("depreciationMethod", "Not Set");
                }
                jsonObjectResponse.add(singleAssetObj);
            }
        } catch (Exception ex) {
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
            return jsonObjectResponse;
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"approveAssetAllocation"})
    public JSONObject approveAssetAllocation(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONObject jsonObjectResponse = new JSONObject();
        try {
            tb_Asset_Allocation tb_Asset_Allocation;
            this.LOGGER.info("GET ASSET TRANSFER APPROVAL REQUESTL" + assetRequest);
            String locationId = assetRequest.getAsString("locationId");
            String personId = assetRequest.getAsString("personId");
            String status = assetRequest.getAsString("status");
            if (!locationId.equals("")) {
                tb_Asset_Allocation = this.assetAllocationService.findByLocationId(locationId);
            } else {
                tb_Asset_Allocation = this.assetAllocationService.findByPersonId(personId);
            }
            tb_Asset_Allocation.setStatus(status);
            this.assetAllocationService.save(tb_Asset_Allocation);
            jsonObjectResponse.put("responseCode", "00");
            jsonObjectResponse.put("responseMessage", "Asset Allocation Approved Succesfully");
        } catch (Exception ex) {
            jsonObjectResponse.put("responseCode", "01");
            jsonObjectResponse.put("responseMessage", "Request cannot be empty");
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"assetJournal"})
    public void assetCaptureJournal(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        tb_Asset_Journal tb_Asset_Journal = new tb_Asset_Journal();
        try {
            this.LOGGER.info("RECIVE ASSETS JOURNAL REQUEST" + assetRequest);
            String assetCode = assetRequest.getAsString("assetCode");
            String activity = assetRequest.getAsString("activity");
            String details = assetRequest.getAsString("details");
            tb_Asset_Journal.setActivity(activity);
            tb_Asset_Journal.setTrackingDate(new Date());
            tb_Asset_Journal.setRecordDatetime(new Date());
            tb_Asset_Journal.setAssetCode(assetCode);
            tb_Asset_Journal.setActivityBy(assetRequest.getAsString("activityBy"));
            tb_Asset_Journal.setLocationId(assetRequest.getAsString("locationId"));
            this.assetJournalService.save(tb_Asset_Journal);
        } catch (Exception ex) {
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
    }

    @RequestMapping({"getAssetJournal"})
    public JSONArray getAssetJournal(HttpServletResponse httpResponse
    ) {
        tb_Asset_Journal tb_Asset_Journal = new tb_Asset_Journal();
        JSONArray resArray = new JSONArray();
        JSONObject resMessage = new JSONObject();
        try {
            this.LOGGER.info("RECIVE GET ASSETS JOURNAL REQUEST");
            List<tb_Asset_Journal> assetJournalList = this.assetJournalService.findAll();
            for (int i = 0; i < assetJournalList.size(); i++) {
                tb_Asset_Journal = assetJournalList.get(i);
                resMessage.put("assetCode", tb_Asset_Journal.getAssetCode());
                resMessage.put("activity", tb_Asset_Journal.getActivity());
                resMessage.put("details", tb_Asset_Journal.getDetails());
                resMessage.put("activityBy", tb_Asset_Journal.getActivityBy());
                resMessage.put("locationId", tb_Asset_Journal.getLocationId());
                resMessage.put("trackingDate", tb_Asset_Journal.getTrackingDate());
                resArray.add(resMessage);
            }
        } catch (Exception ex) {
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
            return resArray;
        }
        return resArray;
    }

    @RequestMapping({"updateWarrantDetails"})
    public JSONObject updateWarrantDetails(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONObject jsonObjectResponse = new JSONObject();
        try {
            String assetCode = assetRequest.getAsString("assetCode");
            tb_Asset tb_Asset = this.assetService.findByAssetCode(assetCode);
            String warrantyDetails = assetRequest.getAsString("warrantyDetails");
            String warrantExpiryDate = assetRequest.getAsString("warrantExpiryDate");
            tb_Asset.setWarrantExpiryDate(warrantExpiryDate);
            tb_Asset.setWarrantyDetails(warrantyDetails);
            this.assetService.save(tb_Asset);
            jsonObjectResponse.put("responseCode", "00");
            jsonObjectResponse.put("responseMessage", "Asset Warrant Updated Succesfully");
        } catch (Exception ex) {
            jsonObjectResponse.put("responseCode", "01");
            jsonObjectResponse.put("responseMessage", ex.getMessage());
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"updateDepreciationDetails"})
    public JSONObject updateDepreciationDetails(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONObject jsonObjectResponse = new JSONObject();
        try {
            String assetCode = assetRequest.getAsString("assetCode");
            tb_Asset tb_Asset = this.assetService.findByAssetCode(assetCode);
            String depreciationModel = assetRequest.getAsString("depreciationModel");
            String salvageValue = assetRequest.getAsString("salvageValue");
            String usefulLife = assetRequest.getAsString("usefulLife");
            tb_Asset.setDepreciationModel(depreciationModel);
            tb_Asset.setSalvageValue(salvageValue);
            tb_Asset.setUsefulLife(usefulLife);
            this.assetService.save(tb_Asset);
            jsonObjectResponse.put("responseCode", "00");
            jsonObjectResponse.put("responseMessage", "Asset Depreciation Updated Succesfully");
        } catch (Exception ex) {
            jsonObjectResponse.put("responseCode", "01");
            jsonObjectResponse.put("responseMessage", ex.getMessage());
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    @RequestMapping({"getAssetDepreciation"})
    public JSONObject getAssetDepreciation(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONObject resObject = new JSONObject();
        try {
            this.LOGGER.info("RECIVE GET ASSETS JOURNAL REQUEST");
            String assetCode = assetRequest.getAsString("assetCode");
            tb_Asset tb_Assets = this.assetService.findByAssetCode(assetCode);
            double initialCost = tb_Assets.getPurchasePrice();
            String salvageValue = tb_Assets.getSalvageValue();
            this.LOGGER.info("salvageValue::" + String.valueOf(salvageValue));
            this.LOGGER.info("initialCost::" + String.valueOf(initialCost));
            int currentYear = Calendar.getInstance().get(1);
            LocalDate localDate = LocalDate.now();
            int purchaseYear = localDate.getYear();
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate ld = LocalDate.parse(tb_Assets.getPurchaseDate().toString().substring(0, 10), f);
            int yearsLived = currentYear - ld.getYear();
            this.LOGGER.info("usefulLife::" + String.valueOf(yearsLived));
            double totalDepreciation = 0.0D;
            if (tb_Assets.getDepreciationModel().equalsIgnoreCase("straightline")) {
                totalDepreciation = calculateTotalDepreciation(initialCost, Double.parseDouble(salvageValue), Integer.valueOf(tb_Assets.getUsefulLife()).intValue(), yearsLived);
            } else {
                totalDepreciation = calculateReducingBlDepre(initialCost, Double.parseDouble(salvageValue), yearsLived);
            }
            double bookValue = initialCost - totalDepreciation;
            this.LOGGER.info("BOOKVALUE::" + String.valueOf(bookValue));
            this.LOGGER.info("totalDepreciation::" + String.valueOf(totalDepreciation));
            resObject.put("responseCode", "00");
            resObject.put("responseMessage", "Successful");
            resObject.put("bookValue", "KES:" + bookValue);
            resObject.put("totalDepreciation", "KES:" + totalDepreciation);
            resObject.put("assetCode", tb_Assets.getAssetCode());
            resObject.put("serialNumber", tb_Assets.getSerialNumber());
            resObject.put("salvageValue", tb_Assets.getSalvageValue());
        } catch (Exception ex) {
            resObject.put("responseCode", "01");
            resObject.put("responseMessage", "failed");
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
            return resObject;
        }
        return resObject;
    }

    @RequestMapping({"assetDisposal"})
    public JSONObject doAssetDisposal(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse
    ) {
        JSONObject resObject = new JSONObject();
        try {
            String updatedBy = assetRequest.getAsString("dicommissionedBy");
            this.LOGGER.info("RECIVE ASSETS DISPOSAL REQUEST");
            String assetCode = assetRequest.getAsString("assetCode");
            tb_Asset tb_Assets = this.assetService.findByAssetCode(assetCode);
            tb_Assets.setStatus("DECOMMISSIONED");
            this.assetService.save(tb_Assets);
            tb_Asset_Allocation tb_Asset_Allocation = this.assetAllocationService.findByAssetCode(tb_Assets.getAssetCode());
            if (tb_Asset_Allocation != null) {
                tb_Asset_Allocation.setStatus("DECOMMISSIONED");
                this.assetAllocationService.save(tb_Asset_Allocation);
            }
            JSONObject warehouseRequest = new JSONObject();
            warehouseRequest.put("assetCode", tb_Assets.getAssetCode());
            warehouseRequest.put("statusName", "Decommissioned");
            warehouseRequest.put("updatedBy", updatedBy);
            this.myAsyncService.httpPOST("http://10.22.25.92:8080/ALMWarehousing/updateInventoryStatus\n", warehouseRequest.toString());
            resObject.put("responseCode", "00");
            resObject.put("responseMessage", "Asset has been successfully decommissioned.");
        } catch (Exception ex) {
            resObject.put("responseCode", "01");
            resObject.put("responseMessage", "failed");
            this.LOGGER.info("EXCEPTION::" + ex.getMessage());
            return resObject;
        }
        return resObject;
    }

    public double calculateTotalDepreciation(double initialCost, double salvageValue, int usefulLife, int yearDiff) {
        double annualDepreciation = 0.0D;
        double totalDepreciation = 0.0D;
        if (usefulLife != 0) {
            annualDepreciation = (initialCost - salvageValue) / usefulLife;
            totalDepreciation = annualDepreciation * yearDiff;
        }
        return totalDepreciation;
    }

    public double calculateReducingBlDepre(double initialCost, double salvageValue, int usefulLife) {
        double depreciationRate = 0.25D;
        double bookValue = 0.0D;
        double accumulatedDepreciation = 0.0D;
        for (int year = 1; year <= usefulLife; year++) {
            double depreciation = (initialCost - accumulatedDepreciation) * depreciationRate;
            accumulatedDepreciation += depreciation;
            bookValue = initialCost - accumulatedDepreciation;
            System.out.println("Year " + year + " - Depreciation: " + depreciation + ", Book Value: " + bookValue);
        }
        return accumulatedDepreciation;
    }
}
