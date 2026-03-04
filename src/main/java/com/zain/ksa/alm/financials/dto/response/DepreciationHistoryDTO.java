package com.zain.ksa.alm.financials.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lean DTO for DepreciationHistory records.
 *
 * <p>Contains ONLY depreciation metrics and foreign keys. Master asset data
 * (description, category, cost, etc.) are queried separately or via JOIN.</p>
 *
 * <p><b>Usage:</b></p>
 * <ul>
 *   <li>REST API responses: GET /depreciation-history/list</li>
 *   <li>Exports: CSV/Excel with minimal columns</li>
 *   <li>Scheduler verification: Confirm computed values persisted correctly</li>
 * </ul>
 *
 * <p><b>Field order:</b> Matches database column order for consistency.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationHistoryDTO {

    private Long recordNo;
    private String assetId;
    private String depreciationPeriod;
    private Double monthlyDepreciationAmt;
    private Double accumulatedDepreciationAmt;
    private Double netCost;
    private LocalDateTime depreciationDate;
    private LocalDateTime recordDatetime;
    private String createdBy;
    private String changedBy;
}