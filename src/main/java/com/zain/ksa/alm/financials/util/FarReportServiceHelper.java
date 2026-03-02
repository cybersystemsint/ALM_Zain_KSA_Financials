package com.zain.ksa.alm.financials.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
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
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.repository.FarReportRepository;

import net.minidev.json.JSONObject;

@Service
public class FarReportServiceHelper {

    private static final Logger LOGGER = LogManager.getLogger(FarReportServiceHelper.class);

    private final JdbcTemplate        jdbcTemplate;
    private final FarReportRepository repository;

    @Autowired
    public FarReportServiceHelper(JdbcTemplate jdbcTemplate, FarReportRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository   = repository;
    }

    // ── Static constants — EXPECTED_FIELDS must be declared first because
    //    ALLOWED_COLUMNS depends on it. Java initializes static fields in
    //    top-to-bottom order; reversing these caused the "cannot find symbol"
    //    compiler error. ──────────────────────────────────────────────────────

    private static final String[] EXPECTED_FIELDS = {
        "recordDatetime", "book", "assetId", "quantity", "description",
        "assetType", "creationDate", "serialNumber", "tagNumber", "picStatus",
        "picDate", "cipDeliveryDate", "linkId", "acceptanceNumber", "depreciateFlag",
        "cipEu", "invoiceNumber", "poNumber", "poLineNumber", "uplLine",
        "transferToNewFar", "assetStatus", "value", "partNumber", "vendorName",
        "vendorNumber", "mergedCode", "costAccount", "accumulatedDepreAccount",
        "cipCostAccount", "expenseCostCenter", "expenseAccount", "life",
        "datePlacedInService", "cost", "nbv", "depreciationAmount", "ytdDepreciation",
        "depreciationReserve", "salvageValue", "category", "categoryDescription",
        "locationSegment1", "locationSegment2", "locationSegment3", "locationSegment4",
        "locations", "sequenceNumber", "createdBy", "createdDate", "updatedBy",
        "updatedDate", "monthlyDepreciationAmt", "accumulatedDepreciationAmt",
        "depreciationDate", "netCost", "statusFlag", "changedBy", "insertedBy",
        "financialApproval", "changedDate", "nodeType"
    };

    /**
     * Set for O(1) whitelist lookups — used by fetchFarReport() and validateRow().
     * Declared AFTER EXPECTED_FIELDS so the initializer can reference it safely.
     * Previously this was declared before EXPECTED_FIELDS, which caused the
     * "cannot find symbol: variable ALLOWED_COLUMNS" compile error.
     */
    private static final Set<String> ALLOWED_COLUMNS = new HashSet<>(Arrays.asList(EXPECTED_FIELDS));

    // ── SQL constants ─────────────────────────────────────────────────────────

    private static final String SELECT_SQL = "SELECT * FROM tb_FarReport WHERE assetId = ?";

    private static final String INSERT_SQL = "INSERT INTO tb_FarReport ("
            + "recordDatetime, book, assetId, quantity, description, assetType, creationDate, "
            + "serialNumber, tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, "
            + "depreciateFlag, cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, "
            + "assetStatus, value, partNumber, vendorName, vendorNumber, mergedCode, costAccount, "
            + "accumulatedDepreAccount, cipCostAccount, expenseCostCenter, expenseAccount, life, "
            + "datePlacedInService, cost, nbv, depreciationAmount, ytdDepreciation, depreciationReserve, "
            + "salvageValue, category, categoryDescription, locationSegment1, locationSegment2, "
            + "locationSegment3, locationSegment4, locations, sequenceNumber, createdBy, createdDate, "
            + "updatedBy, updatedDate, monthlyDepreciationAmt, accumulatedDepreciationAmt, "
            + "depreciationDate, netCost, statusFlag, changedBy, insertedBy, financialApproval, "
            + "changedDate, nodeType"
            + ") VALUES ("
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?"
            + ")";

    private static final String UPDATE_SQL = "UPDATE tb_FarReport SET "
            + "recordDatetime = ?, book = ?, quantity = ?, description = ?, assetType = ?, creationDate = ?, "
            + "serialNumber = ?, tagNumber = ?, picStatus = ?, picDate = ?, cipDeliveryDate = ?, linkId = ?, "
            + "acceptanceNumber = ?, depreciateFlag = ?, cipEu = ?, invoiceNumber = ?, poNumber = ?, "
            + "poLineNumber = ?, uplLine = ?, transferToNewFar = ?, assetStatus = ?, value = ?, partNumber = ?, "
            + "vendorName = ?, vendorNumber = ?, mergedCode = ?, costAccount = ?, accumulatedDepreAccount = ?, "
            + "cipCostAccount = ?, expenseCostCenter = ?, expenseAccount = ?, life = ?, datePlacedInService = ?, "
            + "cost = ?, nbv = ?, depreciationAmount = ?, ytdDepreciation = ?, depreciationReserve = ?, "
            + "salvageValue = ?, category = ?, categoryDescription = ?, locationSegment1 = ?, locationSegment2 = ?, "
            + "locationSegment3 = ?, locationSegment4 = ?, locations = ?, sequenceNumber = ?, createdBy = ?, "
            + "createdDate = ?, updatedBy = ?, updatedDate = ?, monthlyDepreciationAmt = ?, "
            + "accumulatedDepreciationAmt = ?, depreciationDate = ?, netCost = ?, statusFlag = ?, changedBy = ?, "
            + "insertedBy = ?, financialApproval = ?, changedDate = ?, nodeType = ? "
            + "WHERE assetId = ?";

    private final ConcurrentHashMap<String, String> taskStatus = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // FETCH
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> fetchFarReport(
            String assetId,
            String columnName,
            String searchQuery,
            String dateFrom,
            String dateTo,
            int page,
            int size) {

        StringBuilder where  = new StringBuilder(" WHERE 1=1");
        List<Object>  params = new ArrayList<>();

        if (!assetId.isBlank()) {
            where.append(" AND assetId = ?");
            params.add(assetId);
        }
        if (!columnName.isBlank() && !searchQuery.isBlank()) {
            // ALLOWED_COLUMNS is now a Set<String> — O(1) lookup, no symbol error
            if (!ALLOWED_COLUMNS.contains(columnName)) {
                throw new IllegalArgumentException("Invalid columnName: " + columnName);
            }
            where.append(" AND LOWER(").append(columnName).append(") LIKE LOWER(?)");
            params.add("%" + searchQuery + "%");
        }
        if (!dateFrom.isBlank()) {
            where.append(" AND recordDatetime >= ?");
            params.add(dateFrom);
        }
        if (!dateTo.isBlank()) {
            where.append(" AND recordDatetime <= ?");
            params.add(dateTo);
        }

        boolean hasFilters = !params.isEmpty();

        // ── Query 1: COUNT + filtered aggregates in one round-trip ────────────
        String aggregateSql =
            "SELECT COUNT(*) AS cnt," +
            "  COALESCE(SUM(cost), 0) AS filteredCost," +
            "  COALESCE(SUM(netCost), 0) AS filteredNBV," +
            "  COALESCE(SUM(accumulatedDepreciationAmt), 0) AS filteredDepreciation" +
            " FROM tb_FarReport" + where;

        Map<String, Object> agg = jdbcTemplate.queryForMap(aggregateSql, params.toArray());

        int        totalRecords         = ((Number) agg.get("cnt")).intValue();
        BigDecimal filteredCost         = toBigDecimal(agg.get("filteredCost"));
        BigDecimal filteredNBV          = toBigDecimal(agg.get("filteredNBV"));
        BigDecimal filteredDepreciation = toBigDecimal(agg.get("filteredDepreciation"));

        // ── Query 2 (conditional): whole-table totals ─────────────────────────
        BigDecimal totalCost;
        BigDecimal totalNBV;
        BigDecimal totalDepreciation;

        if (!hasFilters) {
            totalCost         = filteredCost;
            totalNBV          = filteredNBV;
            totalDepreciation = filteredDepreciation;
        } else {
            Map<String, Object> totals = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(cost), 0) AS totalCost," +
                "  COALESCE(SUM(netCost), 0) AS totalNBV," +
                "  COALESCE(SUM(accumulatedDepreciationAmt), 0) AS totalDepreciation" +
                " FROM tb_FarReport");
            totalCost         = toBigDecimal(totals.get("totalCost"));
            totalNBV          = toBigDecimal(totals.get("totalNBV"));
            totalDepreciation = toBigDecimal(totals.get("totalDepreciation"));
        }

        // ── Query 3: page data ────────────────────────────────────────────────
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(page * size);

        String dataSql =
            "SELECT recordNo, recordDatetime, book, assetId, quantity, description, assetType, creationDate," +
            " serialNumber, tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, depreciateFlag," +
            " cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, assetStatus, value, partNumber," +
            " vendorName, vendorNumber, mergedCode, costAccount, accumulatedDepreAccount, cipCostAccount, expenseCostCenter," +
            " expenseAccount, Life, datePlacedInService, cost, nbv, depreciationAmount, ytdDepreciation, depreciationReserve," +
            " salvageValue, category, categoryDescription, locationSegment1, locationSegment2, locationSegment3," +
            " locationSegment4, locations, sequenceNumber, createdBy, createdDate, updatedBy, updatedDate," +
            " monthlyDepreciationAmt, accumulatedDepreciationAmt, depreciationDate, netCost, statusFlag, changedBy," +
            " insertedBy, financialApproval, changedDate, nodeType" +
            " FROM tb_FarReport" + where + " ORDER BY recordNo ASC LIMIT ? OFFSET ?";

        List<Map<String, Object>> data = jdbcTemplate.query(dataSql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            int cols = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= cols; i++) {
                row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
            }
            return row;
        }, dataParams.toArray());

        // ── Response ──────────────────────────────────────────────────────────
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data",                 data);
        response.put("totalRecords",         totalRecords);
        response.put("totalPages",           (int) Math.ceil((double) totalRecords / size));
        response.put("currentPage",          page);
        response.put("pageSize",             size);
        response.put("filteredCost",         filteredCost);
        response.put("filteredNBV",          filteredNBV);
        response.put("filteredDepreciation", filteredDepreciation);
        response.put("totalCost",            totalCost);
        response.put("totalNBV",             totalNBV);
        response.put("totalDepreciation",    totalDepreciation);
        return response;
    }

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPLOAD
    // ─────────────────────────────────────────────────────────────────────────

    public JSONObject processUpload(List<Map<String, Object>> data, String source) {
        JSONObject jsonObjectResponse = new JSONObject();
        if (data == null || data.isEmpty()) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", "No data provided for your " + source.toLowerCase()
                    + " upload. Please ensure your file contains valid records.");
            return jsonObjectResponse;
        }

        SimpleDateFormat dateFormat  = new SimpleDateFormat("yyyy-MM-dd");
        Date             newDate     = Date.valueOf(LocalDate.now());
        List<Object[]>   insertBatchArgs  = new ArrayList<>();
        List<String>     updatedAssetIds  = new ArrayList<>();
        Set<String>      processedAssetIds = new HashSet<>();

        int insertedRows = 0;
        int updatedRows  = 0;
        int skippedRows  = 0;

        try {
            for (Map<String, Object> rowMap : data) {
                if (!validateRow(rowMap, jsonObjectResponse, source)) {
                    skippedRows++;
                    continue;
                }

                String assetId = String.valueOf(rowMap.get("assetId"));

                if (processedAssetIds.contains(assetId)) {
                    LOGGER.warn("Skipping duplicate assetId in same batch: {}", assetId);
                    skippedRows++;
                    continue;
                }

                String processResult = processRow(rowMap, newDate, dateFormat, insertBatchArgs,
                                                  updatedAssetIds, source);
                if ("inserted".equals(processResult)) {
                    insertedRows++;
                    processedAssetIds.add(assetId);
                } else if ("updated".equals(processResult)) {
                    updatedRows++;
                    processedAssetIds.add(assetId);
                } else {
                    skippedRows++;
                }
            }

            if (!insertBatchArgs.isEmpty()) {
                try {
                    int batchSize = 1000;
                    for (int i = 0; i < insertBatchArgs.size(); i += batchSize) {
                        List<Object[]> batch = insertBatchArgs.subList(
                                i, Math.min(i + batchSize, insertBatchArgs.size()));
                        jdbcTemplate.batchUpdate(INSERT_SQL, batch);
                    }
                } catch (DuplicateKeyException e) {
                    LOGGER.warn("Batch insert failed due to duplicates, retrying individually");
                    insertedRows = 0;
                    for (Object[] args : insertBatchArgs) {
                        try {
                            jdbcTemplate.update(INSERT_SQL, args);
                            insertedRows++;
                        } catch (DuplicateKeyException de) {
                            LOGGER.warn("Skipping duplicate assetId during individual insert: {}", args[2]);
                            skippedRows++;
                            insertedRows--;
                        }
                    }
                }
            }

            jsonObjectResponse.put("responseCode", "0");
            StringBuilder successMessage = new StringBuilder(
                    "Your " + source.toLowerCase() + " upload completed successfully! Summary: ");
            if (insertedRows > 0)
                successMessage.append(insertedRows).append(" new record")
                        .append(insertedRows == 1 ? "" : "s").append(" added. ");
            if (updatedRows > 0)
                successMessage.append(updatedRows).append(" record")
                        .append(updatedRows == 1 ? "" : "s")
                        .append(" updated (Asset IDs: ").append(String.join(", ", updatedAssetIds)).append("). ");
            if (skippedRows > 0)
                successMessage.append(skippedRows).append(" record")
                        .append(skippedRows == 1 ? "" : "s")
                        .append(" skipped due to duplicates, no changes, or invalid data.");
            if (insertedRows == 0 && updatedRows == 0 && skippedRows > 0)
                successMessage = new StringBuilder("No changes were made during your "
                        + source.toLowerCase() + " upload. All ")
                        .append(skippedRows).append(" record")
                        .append(skippedRows == 1 ? "" : "s")
                        .append(" were either duplicates with no changes or invalid.");

            jsonObjectResponse.put("responseMessage",  successMessage.toString());
            jsonObjectResponse.put("insertedCount",    insertedRows);
            jsonObjectResponse.put("updatedCount",     updatedRows);
            jsonObjectResponse.put("skippedCount",     skippedRows);
            jsonObjectResponse.put("updatedAssetIds",  updatedAssetIds);

        } catch (DataAccessException e) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", "A database error occurred while processing your "
                    + source.toLowerCase() + " upload: " + e.getMessage()
                    + ". Please try again or contact support.");
            LOGGER.error("Database error processing {} data", source, e);
        } catch (Exception e) {
            jsonObjectResponse.put("responseCode", "1");
            jsonObjectResponse.put("responseMessage", "An unexpected error occurred while processing your "
                    + source.toLowerCase() + " upload: " + e.getMessage()
                    + ". Please try again or contact support.");
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
            // Use ALLOWED_COLUMNS Set (O(1)) instead of re-creating Arrays.asList each row
            if (!ALLOWED_COLUMNS.contains(normalizedKey)) {
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
            Map<String, Object> existing = jdbcTemplate.queryForMap(SELECT_SQL, assetId);
            if (hasChanges(rowMap, existing)) {
                LOGGER.info("Updating changed {} record for assetId: {}", source, assetId);
                jdbcTemplate.update(UPDATE_SQL, buildUpdateArgs(rowMap, newDate, format));
                updatedAssetIds.add(assetId);
                return "updated";
            } else {
                LOGGER.info("Skipping unchanged {} for assetId: {} - no changes detected", source, assetId);
                return "skipped";
            }
        } catch (EmptyResultDataAccessException e) {
            LOGGER.info("Inserting new {} record for assetId: {}", source, assetId);
            insertBatchArgs.add(buildInsertArgs(rowMap, newDate, format));
            return "inserted";
        }
    }

    private Object[] buildInsertArgs(Map<String, Object> rowMap, Date newDate, SimpleDateFormat format) {
        Object[] args  = new Object[62];
        int      index = 0;

        args[index++] = newDate;
        args[index++] = String.valueOf(rowMap.getOrDefault("book", ""));
        args[index++] = String.valueOf(rowMap.get("assetId"));
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
        args[index++] = newDate;
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("monthlyDepreciationAmt", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("accumulatedDepreciationAmt", "")));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("depreciationDate", "")), format);
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("netCost", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("statusFlag", "New"));
        args[index++] = String.valueOf(rowMap.getOrDefault("changedBy", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("insertedBy", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("financialApproval", ""));
        args[index++] = newDate;
        args[index++] = String.valueOf(rowMap.getOrDefault("nodeType", ""));

        if (index != 62) {
            LOGGER.error("FATAL: INSERT field count mismatch - Expected: 62, Got: {}, AssetId: {}",
                         index, rowMap.get("assetId"));
            throw new IllegalStateException(
                    String.format("INSERT field count mismatch: expected 62, got %d", index));
        }
        return args;
    }

    private Object[] buildUpdateArgs(Map<String, Object> rowMap, Date newDate, SimpleDateFormat format) {
        Object[] args  = new Object[62];
        int      index = 0;

        args[index++] = newDate;
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
        args[index++] = newDate;
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("monthlyDepreciationAmt", "")));
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("accumulatedDepreciationAmt", "")));
        args[index++] = parseDate(String.valueOf(rowMap.getOrDefault("depreciationDate", "")), format);
        args[index++] = parseDouble(String.valueOf(rowMap.getOrDefault("netCost", "")));
        args[index++] = String.valueOf(rowMap.getOrDefault("statusFlag", "New"));
        args[index++] = String.valueOf(rowMap.getOrDefault("changedBy", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("insertedBy", ""));
        args[index++] = String.valueOf(rowMap.getOrDefault("financialApproval", ""));
        args[index++] = newDate;
        args[index++] = String.valueOf(rowMap.getOrDefault("nodeType", ""));
        args[index++] = String.valueOf(rowMap.get("assetId")); // WHERE clause

        if (index != 62) {
            LOGGER.error("FATAL: UPDATE field count mismatch - Expected: 62, Got: {}, AssetId: {}",
                         index, rowMap.get("assetId"));
            throw new IllegalStateException(
                    String.format("UPDATE field count mismatch: expected 62, got %d", index));
        }
        return args;
    }

    private boolean hasChanges(Map<String, Object> newMap, Map<String, Object> existingMap) {
        Set<String> ignoreKeys = new HashSet<>(Arrays.asList(
                "recordNo", "recordDatetime", "createdDate", "updatedDate", "changedDate"));
        Set<String> numericFields = new HashSet<>(Arrays.asList(
                "quantity", "value", "cost", "nbv", "depreciationAmount", "ytdDepreciation",
                "depreciationReserve", "salvageValue", "monthlyDepreciationAmt",
                "accumulatedDepreciationAmt", "netCost", "sequenceNumber", "life",
                "poLineNumber", "uplLine"));

        List<String> changedFields = new ArrayList<>();

        for (String key : newMap.keySet()) {
            String normalizedKey = key.equalsIgnoreCase("life") ? "life" : key;
            String lowerKey      = normalizedKey.toLowerCase();

            if (ignoreKeys.contains(lowerKey) || ignoreKeys.contains(normalizedKey)) continue;

            Object newVal   = newMap.get(key);
            Object existVal = existingMap.get(lowerKey);
            if (existVal == null) existVal = existingMap.get(normalizedKey);
            if (existVal == null) existVal = existingMap.get(key);

            boolean valuesEqual = (numericFields.contains(normalizedKey.toLowerCase())
                                   || numericFields.contains(normalizedKey))
                    ? numericValuesEqual(newVal, existVal)
                    : objectsEqual(newVal, existVal);

            if (!valuesEqual) {
                changedFields.add(String.format("%s: '%s' -> '%s'", normalizedKey,
                        existVal != null ? existVal : "null",
                        newVal   != null ? newVal   : "null"));
            }
        }

        String assetId = String.valueOf(newMap.get("assetId"));
        if (!changedFields.isEmpty()) {
            LOGGER.info("Changes detected for assetId {}: {}", assetId, changedFields);
            return true;
        }
        LOGGER.info("No changes detected for assetId {} - skipping update", assetId);
        return false;
    }

    private boolean numericValuesEqual(Object o1, Object o2) {
        if (o1 == null && o2 == null) return true;
        if (o1 == null || o2 == null) return false;
        String str1 = String.valueOf(o1).trim();
        String str2 = String.valueOf(o2).trim();
        if ((str1.isEmpty() || str1.equals("null")) && (str2.isEmpty() || str2.equals("null"))) return true;
        if ((str1.isEmpty() || str1.equals("null")) || (str2.isEmpty() || str2.equals("null"))) return false;
        try {
            if (str1.contains(".") || str2.contains("."))
                return Double.compare(Double.parseDouble(str1), Double.parseDouble(str2)) == 0;
            return Long.parseLong(str1) == Long.parseLong(str2);
        } catch (NumberFormatException e) {
            return str1.equals(str2);
        }
    }

    private boolean objectsEqual(Object o1, Object o2) {
        if (o1 == null && o2 == null) return true;
        if (o1 == null || o2 == null) return false;
        String str1 = String.valueOf(o1).trim();
        String str2 = String.valueOf(o2).trim();
        if (str1.isEmpty()       && str2.isEmpty())      return true;
        if (str1.equals("null") && str2.isEmpty())       return true;
        if (str1.isEmpty()       && str2.equals("null")) return true;
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

    // ─────────────────────────────────────────────────────────────────────────
    // EXCEL EXPORT
    // ─────────────────────────────────────────────────────────────────────────

    public void exportToExcel(OutputStream outputStream, String column, String value, String operator)
            throws IOException {
        Specification<FarReport> spec  = createSpecification(column, value, operator);
        long                     total = repository.count(spec);
        LOGGER.info("Total records to export: {}", total);

        int batchSize = 50_000;
        int pages     = (int) ((total + batchSize - 1) / batchSize);

        BufferedOutputStream bos = (outputStream instanceof BufferedOutputStream)
                ? (BufferedOutputStream) outputStream
                : new BufferedOutputStream(outputStream, 32 * 1024);

        Workbook  workbook = new Workbook(bos, "Far Reports", "1.0");
        Worksheet sheet    = workbook.newWorksheet("Sheet1");

        int currentRow = 0;
        for (int col = 0; col < EXPECTED_FIELDS.length; col++) {
            sheet.value(currentRow, col, EXPECTED_FIELDS[col]);
        }
        currentRow++;

        for (int page = 0; page < pages; page++) {
            long     startTime   = System.currentTimeMillis();
            Pageable pageable    = PageRequest.of(page, batchSize, Sort.by("recordNo").ascending());
            Page<FarReport> batchPage = repository.findAll(spec, pageable);
            List<FarReport> batchContent = batchPage.getContent();
            LOGGER.info("Fetched batch {} / {} in {} ms ({} records)",
                    page + 1, pages, System.currentTimeMillis() - startTime, batchContent.size());

            for (FarReport entity : batchContent) {
                BeanWrapper beanWrapper = new BeanWrapperImpl(entity);
                for (int col = 0; col < EXPECTED_FIELDS.length; col++) {
                    Object val = beanWrapper.getPropertyValue(EXPECTED_FIELDS[col]);
                    if (val == null)                         sheet.value(currentRow, col, "");
                    else if (val instanceof java.util.Date) sheet.value(currentRow, col, (java.util.Date) val);
                    else if (val instanceof Number)         sheet.value(currentRow, col, ((Number) val).doubleValue());
                    else if (val instanceof Boolean)        sheet.value(currentRow, col, (Boolean) val);
                    else                                    sheet.value(currentRow, col, val.toString());
                }
                currentRow++;
            }
            sheet.flush();
            LOGGER.info("Wrote up to row {} (after batch {})", currentRow, page + 1);
        }

        workbook.finish();
        bos.flush();
        LOGGER.info("Export completed successfully with {} records.", total);
    }

    private Specification<FarReport> createSpecification(String column, String value, String operator) {
        return (root, query, cb) -> {
            if (StringUtils.isEmpty(column) || StringUtils.isEmpty(value))
                return cb.isTrue(cb.literal(true));
            String normalizedColumn = column.equalsIgnoreCase("life") ? "life" : column;
            // Reuse ALLOWED_COLUMNS Set here too for consistency
            if (!ALLOWED_COLUMNS.contains(normalizedColumn))
                throw new IllegalArgumentException("Invalid column name: " + column);
            return switch (operator.toLowerCase()) {
                case "equals" -> cb.equal(root.get(normalizedColumn), value);
                case "like"   -> cb.like(cb.lower(root.get(normalizedColumn)),
                                         "%" + value.toLowerCase() + "%");
                default       -> throw new IllegalArgumentException("Unsupported operator: " + operator);
            };
        };
    }
}