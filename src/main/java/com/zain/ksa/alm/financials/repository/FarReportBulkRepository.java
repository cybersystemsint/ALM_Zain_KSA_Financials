package com.zain.ksa.alm.financials.repository;

import com.zain.ksa.alm.financials.entity.FarReport;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * Native JDBC bulk UPDATE for the depreciation columns on {@link FarReport}.
 *
 * <p>Replaces JPA {@code saveAll()} which:
 * <ol>
 *   <li>Issues a SELECT per entity to check managed state.</li>
 *   <li>Dirty-checks ALL ~60 columns and generates a full UPDATE per row.</li>
 * </ol>
 *
 * <p>This class generates a single prepared-statement batch that touches only
 * the 8 depreciation columns, avoiding the full-row update overhead.
 *
 * <p><b>Column mapping</b> is based on {@code @Column(name=...)} in
 * {@link com.zain.ksa.alm.financials.entity.FarReport}. All column names
 * are declared explicitly in the entity and are used verbatim here.
 */
@Repository
public class FarReportBulkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FarReportBulkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Bulk-update the 8 depreciation-related columns on {@code tb_FarReport}
     * for all assets in the list, using a single consistent {@code runDate}
     * timestamp for the entire batch.
     *
     * <p>Columns updated:
     * <ul>
     *   <li>{@code cost}                       — IC rounded to 3dp</li>
     *   <li>{@code salvageValue}               — SV rounded to 3dp</li>
     *   <li>{@code depreciationAmount}         — MD (monthly)</li>
     *   <li>{@code monthlyDepreciationAmt}     — MD (export column alias)</li>
     *   <li>{@code accumulatedDepreciationAmt} — AD (cumulative)</li>
     *   <li>{@code netCost}                    — NC (NBV)</li>
     *   <li>{@code nbv}                        — mirrors netCost</li>
     *   <li>{@code depreciationDate}           — run timestamp</li>
     * </ul>
     *
     * @param assets  list of FarReport entities already mutated by
     *                {@code applyDepreciationToFarReport()}; no-op if empty
     * @param runDate single consistent timestamp for the entire scheduler run
     */
    public void bulkUpdateDepreciation(List<FarReport> assets, Date runDate) {
        if (assets == null || assets.isEmpty()) return;

        Timestamp ts = new Timestamp(runDate.getTime());

        String sql = """
                UPDATE tb_FarReport SET
                    cost                         = :cost,
                    salvageValue                 = :salvageValue,
                    depreciationAmount           = :depreciationAmount,
                    monthlyDepreciationAmt       = :monthlyDepreciationAmt,
                    accumulatedDepreciationAmt   = :accumulatedDepreciationAmt,
                    netCost                      = :netCost,
                    nbv                          = :nbv,
                    depreciationDate             = :depreciationDate
                WHERE assetId = :assetId
                """;

        MapSqlParameterSource[] batchParams = assets.stream()
                .map(a -> new MapSqlParameterSource()
                        .addValue("assetId",                    a.getAssetId())
                        .addValue("cost",                       a.getCost())
                        .addValue("salvageValue",               a.getSalvageValue())
                        .addValue("depreciationAmount",         a.getDepreciationAmount())
                        .addValue("monthlyDepreciationAmt",     a.getMonthlyDepreciationAmt())
                        .addValue("accumulatedDepreciationAmt", a.getAccumulatedDepreciationAmt())
                        .addValue("netCost",                    a.getNetCost())
                        .addValue("nbv",                        a.getNbv())
                        .addValue("depreciationDate",           ts))
                .toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate(sql, batchParams);
    }
}