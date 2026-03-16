package com.zain.ksa.alm.financials.service.validation;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarReportValidation {

    private static final Logger log = LoggerFactory.getLogger(FarReportValidation.class);

    private static final Set<String> REQUIRED_FIELDS = new LinkedHashSet<>(Arrays.asList(
        "assetId", "assetType", "book", "category", "categoryDescription",
        "cost", "costAccount", "creationDate", "datePlacedInService",
        "depreciateFlag", "depreciationAmount", "depreciationReserve",
        "description", "expenseAccount", "expenseCostCenter",
        "invoiceNumber", "life", "locationSegment1", "locationSegment2",
        "locationSegment3", "locationSegment4", "locations", "mapped",
        "nbv", "partNumber", "quantity", "salvageValue",
        "vendorName", "vendorNumber"
    ));

    private static final Map<String, String> ALIAS_MAP = Map.ofEntries(
        Map.entry("ASSET_ID",               "assetId"),
        Map.entry("ASSET_TYPE",             "assetType"),
        Map.entry("BOOK",                   "book"),
        Map.entry("CATEGORY",               "category"),
        Map.entry("CATEGORY_DESCRIPTION",   "categoryDescription"),
        Map.entry("COST",                   "cost"),
        Map.entry("COST_ACCOUNT",           "costAccount"),
        Map.entry("CREATION_DATE",          "creationDate"),
        Map.entry("DATE_PLACED_IN_SERVICE", "datePlacedInService"),
        Map.entry("DEPRECIATE_FLAG",        "depreciateFlag"),
        Map.entry("DEPRN_AMOUNT",           "depreciationAmount"),
        Map.entry("DEPRN_RESERVE",          "depreciationReserve"),
        Map.entry("DESCRIPTION",            "description"),
        Map.entry("EXPENSE_ACCOUNT",        "expenseAccount"),
        Map.entry("EXPENSE_COST_CENTER",    "expenseCostCenter"),
        Map.entry("INVOICE_NUMBER",         "invoiceNumber"),
        Map.entry("LIFE",                   "life"),
        Map.entry("LOCATION_SEGMENT1",      "locationSegment1"),
        Map.entry("LOCATION_SEGMENT2",      "locationSegment2"),
        Map.entry("LOCATION_SEGMENT3",      "locationSegment3"),
        Map.entry("LOCATION_SEGMENT4",      "locationSegment4"),
        Map.entry("LOCATIONS",              "locations"),
        Map.entry("MAPPED",                 "mapped"),
        Map.entry("NBV",                    "nbv"),
        Map.entry("PART_NUMBER",            "partNumber"),
        Map.entry("QUANTITY",               "quantity"),
        Map.entry("SALVAGE_VALUE",          "salvageValue"),
        Map.entry("VENDOR_NAME",            "vendorName"),
        Map.entry("VENDOR_NUMBER",          "vendorNumber")
    );

    private static final double MONETARY_TOLERANCE = 0.001;

    // ── Public API ────────────────────────────────────────────────────────────

    public static class ValidationResult {
        public final boolean      valid;
        public final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid  = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        public boolean hasErrors() { return !errors.isEmpty(); }

        @Override
        public String toString() {
            return hasErrors() ? "ERRORS: " + String.join("; ", errors) : "OK";
        }
    }

    public static ValidationResult validateRow(Map<String, Object> row, int rowIndex) {
        List<String> errors = new ArrayList<>();
        int rowNum = rowIndex + 1;

        if (row == null || row.isEmpty()) {
            errors.add("Row " + rowNum + ": row is empty or null");
            return new ValidationResult(false, errors);
        }

        Map<String, Object> normalized = normalizeKeys(row);

        // 1. Missing required fields — grouped into one error message
        List<String> missing = new ArrayList<>();
        for (String field : REQUIRED_FIELDS) {
            Object value = normalized.get(field);
            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            errors.add("Row " + rowNum + ": required fields missing — " + String.join(", ", missing));
        }

        // 2. Financial checks
        checkFinancialFields(normalized, rowNum, errors);



        // 4. Numeric range checks
        checkNumericFields(normalized, rowNum, errors);

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public static Set<String>         getRequiredFields() { return new LinkedHashSet<>(REQUIRED_FIELDS); }
    public static Map<String, String> getAliasMap()       { return new HashMap<>(ALIAS_MAP); }

    // ── Private validators ────────────────────────────────────────────────────

    private static void checkFinancialFields(Map<String, Object> row, int rowNum,
                                             List<String> errors) {
        try {
            Double  cost     = toDouble(row.get("cost"));
            Double  salvage  = toDouble(row.get("salvageValue"));
            Integer life     = toInteger(row.get("life"));
            Double  nbv      = toDouble(row.get("nbv"));
            Double  deprnRsv = toDouble(row.get("depreciationReserve"));

            if (cost != null && cost <= 0)
                errors.add("Row " + rowNum + ": 'cost' must be > 0");

            if (life != null && life <= 0)
                errors.add("Row " + rowNum + ": 'life' must be > 0 months");

            if (salvage != null && cost != null && salvage > cost + MONETARY_TOLERANCE)
                errors.add("Row " + rowNum + ": 'salvageValue' (" + salvage + ") cannot exceed 'cost' (" + cost + ")");

            if (nbv != null && nbv < 0)
                errors.add("Row " + rowNum + ": 'nbv' cannot be negative");

            if (deprnRsv != null && cost != null && salvage != null) {
                double maxAD = cost - salvage;
                if (deprnRsv > maxAD + MONETARY_TOLERANCE)
                    errors.add("Row " + rowNum + ": 'depreciationReserve' (" + String.format("%.3f", deprnRsv)
                            + ") exceeds max allowed (" + String.format("%.3f", maxAD) + ")");
            }

        } catch (Exception ex) {
            log.debug("checkFinancialFields row {}: {}", rowNum, ex.getMessage());
        }
    }



    private static void checkNumericFields(Map<String, Object> row, int rowNum, List<String> errors) {
        try {
            Integer qty = toInteger(row.get("quantity"));
            if (qty != null && qty < 0)
                errors.add("Row " + rowNum + ": 'quantity' cannot be negative");

            Double deprnAmt = toDouble(row.get("depreciationAmount"));
            if (deprnAmt != null && deprnAmt < 0)
                errors.add("Row " + rowNum + ": 'depreciationAmount' cannot be negative");

            Double deprnRsv = toDouble(row.get("depreciationReserve"));
            if (deprnRsv != null && deprnRsv < 0)
                errors.add("Row " + rowNum + ": 'depreciationReserve' cannot be negative");

        } catch (Exception ex) {
            log.debug("checkNumericFields row {}: {}", rowNum, ex.getMessage());
        }
    }

    // ── Key normalisation ─────────────────────────────────────────────────────

    private static Map<String, Object> normalizeKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            String normalizedKey;
            if (ALIAS_MAP.containsKey(key.toUpperCase())) {
                normalizedKey = ALIAS_MAP.get(key.toUpperCase());
            } else if (ALIAS_MAP.containsValue(key)) {
                normalizedKey = key;
            } else {
                normalizedKey = key.toLowerCase();
            }
            normalized.put(normalizedKey, entry.getValue());
        }
        return normalized;
    }

    // ── Type helpers ──────────────────────────────────────────────────────────

    private static Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Double) return (Double) obj;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try { return Double.parseDouble(obj.toString().trim()); } catch (Exception e) { return null; }
    }

    private static Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number)  return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString().trim()); } catch (Exception e) { return null; }
    }

    private static long toTime(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof java.util.Date)          return ((java.util.Date) obj).getTime();
        if (obj instanceof java.time.LocalDate)     return java.sql.Date.valueOf((java.time.LocalDate) obj).getTime();
        if (obj instanceof java.time.LocalDateTime) return java.sql.Timestamp.valueOf((java.time.LocalDateTime) obj).getTime();
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(obj.toString().trim()).getTime();
        } catch (Exception e) { return 0; }
    }
}