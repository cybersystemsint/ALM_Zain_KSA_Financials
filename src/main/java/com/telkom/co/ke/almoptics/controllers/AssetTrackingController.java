/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.entities.tbAssetTracking;
import com.telkom.co.ke.almoptics.services.AssetTrackingService;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import com.telkom.co.ke.almoptics.entities.tbNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.telkom.co.ke.almoptics.services.tbNodeService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;

/**
 *
 * @author jgithu
 */
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping({"/"})
public class AssetTrackingController {

    private final Logger log = LogManager.getLogger(AssetTrackingController.class.getName());

    private final AssetTrackingService assetTrackingService;

    @Autowired
    private tbNodeService nodeService;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AssetTrackingController(AssetTrackingService assetTrackingService, JdbcTemplate jdbcTemplate) {

        this.assetTrackingService = assetTrackingService;
        this.jdbcTemplate = jdbcTemplate;

    }

    //UPLOAD NEW FAR  REPORT PER KSA FORMART
    @RequestMapping({"addAssetTracking"})
    public JSONObject addAssetTracking(@RequestBody String req, HttpServletResponse httpResponse) throws ParseException {
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
                this.log.info("RECEIVED NEW ASSET TRACKING REQUEST " + req);
                //CHECK IF THE SERIAL NO IS FROM ACTIVE INVENTORY 
                tbNode nodes = nodeService.findBySerialNumber(jsonObject.getString("serialNumber"));
                tbAssetTracking tracking = new tbAssetTracking();
                tracking.setRecordDatetime(newDate);
                tracking.setSerialNumber(jsonObject.getString("serialNumber"));
                tracking.setSiteId(jsonObject.getString("siteId"));
                tracking.setActionType(jsonObject.getString("actionType"));
                tracking.setUsername(jsonObject.getString("username"));
                if (nodes != null && nodes.getSerialNumber() != null && !nodes.getSerialNumber().isEmpty()) {
                    tracking.setModel(nodes.getModel());
                    tracking.setManufacturer(String.valueOf(nodes.getManufacturerId()));
                    tracking.setManufacturerDate(nodes.getManufacturingDate());
                }

                tracking.setChangeDate(newDate);
                this.assetTrackingService.save(tracking);
                this.log.info("NEW ASSET TRACKING SAVED " + tracking);
                jsonObjectResponse.put("responseCode", "0");
                jsonObjectResponse.put("responseMessage", "Success");

            }

        } catch (JSONException ex) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", ex.getMessage());
            this.log.info("EXCEPTION::" + ex.getMessage());
        }
        return jsonObjectResponse;
    }

    @PostMapping(value = "getAssetTracking", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> acceptanceReport(@RequestBody JSONObject assetRequest) {

        String serialNumber = assetRequest.getAsString("serialNumber");
        String siteId = assetRequest.getAsString("siteId");

        String username = assetRequest.getAsString("username");
        String columnName = assetRequest.containsKey("columnName") ? assetRequest.getAsString("columnName") : "";
        String searchQuery = assetRequest.containsKey("searchQuery") ? assetRequest.getAsString("searchQuery") : "";
        ///  String asAtDate = assetRequest.containsKey("asAtDate") ? assetRequest.getAsString("asAtDate") : null;

        List<Object> params = new ArrayList<>();
        String whereClause = " WHERE 1=1";

        if (!serialNumber.equalsIgnoreCase("")) {
            whereClause += " AND serialNumber = ?";
            params.add(serialNumber);
        }

        if (!siteId.equalsIgnoreCase("")) {
            whereClause += " AND siteId = ?";
            params.add(siteId);
        }
         if (!username.equalsIgnoreCase("")) {
            whereClause += " AND username = ?";
            params.add(username);
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND " + columnName.toLowerCase() + " LIKE ?";
            params.add("%" + searchQuery + "%");
        }

        // jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");
        String countDetails = "SELECT COUNT(*) FROM tb_AssetTracking" + whereClause;
        int totalRecords = jdbcTemplate.queryForObject(countDetails, Integer.class, params.toArray());

        int page = Math.max(assetRequest.containsKey("page") ? assetRequest.getAsNumber("page").intValue() : 1, 1);
        int size = Math.max(assetRequest.containsKey("size") ? assetRequest.getAsNumber("size").intValue() : 500, 1);

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        String paginationSql = "";

        if (page == 0 && size == 0) {
            paginationSql = "";
        } else if (page == 1 && size == 20000) {
            page = 0;
            size = totalRecords;
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;

            paginationSql = " LIMIT " + size + " OFFSET " + offset;

        } else {
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;
            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        }

        //List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());
        String newScript = "SELECT * FROM `tb_AssetTracking` "
                + whereClause + paginationSql;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(newScript, params.toArray());

        // Add an incremental column programmatically
      //  AtomicInteger counter = new AtomicInteger(1);
       // result.forEach(row -> row.put("recordNo", counter.getAndIncrement()));

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;
    }

    //NEW END POINT FOR GETTING ASSET TRACKING
//    @GetMapping({"getAssetTracking"})
//    public JSONArray getUnmappedActiveAssets(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
//        JSONArray jsonObjectResponse = new JSONArray();
//        try {
//            this.log.info("GET ALL ASSET TRACKING ");
//            String serialNumber = assetRequest.getAsString("serialNumber");
//            String siteId = assetRequest.getAsString("siteId");
//            List<tbAssetTracking> allReport = this.assetTrackingService.findAll();
//            if (!serialNumber.equalsIgnoreCase("") || !serialNumber.isEmpty()) {
//                allReport = this.assetTrackingService.findBySerialNumber(serialNumber);
//            } else if (!siteId.equalsIgnoreCase("") || !siteId.isEmpty()) {
//                allReport = this.assetTrackingService.findBySiteId(siteId);
//            }
//
//            for (int i = 0; i < allReport.size(); i++) {
//                JSONObject singleAssetObj = new JSONObject();
//                tbAssetTracking financeRPT = allReport.get(i);
//
//                singleAssetObj.put("recordNo", financeRPT.getRecordNo());
//                singleAssetObj.put("recordDatetime", financeRPT.getRecordDatetime());
//                singleAssetObj.put("serialNumber", financeRPT.getSerialNumber());
//                singleAssetObj.put("rfid", financeRPT.getRfid());
//                singleAssetObj.put("tagNumber", financeRPT.getTagNumber());
//                singleAssetObj.put("siteId", financeRPT.getSiteId());
//                singleAssetObj.put("manufacturer", financeRPT.getManufacturer());
//                singleAssetObj.put("changeDate", financeRPT.getChangeDate());
//                singleAssetObj.put("username", financeRPT.getUsername());
//                singleAssetObj.put("actionType", financeRPT.getActionType());
//                singleAssetObj.put("model", financeRPT.getModel());
//                singleAssetObj.put("manufacturerDate", financeRPT.getManufacturerDate());
//                jsonObjectResponse.add(singleAssetObj);
//            }
//        } catch (NumberFormatException ex) {
//            this.log.info("EXCEPTION::" + ex.getMessage());
//            return jsonObjectResponse;
//        }
//        return jsonObjectResponse;
//    }

}
