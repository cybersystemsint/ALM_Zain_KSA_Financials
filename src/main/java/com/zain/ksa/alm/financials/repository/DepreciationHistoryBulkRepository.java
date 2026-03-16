package com.zain.ksa.alm.financials.repository;

import com.zain.ksa.alm.financials.entity.DepreciationHistory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * Native JDBC bulk upsert for DepreciationHistory.
 *
 * <p>Replaces JPA {@code saveAll()} which issues one SELECT + one INSERT/UPDATE
 * per row. This class sends the entire batch in a single prepared-statement
 * execution using {@code INSERT ... ON DUPLICATE KEY UPDATE} (MySQL/MariaDB).
 *
 * <p><b>Pre-condition:</b> The unique constraint
 * {@code uk_dh_asset_period (assetId, depreciationPeriod)} must exist.
 * It is already declared on {@link com.zain.ksa.alm.financials.entity.DepreciationHistory}.
 *
 * <p><b>PostgreSQL alternative:</b> Replace the ON DUPLICATE KEY UPDATE clause with:
 * <pre>{@code
 *   ON CONFLICT (assetId, depreciationPeriod) DO UPDATE SET
 *       monthlyDepreciationAmt     = EXCLUDED.monthlyDepreciationAmt,
 *       accumulatedDepreciationAmt = EXCLUDED.accumulatedDepreciationAmt,
 *       netCost                    = EXCLUDED.netCost,
 *       depreciationDate           = EXCLUDED.depreciationDate,
 *       recordDatetime             = EXCLUDED.recordDatetime,
 *       changedBy                  = EXCLUDED.changedBy
 * }</pre>
 */
@Repository
public class DepreciationHistoryBulkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DepreciationHistoryBulkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Upsert a batch of {@link DepreciationHistory} records in a single DB round-trip.
     *
     * <ul>
     *   <li>INSERT on first run of the month for a given asset.</li>
     *   <li>UPDATE (overwrite computed columns only) on re-runs.</li>
     *   <li>{@code createdBy} is intentionally excluded from the UPDATE clause
     *       to preserve the original audit trail.</li>
     * </ul>
     *
     * @param records non-null list; no-op if empty
     */
    public void upsertAll(List<DepreciationHistory> records) {
        if (records == null || records.isEmpty()) return;

        // NOTE: column names must match @Column(name=...) values in the entity.
        // DepreciationHistory uses the field names directly (no explicit name override),
        // so Hibernate defaults to the field name — confirmed against entity definition.
        String sql = """
                INSERT INTO tb_DepreciationHistory (
                    assetId,
                    depreciationPeriod,
                    monthlyDepreciationAmt,
                    accumulatedDepreciationAmt,
                    netCost,
                    depreciationDate,
                    recordDatetime,
                    createdBy,
                    changedBy
                ) VALUES (
                    :assetId,
                    :period,
                    :monthlyAmt,
                    :accumulatedAmt,
                    :netCost,
                    :depreciationDate,
                    :recordDatetime,
                    :createdBy,
                    :changedBy
                )
                ON DUPLICATE KEY UPDATE
                    monthlyDepreciationAmt     = VALUES(monthlyDepreciationAmt),
                    accumulatedDepreciationAmt = VALUES(accumulatedDepreciationAmt),
                    netCost                    = VALUES(netCost),
                    depreciationDate           = VALUES(depreciationDate),
                    recordDatetime             = VALUES(recordDatetime),
                    changedBy                  = VALUES(changedBy)
                """;

        MapSqlParameterSource[] batchParams = records.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("assetId",          r.getAssetId())
                        .addValue("period",           r.getDepreciationPeriod())
                        .addValue("monthlyAmt",       r.getMonthlyDepreciationAmt())
                        .addValue("accumulatedAmt",   r.getAccumulatedDepreciationAmt())
                        .addValue("netCost",          r.getNetCost())
                        .addValue("depreciationDate", toTimestamp(r.getDepreciationDate()))
                        .addValue("recordDatetime",   toTimestamp(r.getRecordDatetime()))
                        .addValue("createdBy",        r.getCreatedBy())
                        .addValue("changedBy",        r.getChangedBy()))
                .toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate(sql, batchParams);
    }

    private static Timestamp toTimestamp(java.time.LocalDateTime ldt) {
        return ldt != null ? Timestamp.valueOf(ldt) : null;
    }
}