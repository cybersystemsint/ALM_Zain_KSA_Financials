package com.zain.ksa.alm.financials.dto.response;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

/**
 * DTO for DepreciationHistory records exposed via the REST API and exports.
 *
 * <p>Contains the full asset snapshot plus computed depreciation fields.
 * The depreciationPeriod ("YYYY-MM") is the business key for monthly uniqueness.</p>
 */
@Value
@Builder
public class DepreciationHistoryDTO {

    Long recordNo;
    LocalDateTime recordDatetime;
    String book;
    String assetId;
    String depreciationPeriod;
    Integer quantity;
    String description;
    String serialNumber;
    String assetType;
    String tagNumber;
    String picStatus;
    LocalDateTime picDate;
    LocalDateTime cipDeliveryDate;
    String linkId;
    String acceptanceNumber;
    String depreciateFlag;
    String cipEu;
    String invoiceNumber;
    String poNumber;
    String poLineNumber;
    String uplLine;
    String transferToNewFar;
    String assetStatus;
    Double value;
    String partNumber;
    String vendorName;
    String vendorNumber;
    String mergedCode;
    LocalDateTime createdDate;
    LocalDateTime updatedDate;
    String costAccount;
    String accumulatedDepreAccount;
    String cipCostAccount;
    String expenseCostCenter;
    String expenseAccount;
    Integer life;
    LocalDateTime datePlacedInService;
    Double cost;
    Double nbv;
    Double depreciationAmount;
    Double ytdDepreciation;
    Double depreciationReserve;
    Double salvageValue;
    String category;
    String categoryDescription;
    String locationSegment1;
    String locationSegment2;
    String locationSegment3;
    String locationSegment4;
    String locations;
    Integer sequenceNumber;
    Double monthlyDepreciationAmt;
    Double accumulatedDepreciationAmt;
    LocalDateTime depreciationDate;
    Double netCost;
    String statusFlag;
    String changedBy;
    String insertedBy;
    String financialApproval;
    LocalDateTime changedDate;
    String nodeType;
    String createdBy;
    String updatedBy;
    String mapped;
}