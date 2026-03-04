package com.zain.ksa.alm.financials.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Lean DepreciationHistory entity storing ONLY computed depreciation metrics.
 *
 * <p><b>Normalization Strategy:</b></p>
 * <ul>
 *   <li>This table records monthly depreciation snapshots: one record per asset per month.</li>
 *   <li>Contains ONLY the columns updated by the depreciation scheduler:
 *       <ul>
 *         <li>monthlyDepreciationAmt</li>
 *         <li>accumulatedDepreciationAmt</li>
 *         <li>netCost</li>
 *         <li>depreciationDate</li>
 *       </ul>
 *   </li>
 *   <li>Master asset data (description, serialNumber, category, etc.) remains in FarReport.</li>
 *   <li>At query time, JOIN with FarReport on assetId to fetch asset details on-demand.</li>
 * </ul>
 *
 * <p><b>Benefits:</b></p>
 * <ul>
 *   <li><b>Storage:</b> 10 columns instead of 60+ → 7.5× smaller table</li>
 *   <li><b>Performance:</b> Faster batch inserts (30M field writes vs 180M)</li>
 *   <li><b>Data freshness:</b> Master data always current (no sync lag)</li>
 *   <li><b>Flexibility:</b> Select only needed columns per use case</li>
 * </ul>
 *
 * <p><b>Expected volume:</b> ~3 million rows/month.</p>
 *
 * <p><b>Unique constraint:</b> (assetId, depreciationPeriod) ensures exactly one
 * record per asset per calendar month. Allows safe upsert pattern in scheduler.</p>
 */
@Entity
@Table(
    name = "tb_DepreciationHistory",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_dh_asset_period",
            columnNames = {"assetId", "depreciationPeriod"}
        )
    },
    indexes = {
        // Composite index for scheduler bulk-fetch and upserts
        @Index(
            name = "idx_dh_asset_period",
            columnList = "assetId, depreciationPeriod"
        ),
        
        // Date-range queries for export filters
        @Index(
            name = "idx_dh_depreciationDate",
            columnList = "depreciationDate"
        ),
        
        // Period-based queries (e.g., "list all for Feb 2025")
        @Index(
            name = "idx_dh_depreciationPeriod",
            columnList = "depreciationPeriod"
        )
    }
)
public class DepreciationHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordNo;

    /**
     * Foreign key to FarReport asset. Use JOIN to fetch asset details.
     */
    @Column(nullable = false)
    private String assetId;

    /**
     * Calendar month key in "YYYY-MM" format (e.g., "2025-02").
     * Combined with assetId forms the unique business key.
     * Supports period-based lookups and archival/cleanup queries.
     */
    @Column(nullable = false, length = 7)
    private String depreciationPeriod;

    /**
     * Computed monthly depreciation amount.
     * Typically = (cost - salvageValue) / usefulLife
     */
    @Column(nullable = false)
    private Double monthlyDepreciationAmt;

    /**
     * Sum of all depreciation from service date to end of current period.
     * Updated every run to reflect cumulative depreciation.
     */
    @Column(nullable = false)
    private Double accumulatedDepreciationAmt;

    /**
     * Net book value: cost - accumulatedDepreciationAmt.
     * Snapshot at end of period.
     */
    @Column(nullable = false)
    private Double netCost;

    /**
     * Timestamp when depreciation was computed (typically end-of-month).
     * Used for date-range export filters.
     */
    @Column(nullable = false)
    private LocalDateTime depreciationDate;

    /**
     * Record insertion timestamp for audit trail.
     */
    @Column(nullable = false)
    private LocalDateTime recordDatetime;

    /**
     * Audit: who created this depreciation record (typically "SCHEDULER").
     */
    private String createdBy;

    /**
     * Audit: who last changed this record.
     */
    private String changedBy;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DepreciationHistory() {
    }

    public DepreciationHistory(String assetId, String depreciationPeriod,
                               Double monthlyDepreciationAmt,
                               Double accumulatedDepreciationAmt,
                               Double netCost,
                               LocalDateTime depreciationDate,
                               LocalDateTime recordDatetime) {
        this.assetId = assetId;
        this.depreciationPeriod = depreciationPeriod;
        this.monthlyDepreciationAmt = monthlyDepreciationAmt;
        this.accumulatedDepreciationAmt = accumulatedDepreciationAmt;
        this.netCost = netCost;
        this.depreciationDate = depreciationDate;
        this.recordDatetime = recordDatetime;
    }

    // ── Getters and Setters ───────────────────────────────────────────────────

    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getDepreciationPeriod() {
        return depreciationPeriod;
    }

    public void setDepreciationPeriod(String depreciationPeriod) {
        this.depreciationPeriod = depreciationPeriod;
    }

    public Double getMonthlyDepreciationAmt() {
        return monthlyDepreciationAmt;
    }

    public void setMonthlyDepreciationAmt(Double monthlyDepreciationAmt) {
        this.monthlyDepreciationAmt = monthlyDepreciationAmt;
    }

    public Double getAccumulatedDepreciationAmt() {
        return accumulatedDepreciationAmt;
    }

    public void setAccumulatedDepreciationAmt(Double accumulatedDepreciationAmt) {
        this.accumulatedDepreciationAmt = accumulatedDepreciationAmt;
    }

    public Double getNetCost() {
        return netCost;
    }

    public void setNetCost(Double netCost) {
        this.netCost = netCost;
    }

    public LocalDateTime getDepreciationDate() {
        return depreciationDate;
    }

    public void setDepreciationDate(LocalDateTime depreciationDate) {
        this.depreciationDate = depreciationDate;
    }

    public LocalDateTime getRecordDatetime() {
        return recordDatetime;
    }

    public void setRecordDatetime(LocalDateTime recordDatetime) {
        this.recordDatetime = recordDatetime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
}