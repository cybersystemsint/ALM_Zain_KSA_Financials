package com.zain.ksa.alm.financials.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Composite DTO for API responses and exports.
 *
 * <p>Combines depreciation metrics (from DepreciationHistory) with essential
 * asset master data (from FarReport). Populated by a JOIN query at the
 * repository layer — eliminates duplication while keeping API responses complete.</p>
 *
 * <p><b>Usage:</b></p>
 * <ul>
 *   <li>REST API: GET /depreciation-history/list → returns this DTO with full context</li>
 *   <li>Exports: CSV/Excel → includes both depreciation + asset details</li>
 *   <li>Dashboard: Real-time depreciation view with asset info in one response</li>
 * </ul>
 *
 * <p><b>Why composite?</b></p>
 * <ul>
 *   <li>Avoids sending two separate responses or two JOINs</li>
 *   <li>Keeps DepreciationHistory table lean (10 cols vs 60+)</li>
 *   <li>Supports flexible column selection per feature</li>
 * </ul>
 *
 * <p><b>Depreciation fields:</b> Updated monthly by scheduler.</p>
 * <p><b>Asset fields:</b> From FarReport master data (current snapshot).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetDepreciationDetailDTO {

    // ── Depreciation Metrics ──────────────────────────────────────────────────

    /** Primary key of depreciation record. */
    private Long recordNo;

    /** Period in "YYYY-MM" format. */
    private String depreciationPeriod;

    /** Monthly depreciation amount: (cost - salvage) / life */
    private Double monthlyDepreciationAmt;

    /** Cumulative depreciation from service date to period end. */
    private Double accumulatedDepreciationAmt;

    /** Net book value: cost - accumulated depreciation */
    private Double netCost;

    /** When depreciation was computed (typically end-of-month). */
    private LocalDateTime depreciationDate;

    // ── Asset Master Data (from FarReport) ────────────────────────────────────

    /** Asset identifier (FK to FarReport). */
    private String assetId;

    /** Reporting book (e.g., "GAAP", "TAX"). */
    private String book;

    /** Asset description. */
    private String description;

    /** Serial/ID number for physical identification. */
    private String serialNumber;

    /** Asset type classification. */
    private String assetType;

    /** Accounting asset category. */
    private String category;

    /** Description of category. */
    private String categoryDescription;

    /** Original cost of asset. */
    private Double cost;

   private String mapped;

    /** Expected salvage/residual value. */
    private Double salvageValue;

    /** Useful life in years. */
    private Integer life;

    /** Date asset was placed in service. */
    private LocalDateTime datePlacedInService;

    /** GL account for asset cost. */
    private String costAccount;

    /** GL account for accumulated depreciation. */
    private String accumulatedDepreAccount;

    /** GL account for depreciation expense. */
    private String expenseAccount;

    /** Physical quantity (for bulk assets). */
    private Integer quantity;

    /** Current market/book value. */
    private Double value;

    // ── Audit Trail ───────────────────────────────────────────────────────────

    /** Who created the depreciation record. */
    private String createdBy;

    /** Who last changed the depreciation record. */
    private String changedBy;

    /** When the depreciation record was inserted. */
    private LocalDateTime recordDatetime;
}