package com.zain.ksa.alm.financials.service.impl;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a JPA Specification dynamically from a DynamicFilterRequest.
 *
 * Works with ANY entity type T — just call GenericSpecificationBuilder.build(filter).
 *
 * Supports:
 *  - Single column LIKE search  (columnName + searchQuery)
 *  - Multi-column exact filters (filterBy map) with full type coercion
 *  - Date range on a named datetime field (LocalDate OR LocalDateTime)
 *  - isMapped / siteId convenience shortcuts
 *
 * Unknown field names are collected into a validation error list.
 */
public class GenericSpecificationBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(GenericSpecificationBuilder.class);

    private final String dateFieldName;

    /**
     * Fields that are computed/virtual (not actual DB columns).
     * These are silently skipped when used in filters — they exist in the DTO
     * (e.g. sequenceNo is assigned after the query) but have no DB column.
     */
    private static final Set<String> VIRTUAL_FIELDS = Set.of("sequenceNo");

    public GenericSpecificationBuilder(String dateFieldName) {
        this.dateFieldName = dateFieldName;
    }

    /** Build a Specification from the filter. Returns null (= no restriction) if filter is empty. */
    public Specification<T> build(DynamicFilterRequest filter) {
        if (filter == null) return null;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            // ── 1. Single column LIKE search ─────────────────────────────────
            if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
                if (VIRTUAL_FIELDS.contains(filter.getColumnName())) {
                    log.debug("Skipping search on computed field '{}'", filter.getColumnName());
                } else {
                    try {
                        Path<?> path = root.get(filter.getColumnName());
                        predicates.add(cb.like(
                            cb.lower(path.as(String.class)),
                            "%" + filter.getSearchQuery().toLowerCase() + "%"
                        ));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                            "Unknown search column: '" + filter.getColumnName() + "'");
                    }
                }
            }

            // ── 2. Multi-column exact filter ──────────────────────────────────
            if (filter.getFilterBy() != null && !filter.getFilterBy().isEmpty()) {
                for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                    String fieldName = entry.getKey();
                    String rawValue  = entry.getValue();
                    if (rawValue == null || rawValue.isBlank()) continue;
                    if (VIRTUAL_FIELDS.contains(fieldName)) {
                        log.debug("Skipping filterBy on computed field '{}'", fieldName);
                        continue;
                    }

                    try {
                        Path<?> path = root.get(fieldName);
                        Class<?> fieldType = path.getJavaType();

                        Predicate p = buildTypedPredicate(root, cb, fieldName, rawValue, fieldType);
                        if (p != null) {
                            predicates.add(p);
                        }
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                            "Unknown filter field: '" + fieldName + "'");
                    }
                }
            }

            // ── 3. Date range ─────────────────────────────────────────────────
            if (dateFieldName != null) {
                try {
                    Class<?> fieldType = root.get(dateFieldName).getJavaType();

                    if (notBlank(filter.getDateFrom())) {
                        if (LocalDate.class.equals(fieldType)) {
                            LocalDate from = parseDateOnly(filter.getDateFrom());
                            predicates.add(cb.greaterThanOrEqualTo(
                                root.get(dateFieldName), from));
                        } else if (LocalDateTime.class.equals(fieldType)) {
                            LocalDateTime from = parseDateTime(filter.getDateFrom(), true);
                            predicates.add(cb.greaterThanOrEqualTo(
                                root.get(dateFieldName), from));
                        } else if (Date.class.isAssignableFrom(fieldType)) {
                            // java.util.Date / java.sql.Timestamp (FarReport, etc.)
                            Date from = toUtilDate(parseDateTime(filter.getDateFrom(), true));
                            predicates.add(cb.greaterThanOrEqualTo(
                                root.get(dateFieldName), from));
                        }
                    }
                    if (notBlank(filter.getDateTo())) {
                        if (LocalDate.class.equals(fieldType)) {
                            LocalDate to = parseDateOnly(filter.getDateTo());
                            predicates.add(cb.lessThanOrEqualTo(
                                root.get(dateFieldName), to));
                        } else if (LocalDateTime.class.equals(fieldType)) {
                            LocalDateTime to = parseDateTime(filter.getDateTo(), false);
                            predicates.add(cb.lessThanOrEqualTo(
                                root.get(dateFieldName), to));
                        } else if (Date.class.isAssignableFrom(fieldType)) {
                            Date to = toUtilDate(parseDateTime(filter.getDateTo(), false));
                            predicates.add(cb.lessThanOrEqualTo(
                                root.get(dateFieldName), to));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Unable to locate")) {
                        log.warn("Date field '{}' not found on entity, skipping date range filter", dateFieldName);
                    } else {
                        throw e;
                    }
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(
                        "Invalid date format for date range. Use 'yyyy-MM-dd' or 'yyyy-MM-ddTHH:mm:ss'. Got: " + e.getParsedString());
                }
            }

            // ── 4. Convenience shortcuts ──────────────────────────────────────
            if (filter.getIsMapped() != null) {
                for (String fieldName : new String[]{"isMapped", "mapped"}) {
                    try {
                        Class<?> mappedType = root.get(fieldName).getJavaType();
                        if (String.class.equals(mappedType)) {
                            // DepreciationHistory: mapped is String ("true"/"false" or "Y"/"N")
                            predicates.add(cb.equal(root.get(fieldName),
                                filter.getIsMapped().toString()));
                        } else {
                            // Boolean field
                            predicates.add(cb.equal(root.get(fieldName), filter.getIsMapped()));
                        }
                        break;
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (notBlank(filter.getSiteId())) {
                try {
                    Class<?> siteType = root.get("siteId").getJavaType();
                    if (Integer.class.equals(siteType) || int.class.equals(siteType)) {
                        predicates.add(cb.equal(root.get("siteId"),
                                Integer.parseInt(filter.getSiteId())));
                    } else {
                        predicates.add(cb.equal(root.get("siteId"), filter.getSiteId()));
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Builds a typed predicate for filterBy values, coercing the raw string
     * to the correct Java type for the entity field.
     */
    @SuppressWarnings("unchecked")
    private Predicate buildTypedPredicate(
            javax.persistence.criteria.Root<T> root,
            javax.persistence.criteria.CriteriaBuilder cb,
            String fieldName, String rawValue, Class<?> fieldType) {

        try {
            if (Boolean.class.equals(fieldType) || boolean.class.equals(fieldType)) {
                return cb.equal(root.get(fieldName), Boolean.parseBoolean(rawValue));
            }
            if (Integer.class.equals(fieldType) || int.class.equals(fieldType)) {
                return cb.equal(root.get(fieldName), Integer.parseInt(rawValue));
            }
            if (Long.class.equals(fieldType) || long.class.equals(fieldType)) {
                return cb.equal(root.get(fieldName), Long.parseLong(rawValue));
            }
            if (Double.class.equals(fieldType) || double.class.equals(fieldType)) {
                return cb.equal(root.get(fieldName), Double.parseDouble(rawValue));
            }
            if (BigDecimal.class.equals(fieldType)) {
                return cb.equal(root.get(fieldName), new BigDecimal(rawValue));
            }
            if (LocalDate.class.equals(fieldType)) {
                return cb.equal(root.get(fieldName), LocalDate.parse(rawValue));
            }
            if (LocalDateTime.class.equals(fieldType)) {
                // Support both "2026-03-02" and "2026-03-02T10:00:00"
                LocalDateTime dt = parseDateTime(rawValue, true);
                // For exact match on a datetime, match the entire day
                LocalDateTime dayStart = dt.toLocalDate().atStartOfDay();
                LocalDateTime dayEnd   = dt.toLocalDate().atTime(23, 59, 59);
                return cb.between(root.get(fieldName), dayStart, dayEnd);
            }
            if (Date.class.isAssignableFrom(fieldType)) {
                // java.util.Date / java.sql.Timestamp (FarReport, etc.)
                LocalDateTime dt = parseDateTime(rawValue, true);
                Date dayStart = toUtilDate(dt.toLocalDate().atStartOfDay());
                Date dayEnd   = toUtilDate(dt.toLocalDate().atTime(23, 59, 59));
                return cb.between(root.get(fieldName), dayStart, dayEnd);
            }
            // Default: String exact match
            return cb.equal(root.get(fieldName), rawValue);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid value for field '" + fieldName + "': expected " +
                fieldType.getSimpleName() + " but got '" + rawValue + "'");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Invalid date value for field '" + fieldName + "': use 'yyyy-MM-dd' or 'yyyy-MM-ddTHH:mm:ss'. Got: '" + rawValue + "'");
        }
    }

    // ── Date parsing helpers ─────────────────────────────────────────────────

    /**
     * Parse a date string to LocalDateTime.
     * Accepts "yyyy-MM-dd" or "yyyy-MM-ddTHH:mm:ss".
     */
    private LocalDateTime parseDateTime(String dateStr, boolean startOfDay) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(dateStr);
        } catch (DateTimeParseException e) {
            LocalDate date = LocalDate.parse(dateStr);
            return startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59);
        }
    }

    /**
     * Parse to LocalDate only (for entities that use LocalDate fields).
     */
    private LocalDate parseDateOnly(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            // Maybe they sent a full datetime — extract the date part
            return LocalDateTime.parse(dateStr).toLocalDate();
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Convert LocalDateTime to java.util.Date for entities using legacy Date fields.
     */
    private static Date toUtilDate(LocalDateTime ldt) {
        if (ldt == null) return null;
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}