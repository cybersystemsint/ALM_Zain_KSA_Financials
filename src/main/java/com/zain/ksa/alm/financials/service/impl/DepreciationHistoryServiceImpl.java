package com.zain.ksa.alm.financials.service.impl;

// ─── OPTIMIZATION SUMMARY ───────────────────────────────────────────────────
//
//  Problem: findAllWithSummary() fired 3–4 sequential SQL round-trips per call:
//    1. MAX(depreciationPeriod)          — solo query
//    2. COUNT(*) + filtered aggregates   — separate query
//    3. Grand totals (when filters exist) — yet another full JOIN scan
//    4. Paginated rows
//
//  Fix: collapse steps 1–3 into ONE query using conditional aggregation
//  (SQL CASE WHEN), leaving only 2 round-trips regardless of filter state.
//
//  Other improvements:
//    • COUNT(*) → COUNT(dh.recordNo) so the DB can use the PK index
//    • queryForMap + manual Map.get() → queryForObject with a typed RowMapper
//    • Grand-total conditional branch completely eliminated
//    • latestPeriod passed directly to the page query — no repeated MAX() call
//    • WHERE clause built once and reused for both the aggregate and page queries
//    • Params list built once via buildWhereAndParams() helper to avoid drift
//
// ─────────────────────────────────────────────────────────────────────────────

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.response.AssetDepreciationDetailDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;
import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.exception.ResourceNotFoundException;
import com.zain.ksa.alm.financials.repository.DepreciationHistoryRepository;
import com.zain.ksa.alm.financials.service.DepreciationHistoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DepreciationHistoryServiceImpl implements DepreciationHistoryService {

    private static final Logger log = LoggerFactory.getLogger(DepreciationHistoryServiceImpl.class);

    private final DepreciationHistoryRepository repository;
    private final JdbcTemplate                  jdbcTemplate;

    private static final Set<String> ALLOWED_COLUMNS = Set.of(
            "recordNo", "assetId", "depreciationPeriod", "monthlyDepreciationAmt",
            "accumulatedDepreciationAmt", "netCost", "depreciationDate",
            "createdBy", "changedBy", "recordDatetime"
    );

    private static final GenericSpecificationBuilder<DepreciationHistory> SPEC_BUILDER =
            new GenericSpecificationBuilder<>("depreciationDate");

    private static final List<DateTimeFormatter> DATE_PARSE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private static final String FAR_DEDUP_SUBQUERY =
        " LEFT JOIN (" +
        "   SELECT assetId, cost, salvageValue, life," +
        "          datePlacedInService, book, description, serialNumber," +
        "          assetType, category, categoryDescription," +
        "          costAccount, accumulatedDepreAccount, expenseAccount," +
        "          quantity, value, mapped" +
        "   FROM tb_FarReport" +
        "   WHERE recordNo IN (" +
        "     SELECT MAX(recordNo) FROM tb_FarReport GROUP BY assetId" +
        "   )" +
        " ) fr ON dh.assetId = fr.assetId";

    private static final String BASE_FROM =
        " FROM tb_DepreciationHistory dh" + FAR_DEDUP_SUBQUERY;

    // ── Helper: holds a WHERE clause string + its bind parameters ────────────
    private static final class WhereClause {
        final String        sql;
        final List<Object>  params;

        WhereClause(String sql, List<Object> params) {
            this.sql    = sql;
            this.params = params;
        }
    }

    public DepreciationHistoryServiceImpl(DepreciationHistoryRepository repository,
                                          JdbcTemplate jdbcTemplate) {
        this.repository   = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read: paginated composite DTO (basic list, no summary cards)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(
        value = "depreciation:list",
        key = "T(java.util.Objects).hash(#filter.columnName, #filter.searchQuery, " +
              "#filter.dateFrom, #filter.dateTo, #pageable.pageNumber, #pageable.pageSize)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<AssetDepreciationDetailDTO> findAll(DynamicFilterRequest filter,
                                                             Pageable pageable) {
        Specification<DepreciationHistory> spec = SPEC_BUILDER.build(filter);
        Page<DepreciationHistory> page = repository.findAll(spec, pageable);

        if (page.isEmpty()) {
            return PagedResponse.<AssetDepreciationDetailDTO>builder()
                    .content(List.of())
                    .pageNumber(page.getNumber())
                    .pageSize(page.getSize())
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();
        }

        List<String> assetIds = page.getContent().stream()
                .map(DepreciationHistory::getAssetId)
                .collect(Collectors.toList());

        Map<String, FarReport> farMap = repository.findFarReportsByAssetIds(assetIds)
                .stream()
                .collect(Collectors.toMap(FarReport::getAssetId, f -> f, (a, b) -> a));

        List<AssetDepreciationDetailDTO> content = page.getContent().stream()
                .map(d -> mergeToCompositeDTO(d, farMap.get(d.getAssetId())))
                .collect(Collectors.toList());

        return PagedResponse.<AssetDepreciationDetailDTO>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read: paginated list WITH aggregate summary cards — OPTIMISED
    //
    //  Before: 3–4 round-trips (MAX period / COUNT+filtered agg / grand agg / rows)
    //  After : 2 round-trips  (single combined agg / rows)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(
        value = "depreciation:summary",
        key = "T(java.util.Objects).hash(#filter.columnName, #filter.searchQuery, " +
              "#filter.filterBy, #filter.dateFrom, #filter.dateTo, " +
              "#pageable.pageNumber, #pageable.pageSize, #pageable.sort)"
    )
    @Transactional(readOnly = true)
    public Map<String, Object> findAllWithSummary(DynamicFilterRequest filter, Pageable pageable) {

        // ── Step 1: build WHERE (period-only so far — MAX derived inside query) ─
        //    We don't know the latest period yet; the aggregate query will fetch it
        //    alongside the aggregates in a single pass.  See Step 2.
        WhereClause userFilters = buildUserFilters(filter);

        // ── Step 2: ONE query — MAX period + COUNT + filtered totals + grand totals
        //
        //  Key technique: CONDITIONAL AGGREGATION
        //    • grand totals  → SUM(col)  with only the period predicate
        //    • filtered totals → SUM(CASE WHEN <user filters> THEN col END)
        //
        //  This lets a single table scan produce both sets of numbers.
        //  COUNT uses the PK column (cheaper than COUNT(*) on some engines).
        //
        //  Bind parameter order:
        //    [0..n-1]  = user-filter params (repeated in CASE WHEN)
        //    duplicate = user-filter params AGAIN for the COUNT CASE WHEN
        //
        //  We build the params list once and append the duplicated user-filter
        //  block for the CASE WHEN count.

        // Build the CASE WHEN predicate string (same conditions, no period — period
        // is already applied by the outer WHERE via MAX sub-select).
        // When there are no user filters, filtered == grand, so CASE WHEN is "1=1".
        String caseWhen = userFilters.params.isEmpty() ? "1=1" : userFilters.sql
                .replaceFirst("(?i)^\\s*AND\\s*", "");  // strip leading AND

        // Params for outer query:
        //   First copy  → for the CASE WHEN in the SUM expressions (filtered agg)
        //   Second copy → for the CASE WHEN in the COUNT expression
        // Grand totals need no extra params — the outer WHERE handles period scope.
        List<Object> aggParams = new ArrayList<>();
        aggParams.addAll(userFilters.params);  // for SUM CASE WHEN
        aggParams.addAll(userFilters.params);  // for COUNT CASE WHEN

        String aggregateSql =
            "SELECT" +
            "  MAX(dh.depreciationPeriod)                                       AS latestPeriod," +
            // Grand totals — scoped to MAX(period) automatically via WHERE below
            "  COALESCE(SUM(fr.cost),                                        0) AS totalCost,"   +
            "  COALESCE(SUM(dh.accumulatedDepreciationAmt),                  0) AS totalAccumDepr,"  +
            "  COALESCE(SUM(dh.monthlyDepreciationAmt),                      0) AS totalMonthlyDepr," +
            // Filtered totals — same period scope, plus user predicates in CASE WHEN
            "  COALESCE(SUM(CASE WHEN " + caseWhen + " THEN fr.cost                        END), 0) AS filteredCost,"        +
            "  COALESCE(SUM(CASE WHEN " + caseWhen + " THEN dh.accumulatedDepreciationAmt  END), 0) AS filteredAccumDepr,"   +
            "  COALESCE(SUM(CASE WHEN " + caseWhen + " THEN dh.monthlyDepreciationAmt      END), 0) AS filteredMonthlyDepr," +
            // COUNT on PK column — avoids full row materialisation on some engines
            "  COUNT(CASE WHEN " + caseWhen + " THEN dh.recordNo END)                             AS filteredCount"          +
            BASE_FROM +
            // Restrict the entire scan to the latest period via a sub-select;
            // this is index-friendly (depreciationPeriod index) and avoids the
            // separate round-trip that previously called MAX() alone.
            " WHERE dh.depreciationPeriod = (" +
            "   SELECT MAX(depreciationPeriod) FROM tb_DepreciationHistory" +
            " )";

        // ── Typed RowMapper — avoids queryForMap's HashMap allocation + untyped gets ─
        AggregateRow agg = jdbcTemplate.queryForObject(aggregateSql, aggParams.toArray(),
            (rs, n) -> new AggregateRow(
                rs.getString("latestPeriod"),
                rs.getLong("filteredCount"),
                toBigDecimal(rs.getObject("totalCost")),
                toBigDecimal(rs.getObject("totalAccumDepr")),
                toBigDecimal(rs.getObject("totalMonthlyDepr")),
                toBigDecimal(rs.getObject("filteredCost")),
                toBigDecimal(rs.getObject("filteredAccumDepr")),
                toBigDecimal(rs.getObject("filteredMonthlyDepr"))
            ));

        if (agg == null || agg.latestPeriod == null) return buildEmptyResponse(pageable);

        // Derive NBV in Java — guarantees Cost = NBV + AccumDepr ✓
        BigDecimal totalNBV    = agg.totalCost.subtract(agg.totalAccumDepr);
        BigDecimal filteredNBV = agg.filteredCost.subtract(agg.filteredAccumDepr);

        // ── Step 3: paginated rows — latestPeriod already known, no extra query ──
        StringBuilder orderBy = new StringBuilder(" ORDER BY ");
        boolean       sorted  = false;

        if (pageable.getSort().isSorted()) {
            for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
                String col = validateColumn(order.getProperty());
                if (col != null) {
                    if (sorted) orderBy.append(", ");
                    orderBy.append("dh.").append(col)
                           .append(order.isAscending() ? " ASC" : " DESC");
                    sorted = true;
                }
            }
        }
        if (!sorted) orderBy.append("dh.recordNo ASC");

        long offset = (long) pageable.getPageNumber() * pageable.getPageSize();

        // Page WHERE: period is now a literal bind param (latestPeriod already fetched)
        // so the DB can use the idx_dh_period index directly without a sub-select here.
        StringBuilder pageWhere = new StringBuilder(" WHERE dh.depreciationPeriod = ?");
        List<Object>  pageParams = new ArrayList<>();
        pageParams.add(agg.latestPeriod);
        pageParams.addAll(userFilters.params);   // append user filters
        pageWhere.append(userFilters.sql);       // sql already starts with " AND ..."

        String pageSql =
            "SELECT" +
            "  dh.recordNo, dh.assetId, dh.depreciationPeriod,"                +
            "  dh.monthlyDepreciationAmt, dh.accumulatedDepreciationAmt,"       +
            "  dh.netCost, dh.depreciationDate, dh.recordDatetime,"             +
            "  dh.createdBy, dh.changedBy,"                                     +
            "  fr.mapped,"                                                      +
            "  fr.book, fr.description, fr.serialNumber, fr.assetType,"         +
            "  fr.category, fr.categoryDescription, fr.cost AS assetCost,"      +
            "  fr.salvageValue, fr.life, fr.datePlacedInService,"               +
            "  fr.costAccount, fr.accumulatedDepreAccount, fr.expenseAccount,"  +
            "  fr.quantity, fr.value"                                           +
            BASE_FROM + pageWhere + orderBy                                     +
            " LIMIT ? OFFSET ?";

        pageParams.add(pageable.getPageSize());
        pageParams.add(offset);

        List<AssetDepreciationDetailDTO> content = jdbcTemplate.query(
                pageSql, pageParams.toArray(), (rs, n) -> mapRowToDTO(rs));

        // ── Step 4: assemble response ──────────────────────────────────────────
        int totalPages = agg.filteredCount == 0
                ? 0
                : (int) Math.ceil((double) agg.filteredCount / pageable.getPageSize());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data",                        content);
        response.put("totalRecords",                agg.filteredCount);
        response.put("totalPages",                  totalPages);
        response.put("currentPage",                 pageable.getPageNumber());
        response.put("pageSize",                    pageable.getPageSize());
        response.put("latestPeriod",                agg.latestPeriod);
        response.put("totalCost",                   agg.totalCost);
        response.put("totalNBV",                    totalNBV);
        response.put("totalMonthlyDepreciation",    agg.totalMonthlyDepr);
        response.put("totalAccumulatedDepr",        agg.totalAccumDepr);
        response.put("filteredCost",                agg.filteredCost);
        response.put("filteredNBV",                 filteredNBV);
        response.put("filteredMonthlyDepreciation", agg.filteredMonthlyDepr);
        response.put("filteredAccumulatedDepr",     agg.filteredAccumDepr);
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Typed aggregate row — replaces queryForMap's untyped HashMap
    // ─────────────────────────────────────────────────────────────────────────

    private static final class AggregateRow {
        final String     latestPeriod;
        final long       filteredCount;
        final BigDecimal totalCost,      totalAccumDepr,   totalMonthlyDepr;
        final BigDecimal filteredCost,   filteredAccumDepr, filteredMonthlyDepr;

        AggregateRow(String latestPeriod, long filteredCount,
                     BigDecimal totalCost,    BigDecimal totalAccumDepr,    BigDecimal totalMonthlyDepr,
                     BigDecimal filteredCost, BigDecimal filteredAccumDepr, BigDecimal filteredMonthlyDepr) {
            this.latestPeriod       = latestPeriod;
            this.filteredCount      = filteredCount;
            this.totalCost          = totalCost;
            this.totalAccumDepr     = totalAccumDepr;
            this.totalMonthlyDepr   = totalMonthlyDepr;
            this.filteredCost       = filteredCost;
            this.filteredAccumDepr  = filteredAccumDepr;
            this.filteredMonthlyDepr = filteredMonthlyDepr;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build user-filter WHERE fragment (WITHOUT the period predicate)
    // Returns sql like " AND dh.col LIKE ? AND dh.col2 = ?" and matching params.
    // ─────────────────────────────────────────────────────────────────────────

    private WhereClause buildUserFilters(DynamicFilterRequest filter) {
        StringBuilder sql    = new StringBuilder();
        List<Object>  params = new ArrayList<>();

        if (filter.getColumnName() != null && !filter.getColumnName().isBlank()
                && filter.getSearchQuery() != null && !filter.getSearchQuery().isBlank()) {
            String col = validateColumn(filter.getColumnName());
            if (col != null) {
                sql.append(" AND LOWER(dh.").append(col).append(") LIKE LOWER(?)");
                params.add("%" + filter.getSearchQuery() + "%");
            }
        }

        if (filter.getFilterBy() != null) {
            for (Map.Entry<String, String> entry : filter.getFilterBy().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    String col = validateColumn(entry.getKey());
                    if (col != null) {
                        sql.append(" AND dh.").append(col).append(" = ?");
                        params.add(entry.getValue());
                    }
                }
            }
        }

        LocalDateTime dateFrom = parseDate(filter.getDateFrom(), true);
        LocalDateTime dateTo   = parseDate(filter.getDateTo(),   false);
        if (dateFrom != null) {
            sql.append(" AND dh.depreciationDate >= ?");
            params.add(java.sql.Timestamp.valueOf(dateFrom));
        }
        if (dateTo != null) {
            sql.append(" AND dh.depreciationDate <= ?");
            params.add(java.sql.Timestamp.valueOf(dateTo));
        }

        return new WhereClause(sql.toString(), params);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JDBC row → DTO
    // ─────────────────────────────────────────────────────────────────────────

    private AssetDepreciationDetailDTO mapRowToDTO(ResultSet rs) throws SQLException {
        return AssetDepreciationDetailDTO.builder()
            .recordNo(rs.getLong("recordNo"))
            .assetId(rs.getString("assetId"))
            .depreciationPeriod(rs.getString("depreciationPeriod"))
            .monthlyDepreciationAmt(getNullableDouble(rs, "monthlyDepreciationAmt"))
            .accumulatedDepreciationAmt(getNullableDouble(rs, "accumulatedDepreciationAmt"))
            .netCost(getNullableDouble(rs, "netCost"))
            .depreciationDate(rs.getObject("depreciationDate", LocalDateTime.class))
            .recordDatetime(rs.getObject("recordDatetime",     LocalDateTime.class))
            .createdBy(rs.getString("createdBy"))
            .changedBy(rs.getString("changedBy"))
            .mapped(rs.getString("mapped"))
            .book(rs.getString("book"))
            .description(rs.getString("description"))
            .serialNumber(rs.getString("serialNumber"))
            .assetType(rs.getString("assetType"))
            .category(rs.getString("category"))
            .categoryDescription(rs.getString("categoryDescription"))
            .cost(getNullableDouble(rs, "assetCost"))
            .salvageValue(getNullableDouble(rs, "salvageValue"))
            .life(getNullableInteger(rs, "life"))
            .datePlacedInService(rs.getObject("datePlacedInService", LocalDateTime.class))
            .costAccount(rs.getString("costAccount"))
            .accumulatedDepreAccount(rs.getString("accumulatedDepreAccount"))
            .expenseAccount(rs.getString("expenseAccount"))
            .quantity(getNullableInteger(rs, "quantity"))
            .value(getNullableDouble(rs, "value"))
            .build();
    }

    private static Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        Object val = rs.getObject(col);
        if (val == null)              return null;
        if (val instanceof Number)    return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private static Integer getNullableInteger(ResultSet rs, String col) throws SQLException {
        Object val = rs.getObject(col);
        if (val == null)              return null;
        if (val instanceof Number)    return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read: single record by PK
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "depreciation:single", key = "#id")
    @Transactional(readOnly = true)
    public AssetDepreciationDetailDTO findById(Long id) {
        DepreciationHistory depr = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DepreciationHistory", "id", id));
        FarReport far = repository.findFarReportByAssetId(depr.getAssetId()).orElse(null);
        return mergeToCompositeDTO(depr, far);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduler support
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<DepreciationHistory> findByAssetAndPeriod(String assetId,
                                                               String depreciationPeriod) {
        return repository.findByAssetIdAndDepreciationPeriod(assetId, depreciationPeriod);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepreciationHistory> findByAssetIdsAndPeriod(List<String> assetIds,
                                                              String period) {
        if (assetIds == null || assetIds.isEmpty()) return List.of();
        return repository.findByAssetIdsAndPeriod(assetIds, period);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Write
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = {"depreciation:list", "depreciation:single", "depreciation:summary"},
                allEntries = true)
    @Transactional
    public DepreciationHistory save(DepreciationHistory entity) {
        return repository.save(entity);
    }

    @Override
    @CacheEvict(value = {"depreciation:list", "depreciation:single", "depreciation:summary"},
                allEntries = true)
    @Transactional
    public List<DepreciationHistory> saveAll(List<DepreciationHistory> entities) {
        return repository.saveAll(entities);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildEmptyResponse(Pageable pageable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data",                        List.of());
        m.put("totalRecords",                0L);
        m.put("totalPages",                  0);
        m.put("currentPage",                 pageable.getPageNumber());
        m.put("pageSize",                    pageable.getPageSize());
        m.put("latestPeriod",                null);
        m.put("totalCost",                   BigDecimal.ZERO);
        m.put("totalNBV",                    BigDecimal.ZERO);
        m.put("totalMonthlyDepreciation",    BigDecimal.ZERO);
        m.put("totalAccumulatedDepr",        BigDecimal.ZERO);
        m.put("filteredCost",                BigDecimal.ZERO);
        m.put("filteredNBV",                 BigDecimal.ZERO);
        m.put("filteredMonthlyDepreciation", BigDecimal.ZERO);
        m.put("filteredAccumulatedDepr",     BigDecimal.ZERO);
        return m;
    }

    private AssetDepreciationDetailDTO mergeToCompositeDTO(DepreciationHistory depr,
                                                            FarReport far) {
        AssetDepreciationDetailDTO.AssetDepreciationDetailDTOBuilder b =
                AssetDepreciationDetailDTO.builder()
                        .recordNo(depr.getRecordNo())
                        .assetId(depr.getAssetId())
                        .depreciationPeriod(depr.getDepreciationPeriod())
                        .monthlyDepreciationAmt(depr.getMonthlyDepreciationAmt())
                        .accumulatedDepreciationAmt(depr.getAccumulatedDepreciationAmt())
                        .netCost(depr.getNetCost())
                        .depreciationDate(depr.getDepreciationDate())
                        .recordDatetime(depr.getRecordDatetime())
                        .createdBy(depr.getCreatedBy())
                        .changedBy(depr.getChangedBy());

        if (far != null) {
            b.mapped(far.getMapped())
             .book(far.getBook())
             .description(far.getDescription())
             .serialNumber(far.getSerialNumber())
             .assetType(far.getAssetType())
             .category(far.getCategory())
             .categoryDescription(far.getCategoryDescription())
             .cost(far.getCost())
             .salvageValue(far.getSalvageValue())
             .life(far.getLife())
             .datePlacedInService(far.getDatePlacedInService() != null
                     ? far.getDatePlacedInService().toInstant()
                             .atZone(ZoneId.systemDefault()).toLocalDateTime()
                     : null)
             .costAccount(far.getCostAccount())
             .accumulatedDepreAccount(far.getAccumulatedDepreAccount())
             .expenseAccount(far.getExpenseAccount())
             .quantity(far.getQuantity())
             .value(far.getValue());
        }

        return b.build();
    }

    private String validateColumn(String input) {
        if (input == null) return null;
        String normalized = input.trim();
        for (String allowed : ALLOWED_COLUMNS) {
            if (allowed.equalsIgnoreCase(normalized)) return allowed;
        }
        log.warn("[DepreciationHistory] Invalid column requested: {}", input);
        return null;
    }

    private LocalDateTime parseDate(String dateStr, boolean startOfDay) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String trimmed = dateStr.trim();
        for (DateTimeFormatter fmt : DATE_PARSE_FORMATS) {
            try { return LocalDateTime.parse(trimmed, fmt); }
            catch (DateTimeParseException ignored) {}
            try {
                LocalDate d = LocalDate.parse(trimmed, fmt);
                return startOfDay ? d.atStartOfDay() : d.atTime(23, 59, 59);
            }
            catch (DateTimeParseException ignored) {}
        }
        log.warn("[DepreciationHistory] Could not parse date: {}", dateStr);
        return null;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null)               return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number)     return BigDecimal.valueOf(((Number) value).doubleValue());
        try   { return new BigDecimal(value.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}