package com.zain.ksa.alm.financials.service.impl;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a JPA Specification dynamically from a DynamicFilterRequest.
 *
 * Works with ANY entity type T — just call GenericSpecificationBuilder.build(filter).
 *
 * Supports:
 *  - Single column LIKE search  (columnName + searchQuery)
 *  - Multi-column exact filters (filterBy map)
 *  - Date range on a named datetime field
 *  - isMapped / siteId convenience shortcuts
 *
 * Unknown field names are silently ignored so stale query params don't crash.
 */
public class GenericSpecificationBuilder<T> {

    private final String dateFieldName;  

    public GenericSpecificationBuilder(String dateFieldName) {
        this.dateFieldName = dateFieldName;
    }

    /** Build a Specification from the filter. Returns null (= no restriction) if filter is empty. */
    public Specification<T> build(DynamicFilterRequest filter) {
        if (filter == null) return null;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── 1. Single column LIKE search ─────────────────────────────────
            if (notBlank(filter.getColumnName()) && notBlank(filter.getSearchQuery())) {
                try {
                    // Try to access the field — silently skip if it doesn't exist on entity
                    root.get(filter.getColumnName());
                    predicates.add(cb.like(
                        cb.lower(root.get(filter.getColumnName()).as(String.class)),
                        "%" + filter.getSearchQuery().toLowerCase() + "%"
                    ));
                } catch (IllegalArgumentException ignored) {
                    // Field doesn't exist on this entity — skip
                }
            }

            // ── 2. Multi-column exact filter ──────────────────────────────────
            if (filter.getFilterBy() != null && !filter.getFilterBy().isEmpty()) {
                for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                    String fieldName = entry.getKey();
                    String rawValue  = entry.getValue();
                    if (rawValue == null || rawValue.isBlank()) continue;

                    try {
                        Class<?> fieldType = root.get(fieldName).getJavaType();

                        if (Boolean.class.equals(fieldType) || boolean.class.equals(fieldType)) {
                            predicates.add(cb.equal(root.get(fieldName), Boolean.parseBoolean(rawValue)));
                        } else if (Integer.class.equals(fieldType) || int.class.equals(fieldType)) {
                            predicates.add(cb.equal(root.get(fieldName), Integer.parseInt(rawValue)));
                        } else if (Long.class.equals(fieldType) || long.class.equals(fieldType)) {
                            predicates.add(cb.equal(root.get(fieldName), Long.parseLong(rawValue)));
                        } else if (Double.class.equals(fieldType) || double.class.equals(fieldType)) {
                            predicates.add(cb.equal(root.get(fieldName), Double.parseDouble(rawValue)));
                        } else {
                            // String — use exact match for filterBy, LIKE for searchQuery
                            predicates.add(cb.equal(root.get(fieldName), rawValue));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Field doesn't exist on this entity — skip gracefully
                    }
                }
            }

            // ── 3. Date range ─────────────────────────────────────────────────
            if (dateFieldName != null) {
                try {
                    if (filter.getDateFrom() != null) {
                        predicates.add(cb.greaterThanOrEqualTo(
                            root.get(dateFieldName).as(LocalDateTime.class),
                            filter.getDateFrom()
                        ));
                    }
                    if (filter.getDateTo() != null) {
                        predicates.add(cb.lessThanOrEqualTo(
                            root.get(dateFieldName).as(LocalDateTime.class),
                            filter.getDateTo()
                        ));
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            // ── 4. Convenience shortcuts ──────────────────────────────────────
            if (filter.getIsMapped() != null) {
                try {
                    root.get("isMapped");
                    predicates.add(cb.equal(root.get("isMapped"), filter.getIsMapped()));
                } catch (IllegalArgumentException ignored) {}
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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}