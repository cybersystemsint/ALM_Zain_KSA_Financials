package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.util.stream.Stream;


@Service
public class FarReportService {

    private static final Logger LOGGER = LogManager.getLogger(FarReportService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FarReportRepository repository;

    private static final String SELECT_SQL = "SELECT * FROM tb_FarReport WHERE assetId = ?";

    // INSERT SQL - 62 fields (63 total columns minus recordNo which is auto-increment)
    private static final String INSERT_SQL = "INSERT INTO tb_FarReport (" +
            "recordDatetime, book, assetId, quantity, description, assetType, creationDate, " +
            "serialNumber, tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, " +
            "depreciateFlag, cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, " +
            "assetStatus, value, partNumber, vendorName, vendorNumber, mergedCode, costAccount, " +
            "accumulatedDepreAccount, cipCostAccount, expenseCostCenter, expenseAccount, life, " +
            "datePlacedInService, cost, nbv, depreciationAmount, ytdDepreciation, depreciationReserve, " +
            "salvageValue, category, categoryDescription, locationSegment1, locationSegment2, " +
            "locationSegment3, locationSegment4, locations, sequenceNumber, createdBy, createdDate, " +
            "updatedBy, updatedDate, monthlyDepreciationAmt, accumulatedDepreciationAmt, " +
            "depreciationDate, netCost, statusFlag, changedBy, insertedBy, financialApproval, " +
            "changedDate, nodeType, mapped" +
            ") VALUES (" +
            "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +  // 20
            "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +  // 40
            "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +  // 60
            "?, ?, ?" +                                                        // 63
            ")";

    // UPDATE SQL - 61 fields to update + 1 WHERE clause = 62 parameters
    private static final String UPDATE_SQL = "UPDATE tb_FarReport SET " +
            "recordDatetime = ?, book = ?, quantity = ?, description = ?, assetType = ?, creationDate = ?, " +
            "serialNumber = ?, tagNumber = ?, picStatus = ?, picDate = ?, cipDeliveryDate = ?, linkId = ?, " +
            "acceptanceNumber = ?, depreciateFlag = ?, cipEu = ?, invoiceNumber = ?, poNumber = ?, " +
            "poLineNumber = ?, uplLine = ?, transferToNewFar = ?, assetStatus = ?, value = ?, partNumber = ?, " +
            "vendorName = ?, vendorNumber = ?, mergedCode = ?, costAccount = ?, accumulatedDepreAccount = ?, " +
            "cipCostAccount = ?, expenseCostCenter = ?, expenseAccount = ?, life = ?, datePlacedInService = ?, " +
            "cost = ?, nbv = ?, depreciationAmount = ?, ytdDepreciation = ?, depreciationReserve = ?, " +
            "salvageValue = ?, category = ?, categoryDescription = ?, locationSegment1 = ?, locationSegment2 = ?, " +
            "locationSegment3 = ?, locationSegment4 = ?, locations = ?, sequenceNumber = ?, createdBy = ?, " +
            "createdDate = ?, updatedBy = ?, updatedDate = ?, monthlyDepreciationAmt = ?, " +
            "accumulatedDepreciationAmt = ?, depreciationDate = ?, netCost = ?, statusFlag = ?, changedBy = ?, " +
            "insertedBy = ?, financialApproval = ?, changedDate = ?, nodeType = ?, mapped = ? " +
            "WHERE assetId = ?";

    private static final String[] EXPECTED_FIELDS = {
            "recordDatetime", "book", "assetId", "quantity", "description", "assetType", "creationDate",
            "serialNumber", "tagNumber", "picStatus", "picDate", "cipDeliveryDate", "linkId", "acceptanceNumber",
            "depreciateFlag", "cipEu", "invoiceNumber", "poNumber", "poLineNumber", "uplLine", "transferToNewFar",
            "assetStatus", "value", "partNumber", "vendorName", "vendorNumber", "mergedCode", "costAccount",
            "accumulatedDepreAccount", "cipCostAccount", "expenseCostCenter", "expenseAccount", "life",
            "datePlacedInService", "cost", "nbv", "depreciationAmount", "ytdDepreciation", "depreciationReserve",
            "salvageValue", "category", "categoryDescription", "locationSegment1", "locationSegment2",
            "locationSegment3", "locationSegment4", "locations", "sequenceNumber", "createdBy", "createdDate",
            "updatedBy", "updatedDate", "monthlyDepreciationAmt", "accumulatedDepreciationAmt", "depreciationDate",
            "netCost", "statusFlag", "changedBy", "insertedBy", "financialApproval", "changedDate", "nodeType", "mapped"
    };

    private final ConcurrentHashMap<String, String> taskStatus = new ConcurrentHashMap<>();

    public JSONObject processUpload(List<Map<String, Object>> data, String source) {
        JSONObject jsonObjectResponse = new JSONObject();
        if (data == null || data.isEmpty()) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", "No data provided for your " + source.toLowerCase() + " upload. Please ensure your file contains valid records.");
            return jsonObjectResponse;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date newDate = Date.valueOf(LocalDate.now());
        List<Object[]> insertBatchArgs = new ArrayList<>();
        List<String> updatedAssetIds = new ArrayList<>();

        // Track processed assetIds to avoid duplicates in same batch
        Set<String> processedAssetIds = new HashSet<>();

        int insertedRows = 0;
        int updatedRows = 0;
        int skippedRows = 0;

        try {
            for (Map<String, Object> rowMap : data) {
                if (!validateRow(rowMap, jsonObjectResponse, source)) {
                    skippedRows++;
                    continue;
                }

                String assetId = String.valueOf(rowMap.get("assetId"));

                // Skip if already processed in this batch
                if (processedAssetIds.contains(assetId)) {
                    LOGGER.warn("Skipping duplicate assetId in same batch: {}", assetId);
                    skippedRows++;
                    continue;
                }

                String processResult = processRow(rowMap, newDate, dateFormat, insertBatchArgs, updatedAssetIds, source);
                if ("inserted".equals(processResult)) {
                    insertedRows++;
                    processedAssetIds.add(assetId);
                } else if ("updated".equals(processResult)) {
                    updatedRows++;
                    processedAssetIds.add(assetId);
                } else {
                    skippedRows++;
                    // Don't add to processedAssetIds if skipped due to no changes
                }
            }

            // Batch processing with duplicate handling
            if (!insertBatchArgs.isEmpty()) {
                try {
                    int batchSize = 1000;
                    for (int i = 0; i < insertBatchArgs.size(); i += batchSize) {
                        List<Object[]> batch = insertBatchArgs.subList(i, Math.min(i + batchSize, insertBatchArgs.size()));
                        jdbcTemplate.batchUpdate(INSERT_SQL, batch);
                    }
                } catch (DuplicateKeyException e) {
                    // If batch fails due to duplicates, try individual inserts
                    LOGGER.warn("Batch insert failed due to duplicates, trying individual inserts");
                    insertedRows = 0; // Reset count

                    for (Object[] args : insertBatchArgs) {
                        try {
                            jdbcTemplate.update(INSERT_SQL, args);
                            insertedRows++;
                        } catch (DuplicateKeyException de) {
                            String assetId = String.valueOf(args[2]); // assetId is at index 2 for INSERT
                            LOGGER.warn("Skipping duplicate assetId during individual insert: {}", assetId);
                            skippedRows++;
                            insertedRows--; // Adjust count
                        }
                    }
                }
            }

            jsonObjectResponse.put("responseCode", "0");
            StringBuilder successMessage = new StringBuilder("Your " + source.toLowerCase() + " upload completed successfully! ");
            successMessage.append("Summary: ");
            if (insertedRows > 0) {
                successMessage.append(insertedRows).append(" new record").append(insertedRows == 1 ? "" : "s").append(" added. ");
            }
            if (updatedRows > 0) {
                successMessage.append(updatedRows).append(" record").append(updatedRows == 1 ? "" : "s").append(" updated (Asset IDs: ")
                        .append(String.join(", ", updatedAssetIds)).append("). ");
            }
            if (skippedRows > 0) {
                successMessage.append(skippedRows).append(" record").append(skippedRows == 1 ? "" : "s").append(" skipped due to duplicates, no changes, or invalid data.");
            }
            if (insertedRows == 0 && updatedRows == 0 && skippedRows > 0) {
                successMessage = new StringBuilder("No changes were made during your " + source.toLowerCase() + " upload. All ")
                        .append(skippedRows).append(" record").append(skippedRows == 1 ? "" : "s").append(" were either duplicates with no changes or invalid.");
            }
            jsonObjectResponse.put("responseMessage", successMessage.toString());
            jsonObjectResponse.put("insertedCount", insertedRows);
            jsonObjectResponse.put("updatedCount", updatedRows);
            jsonObjectResponse.put("skippedCount", skippedRows);
            jsonObjectResponse.put("updatedAssetIds", updatedAssetIds);
        } catch (DataAccessException e) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", "A database error occurred while processing your " + source.toLowerCase() + " upload: " + e.getMessage() +
                    ". Please try again or contact support.");
            LOGGER.error("Database error processing {} data", source, e);
        } catch (Exception e) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", "An unexpected error occurred while processing your " + source.toLowerCase() + " upload: " + e.getMessage() +
                    ". Please try again or contact support.");
            LOGGER.error("Unexpected error processing {} data", source, e);
        }
        return jsonObjectResponse;
    }

    private boolean validateRow(Map<String, Object> rowMap, JSONObject jsonObjectResponse, String source) {
        String assetId = String.valueOf(rowMap.get("assetId"));
        if (StringUtils.isEmpty(assetId)) {
            LOGGER.info("Skipping invalid {} row - missing assetId", source);
            return false;
        }

        for (String key : rowMap.keySet()) {
            String normalizedKey = key.equalsIgnoreCase("life") ? "life" : key;
            if (!Arrays.asList(EXPECTED_FIELDS).contains(normalizedKey)) {
                LOGGER.warn("Unknown field in {} row for assetId: {} - field: {}", source, assetId, key);
                return false;
            }
        }
        return true;
    }

    private String processRow(Map<String, Object> rowMap, Date newDate, SimpleDateFormat format,
                              List<Object[]> insertBatchArgs, List<String> updatedAssetIds, String source) {
        String assetId = String.valueOf(rowMap.get("assetId"));
        try {
            // Check if record already exists
            Map<String, Object> existing = jdbcTemplate.queryForMap(SELECT_SQL, assetId);

            // Record exists - check if there are any changes
            if (hasChanges(rowMap, existing)) {
                LOGGER.info("Updating changed {} record for assetId: {} - changes detected", source, assetId);
                Object[] updateArgs = buildUpdateArgs(rowMap, newDate, format);
                jdbcTemplate.update(UPDATE_SQL, updateArgs);
                updatedAssetIds.add(assetId);
                return "updated";
            } else {
                LOGGER.info("Skipping unchanged duplicate {} for assetId: {} - no changes detected", source, assetId);
                return "skipped";
            }
        } catch (EmptyResultDataAccessException e) {
            // Record doesn't exist - prepare for insert
            LOGGER.info("Inserting new {} record for assetId: {}", source, assetId);
            insertBatchArgs.add(buildInsertArgs(rowMap, newDate, format));
            return "inserted";
        }
    }

    private Object[] buildInsertArgs(Map<String, Object> rowMap, Date newDate, SimpleDateFormat format) {
        Object[] args = new Object[63]; // 63 fields for INSERT
        int index = 0;

        args[index++] = newDate; // recordDatetime
        args[index++] = String.valueOf(rowMap.getOrDefault("book", ""));
        args[index++] = String.valueOf(rowMap.get("assetId")); // assetId - required for INSERT
        args[index++] = parseInteger(String.valueOf(rowMap.getOrDefault("quantity", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("description", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("assetType", ""));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("creationDate", "")), format);
        args[index++] = String.valueOf(rowMap.getOrDefault("serialNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("tagNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("picStatus", ""));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("picDate", "")), format);
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("cipDeliveryDate", "")), format);
        args[index++] = String.valueOf(rowMap.getOrDefault("linkId", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("acceptanceNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("depreciateFlag", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("cipEu", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("invoiceNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("poNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("poLineNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("uplLine", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("transferToNewFar", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("assetStatus", ""));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("value", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("partNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("vendorName", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("vendorNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("mergedCode", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("costAccount", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("accumulatedDepreAccount", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("cipCostAccount", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("expenseCostCenter", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("expenseAccount", ""));
        args[index++] = parseInteger(String.valueOf(rowMap.getOrDefault("life", "")));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("datePlacedInService", "")), format);
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("cost", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("nbv", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("depreciationAmount", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("ytdDepreciation", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("depreciationReserve", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("salvageValue", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("category", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("categoryDescription", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locationSegment1", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locationSegment2", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locationSegment3", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locationSegment4", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locations", ""));
        args[index++] = parseInteger(String.valueOf(rowMap.getOrDefault("sequenceNumber", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("createdBy", ""));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("createdDate", "")), format) != null
                ? parseDate(String.valueOf(rowMap.getOrDefault("createdDate", "")), format) : newDate;
        args[index++] = String.valueOf(rowMap.getOrDefault("updatedBy", ""));
        args[index++] = newDate; // updatedDate
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("monthlyDepreciationAmt", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("accumulatedDepreciationAmt", "")));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("depreciationDate", "")), format);
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("netCost", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("statusFlag", "New"));
        args[index++] = String.valueOf(rowMap.getOrDefault("changedBy", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("insertedBy", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("financialApproval", ""));
        args[index++] = newDate; // changedDate
        args[index++] = String.valueOf(rowMap.getOrDefault("nodeType", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("mapped", ""));

        if (index != 63) {
            LOGGER.error("FATAL: INSERT field count mismatch - Expected: 63, Got: {}, AssetId: {}",
                    index, rowMap.get("assetId"));
            throw new IllegalStateException(String.format(
                    "INSERT field count mismatch: expected 63, got %d", index));
        }

        return args;
    }

    private Object[] buildUpdateArgs(Map<String, Object> rowMap, Date newDate, SimpleDateFormat format) {
        Object[] args = new Object[63]; // 62 SET fields + 1 WHERE clause = 62 parameters
        int index = 0;

        // SET clause fields (61 fields - all except assetId which is in WHERE clause)
        args[index++] = newDate; // recordDatetime
        args[index++] = String.valueOf(rowMap.getOrDefault("book", ""));
        args[index++] = parseInteger(String.valueOf(rowMap.getOrDefault("quantity", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("description", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("assetType", ""));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("creationDate", "")), format);
        args[index++] = String.valueOf(rowMap.getOrDefault("serialNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("tagNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("picStatus", ""));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("picDate", "")), format);
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("cipDeliveryDate", "")), format);
        args[index++] = String.valueOf(rowMap.getOrDefault("linkId", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("acceptanceNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("depreciateFlag", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("cipEu", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("invoiceNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("poNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("poLineNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("uplLine", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("transferToNewFar", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("assetStatus", ""));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("value", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("partNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("vendorName", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("vendorNumber", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("mergedCode", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("costAccount", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("accumulatedDepreAccount", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("cipCostAccount", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("expenseCostCenter", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("expenseAccount", ""));
        args[index++] = parseInteger(String.valueOf(rowMap.getOrDefault("life", "")));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("datePlacedInService", "")), format);
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("cost", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("nbv", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("depreciationAmount", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("ytdDepreciation", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("depreciationReserve", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("salvageValue", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("category", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("categoryDescription", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locationSegment1", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locationSegment2", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locationSegment3", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locationSegment4", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("locations", ""));
        args[index++] = parseInteger(String.valueOf(rowMap.getOrDefault("sequenceNumber", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("createdBy", ""));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("createdDate", "")), format) != null
                ? parseDate(String.valueOf(rowMap.getOrDefault("createdDate", "")), format) : newDate;
        args[index++] = String.valueOf(rowMap.getOrDefault("updatedBy", ""));
        args[index++] = newDate; // updatedDate
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("monthlyDepreciationAmt", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("accumulatedDepreciationAmt", "")));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("depreciationDate", "")), format);
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("netCost", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("statusFlag", "New"));
        args[index++] = String.valueOf(rowMap.getOrDefault("changedBy", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("insertedBy", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("financialApproval", ""));
        args[index++] = newDate; // changedDate
        args[index++] = String.valueOf(rowMap.getOrDefault("nodeType", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("mapped", ""));

        // WHERE clause - assetId
        args[index++] = String.valueOf(rowMap.get("assetId"));

        if (index != 63) {
            LOGGER.error("FATAL: UPDATE field count mismatch - Expected: 63, Got: {}, AssetId: {}",
                    index, rowMap.get("assetId"));
            throw new IllegalStateException(String.format(
                    "UPDATE field count mismatch: expected 63, got %d", index));
        }

        return args;
    }

    private boolean hasChanges(Map<String, Object> newMap, Map<String, Object> existingMap) {
        // Fields to ignore during comparison
        Set<String> ignoreKeys = new HashSet<>(Arrays.asList(
                "recordNo", "recordDatetime", "createdDate", "updatedDate", "changedDate"
        ));

        // Known numeric fields for better comparison
        Set<String> numericFields = new HashSet<>(Arrays.asList(
                "quantity", "value", "cost", "nbv", "depreciationAmount", "ytdDepreciation",
                "depreciationReserve", "salvageValue", "monthlyDepreciationAmt",
                "accumulatedDepreciationAmt", "netCost", "sequenceNumber", "life",
                "poLineNumber", "uplLine"
        ));

        List<String> changedFields = new ArrayList<>();

        for (String key : newMap.keySet()) {
            String normalizedKey = key.equalsIgnoreCase("life") ? "life" : key;
            String lowerKey = normalizedKey.toLowerCase();

            // Skip ignored fields
            if (ignoreKeys.contains(lowerKey) || ignoreKeys.contains(normalizedKey)) {
                continue;
            }

            Object newVal = newMap.get(key);
            Object existVal = existingMap.get(lowerKey);

            // Try different key variations if not found
            if (existVal == null) {
                existVal = existingMap.get(normalizedKey);
            }
            if (existVal == null) {
                existVal = existingMap.get(key);
            }

            // Enhanced comparison for numeric fields
            boolean valuesEqual;
            if (numericFields.contains(normalizedKey.toLowerCase()) || numericFields.contains(normalizedKey)) {
                valuesEqual = numericValuesEqual(newVal, existVal);
            } else {
                valuesEqual = objectsEqual(newVal, existVal);
            }

            if (!valuesEqual) {
                changedFields.add(String.format("%s: '%s' -> '%s'",
                        normalizedKey,
                        existVal != null ? existVal.toString() : "null",
                        newVal != null ? newVal.toString() : "null"));
            }
        }

        if (!changedFields.isEmpty()) {
            String assetId = String.valueOf(newMap.get("assetId"));
            LOGGER.info("Changes detected for assetId {}: {}", assetId, changedFields);
            return true;
        } else {
            String assetId = String.valueOf(newMap.get("assetId"));
            LOGGER.info("No changes detected for assetId {} - skipping update", assetId);
            return false;
        }
    }

    private boolean numericValuesEqual(Object o1, Object o2) {
        if (o1 == null && o2 == null) return true;
        if (o1 == null || o2 == null) return false;

        String str1 = String.valueOf(o1).trim();
        String str2 = String.valueOf(o2).trim();

        // Handle empty/null cases
        if ((str1.isEmpty() || str1.equals("null")) && (str2.isEmpty() || str2.equals("null"))) {
            return true;
        }

        // If one is empty/null and other has value, they're different
        if ((str1.isEmpty() || str1.equals("null")) || (str2.isEmpty() || str2.equals("null"))) {
            return false;
        }

        try {
            // For decimal numbers, compare as Double
            if (str1.contains(".") || str2.contains(".")) {
                Double val1 = Double.parseDouble(str1);
                Double val2 = Double.parseDouble(str2);
                return Double.compare(val1, val2) == 0;
            } else {
                // For integers, compare as Long
                Long val1 = Long.parseLong(str1);
                Long val2 = Long.parseLong(str2);
                return val1.equals(val2);
            }
        } catch (NumberFormatException e) {
            // If parsing fails, fall back to string comparison
            return str1.equals(str2);
        }
    }

    private boolean objectsEqual(Object o1, Object o2) {
        if (o1 == null && o2 == null) return true;
        if (o1 == null || o2 == null) return false;

        // Handle different data types that might represent the same value
        String str1 = String.valueOf(o1).trim();
        String str2 = String.valueOf(o2).trim();

        // Handle empty strings and null values consistently
        if (str1.isEmpty() && str2.isEmpty()) return true;
        if (str1.equals("null") && str2.isEmpty()) return true;
        if (str1.isEmpty() && str2.equals("null")) return true;

        return str1.equals(str2);
    }

    private Date parseDate(String dateStr, SimpleDateFormat format) {
        if (StringUtils.isEmpty(dateStr) || "null".equals(dateStr)) return null;
        try {
            return new Date(format.parse(dateStr.trim()).getTime());
        } catch (ParseException e) {
            LOGGER.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private Integer parseInteger(String str) {
        if (StringUtils.isEmpty(str) || "null".equals(str)) return null;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse integer: {}", str);
            return null;
        }
    }

    private Double parseDouble(String str) {
        if (StringUtils.isEmpty(str) || "null".equals(str)) return null;
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse double: {}", str);
            return null;
        }
    }

    private Specification<tb_FarReport> createSpecification(String column, String value, String operator) {
        return (root, query, cb) -> {
            if (StringUtils.isEmpty(column) || StringUtils.isEmpty(value)) {
                return cb.isTrue(cb.literal(true));
            }
            String normalizedColumn = column.equalsIgnoreCase("life") ? "life" : column;
            if (!Arrays.asList(EXPECTED_FIELDS).contains(normalizedColumn)) {
                throw new IllegalArgumentException("Invalid column name: " + column);
            }
            switch (operator.toLowerCase()) {
                case "equals":
                    return cb.equal(root.get(normalizedColumn), value);
                case "like":
                    return cb.like(cb.lower(root.get(normalizedColumn)), "%" + value.toLowerCase() + "%");
                default:
                    throw new IllegalArgumentException("Unsupported operator: " + operator);
            }
        };
    }
}