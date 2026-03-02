package com.zain.ksa.alm.financials.mapper;

import com.zain.ksa.alm.financials.dto.response.DepreciationHistoryDTO;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;

/**
 * Mapper interface for converting DepreciationHistory entity to DTO.
 *
 * <p>If using MapStruct, annotate with @Mapper(componentModel = "spring").
 * If hand-written, implement the toDto method below.</p>
 *
 * <p>NOTE: This file shows the required mapping contract. If you already have
 * an InventoryMapper that handles other entities, add this method to it.
 * Otherwise, create a dedicated implementation.</p>
 */
public interface DepreciationHistoryMapper {

    /**
     * Manual mapping example (use if not using MapStruct):
     */
    default DepreciationHistoryDTO toDto(DepreciationHistory e) {
        if (e == null) return null;
        return DepreciationHistoryDTO.builder()
                .recordNo(e.getRecordNo())
                .recordDatetime(e.getRecordDatetime())
                .book(e.getBook())
                .assetId(e.getAssetId())
                .depreciationPeriod(e.getDepreciationPeriod())
                .quantity(e.getQuantity())
                .description(e.getDescription())
                .serialNumber(e.getSerialNumber())
                .assetType(e.getAssetType())
                .tagNumber(e.getTagNumber())
                .picStatus(e.getPicStatus())
                .picDate(e.getPicDate())
                .cipDeliveryDate(e.getCipDeliveryDate())
                .linkId(e.getLinkId())
                .acceptanceNumber(e.getAcceptanceNumber())
                .depreciateFlag(e.getDepreciateFlag())
                .cipEu(e.getCipEu())
                .invoiceNumber(e.getInvoiceNumber())
                .poNumber(e.getPoNumber())
                .poLineNumber(e.getPoLineNumber())
                .uplLine(e.getUplLine())
                .transferToNewFar(e.getTransferToNewFar())
                .assetStatus(e.getAssetStatus())
                .value(e.getValue())
                .partNumber(e.getPartNumber())
                .vendorName(e.getVendorName())
                .vendorNumber(e.getVendorNumber())
                .mergedCode(e.getMergedCode())
                .createdDate(e.getCreatedDate())
                .updatedDate(e.getUpdatedDate())
                .costAccount(e.getCostAccount())
                .accumulatedDepreAccount(e.getAccumulatedDepreAccount())
                .cipCostAccount(e.getCipCostAccount())
                .expenseCostCenter(e.getExpenseCostCenter())
                .expenseAccount(e.getExpenseAccount())
                .life(e.getLife())
                .datePlacedInService(e.getDatePlacedInService())
                .cost(e.getCost())
                .nbv(e.getNbv())
                .depreciationAmount(e.getDepreciationAmount())
                .ytdDepreciation(e.getYtdDepreciation())
                .depreciationReserve(e.getDepreciationReserve())
                .salvageValue(e.getSalvageValue())
                .category(e.getCategory())
                .categoryDescription(e.getCategoryDescription())
                .locationSegment1(e.getLocationSegment1())
                .locationSegment2(e.getLocationSegment2())
                .locationSegment3(e.getLocationSegment3())
                .locationSegment4(e.getLocationSegment4())
                .locations(e.getLocations())
                .sequenceNumber(e.getSequenceNumber())
                .monthlyDepreciationAmt(e.getMonthlyDepreciationAmt())
                .accumulatedDepreciationAmt(e.getAccumulatedDepreciationAmt())
                .depreciationDate(e.getDepreciationDate())
                .netCost(e.getNetCost())
                .statusFlag(e.getStatusFlag())
                .changedBy(e.getChangedBy())
                .insertedBy(e.getInsertedBy())
                .financialApproval(e.getFinancialApproval())
                .changedDate(e.getChangedDate())
                .nodeType(e.getNodeType())
                .createdBy(e.getCreatedBy())
                .updatedBy(e.getUpdatedBy())
                .mapped(e.getMapped())
                .build();
    }
}