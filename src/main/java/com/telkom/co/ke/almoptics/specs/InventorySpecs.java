package com.telkom.co.ke.almoptics.specs;

import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.*;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;

public class InventorySpecs {

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    public static <T> Specification<T> filterBy(
            String searchColumn,
            String searchQuery,
            Map<String, Object> filterBy
    ) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            // --- ENHANCED: searchColumn/searchQuery supports date-range "2026-01-01~2026-01-24"
            if (searchColumn != null && !searchColumn.isEmpty()
                    && searchQuery != null && !searchQuery.isEmpty()) {

                if (searchQuery.matches("\\d{4}-\\d{2}-\\d{2}~\\d{4}-\\d{2}-\\d{2}")) {
                    try {
                        String[] dates = searchQuery.split("~");
                        Date from = new SimpleDateFormat(DATE_PATTERN).parse(dates[0]);
                        Date to = new SimpleDateFormat(DATE_PATTERN).parse(dates[1]);
                        Date toEnd = new Date(to.getTime() + (24 * 60 * 60 * 1000) - 1);
                        Path<Date> path = root.get(searchColumn);
                        predicate = cb.and(predicate,
                                cb.greaterThanOrEqualTo(path, from),
                                cb.lessThanOrEqualTo(path, toEnd)
                        );
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Invalid date range in searchQuery", e);
                    }
                } else {
                    predicate = cb.and(
                            predicate,
                            cb.like(
                                    cb.lower(root.get(searchColumn).as(String.class)),
                                    "%" + searchQuery.toLowerCase() + "%"
                            )
                    );
                }
            }

            if (filterBy != null && !filterBy.isEmpty()) {
                for (Map.Entry<String, Object> entry : filterBy.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value == null || value.toString().isEmpty()) continue;

                    // --- 1. Accept both fieldFrom/fieldTo AND dateStringRange
                    // fieldFrom / fieldTo logic
                    if (key.endsWith("From") || key.endsWith("To")) {
                        String base = key.replaceAll("(From|To)$", "");
                        Path<Date> path = root.get(base);
                        try {
                            Date param = new SimpleDateFormat(DATE_PATTERN).parse(value.toString());
                            if (key.endsWith("From")) {
                                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(path, param));
                            } else {
                                Date paramTo = new Date(param.getTime() + (24 * 60 * 60 * 1000) - 1);
                                predicate = cb.and(predicate, cb.lessThanOrEqualTo(path, paramTo));
                            }
                        } catch (Exception e) {
                            throw new IllegalArgumentException("Invalid date for " + key, e);
                        }
                        continue;
                    }

                    // --- 2. NEW: plain field = "2026-01-01~2026-01-24" means a date-range
                    if (value instanceof String && ((String) value).matches("\\d{4}-\\d{2}-\\d{2}~\\d{4}-\\d{2}-\\d{2}")) {
                        String[] dates = ((String) value).split("~");
                        try {
                            Date from = new SimpleDateFormat(DATE_PATTERN).parse(dates[0]);
                            Date to = new SimpleDateFormat(DATE_PATTERN).parse(dates[1]);
                            Date toEnd = new Date(to.getTime() + (24 * 60 * 60 * 1000) - 1);
                            Path<Date> path = root.get(key);
                            predicate = cb.and(predicate, 
                                    cb.greaterThanOrEqualTo(path, from),
                                    cb.lessThanOrEqualTo(path, toEnd)
                            );
                        } catch (Exception e) {
                            throw new IllegalArgumentException("Invalid date range for " + key, e);
                        }
                        continue;
                    }

                    // --- 3. Normal equals filter for other keys
                    Path<?> path = root.get(key);
                    Class<?> attrType = path.getJavaType();
                    Object param = value;
                    try {
                        if (attrType == Date.class && value instanceof String) {
                            param = new SimpleDateFormat(DATE_PATTERN).parse(value.toString());
                        }
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Invalid filter for " + key, e);
                    }
                    predicate = cb.and(predicate, cb.equal(path, param));
                }
            }
            return predicate;
        };
    }
}