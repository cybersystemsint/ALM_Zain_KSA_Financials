package com.zain.ksa.alm.financials.dto.response;

import java.io.Serializable;
import java.util.Date;

/**
 * DTO for FAR report records.
 *
 * <p>Used by:</p>
 * <ul>
 *   <li>Paginated list endpoint ({@code POST /api/far/list})</li>
 *   <li>Streaming export pipeline (FileExportStrategy uses reflection on this DTO)</li>
 * </ul>
 *
 * <p>Field order determines Excel column order (reflection iterates declared fields
 * in source-code order).</p>
 */
public class FarReportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer recordNo;
    private Date    recordDatetime;
    private String  book;
    private String  assetId;
    private Integer quantity;
    private String  description;
    private Date    creationDate;
    private String  serialNumber;
    private String  assetType;
    private String  tagNumber;
    private String  picStatus;
    private Date    picDate;
    private Date    cipDeliveryDate;
    private String  linkId;
    private String  acceptanceNumber;
    private String  depreciateFlag;
    private String  cipEu;
    private String  invoiceNumber;
    private String  poNumber;
    private String  poLineNumber;
    private String  uplLine;
    private String  transferToNewFar;
    private String  assetStatus;
    private Double  value;
    private String  partNumber;
    private String  vendorName;
    private String  vendorNumber;
    private String  mergedCode;
    private Date    createdDate;
    private Date    updatedDate;
    private String  costAccount;
    private String  accumulatedDepreAccount;
    private String  cipCostAccount;
    private String  expenseCostCenter;
    private String  expenseAccount;
    private Integer life;
    private Date    datePlacedInService;
    private Double  cost;
    private Double  nbv;
    private Double  depreciationAmount;
    private Double  ytdDepreciation;
    private Double  depreciationReserve;
    private Double  salvageValue;
    private String  category;
    private String  categoryDescription;
    private String  locationSegment1;
    private String  locationSegment2;
    private String  locationSegment3;
    private String  locationSegment4;
    private String  locations;
    private Integer sequenceNumber;
    private Double  monthlyDepreciationAmt;
    private Double  accumulatedDepreciationAmt;
    private Date    depreciationDate;
    private Double  netCost;
    private String  statusFlag;
    private String  nodeType;
    private String  createdBy;
    private String  updatedBy;
    private String  changedBy;
    private String  insertedBy;
    private String  financialApproval;
    private Date    changedDate;
    private String mapped;

    // ── Getters and setters ───────────────────────────────────────────────────

    public Integer getRecordNo() { return recordNo; }
    public void setRecordNo(Integer recordNo) { this.recordNo = recordNo; }

    public Date getRecordDatetime() { return recordDatetime; }
    public void setRecordDatetime(Date recordDatetime) { this.recordDatetime = recordDatetime; }

    public String getBook() { return book; }
    public void setBook(String book) { this.book = book; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getCreationDate() { return creationDate; }
    public void setCreationDate(Date creationDate) { this.creationDate = creationDate; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

    public String getPicStatus() { return picStatus; }
    public void setPicStatus(String picStatus) { this.picStatus = picStatus; }

    public Date getPicDate() { return picDate; }
    public void setPicDate(Date picDate) { this.picDate = picDate; }

    public Date getCipDeliveryDate() { return cipDeliveryDate; }
    public void setCipDeliveryDate(Date cipDeliveryDate) { this.cipDeliveryDate = cipDeliveryDate; }

    public String getLinkId() { return linkId; }
    public void setLinkId(String linkId) { this.linkId = linkId; }

    public String getAcceptanceNumber() { return acceptanceNumber; }
    public void setAcceptanceNumber(String acceptanceNumber) { this.acceptanceNumber = acceptanceNumber; }

    public String getDepreciateFlag() { return depreciateFlag; }
    public void setDepreciateFlag(String depreciateFlag) { this.depreciateFlag = depreciateFlag; }

    public String getCipEu() { return cipEu; }
    public void setCipEu(String cipEu) { this.cipEu = cipEu; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public String getPoLineNumber() { return poLineNumber; }
    public void setPoLineNumber(String poLineNumber) { this.poLineNumber = poLineNumber; }

    public String getUplLine() { return uplLine; }
    public void setUplLine(String uplLine) { this.uplLine = uplLine; }

    public String getTransferToNewFar() { return transferToNewFar; }
    public void setTransferToNewFar(String transferToNewFar) { this.transferToNewFar = transferToNewFar; }

    public String getAssetStatus() { return assetStatus; }
    public void setAssetStatus(String assetStatus) { this.assetStatus = assetStatus; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public String getVendorNumber() { return vendorNumber; }
    public void setVendorNumber(String vendorNumber) { this.vendorNumber = vendorNumber; }

    public String getMergedCode() { return mergedCode; }
    public void setMergedCode(String mergedCode) { this.mergedCode = mergedCode; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public Date getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(Date updatedDate) { this.updatedDate = updatedDate; }

    public String getCostAccount() { return costAccount; }
    public void setCostAccount(String costAccount) { this.costAccount = costAccount; }

    public String getAccumulatedDepreAccount() { return accumulatedDepreAccount; }
    public void setAccumulatedDepreAccount(String accumulatedDepreAccount) { this.accumulatedDepreAccount = accumulatedDepreAccount; }

    public String getCipCostAccount() { return cipCostAccount; }
    public void setCipCostAccount(String cipCostAccount) { this.cipCostAccount = cipCostAccount; }

    public String getExpenseCostCenter() { return expenseCostCenter; }
    public void setExpenseCostCenter(String expenseCostCenter) { this.expenseCostCenter = expenseCostCenter; }

    public String getExpenseAccount() { return expenseAccount; }
    public void setExpenseAccount(String expenseAccount) { this.expenseAccount = expenseAccount; }

    public Integer getLife() { return life; }
    public void setLife(Integer life) { this.life = life; }

    public Date getDatePlacedInService() { return datePlacedInService; }
    public void setDatePlacedInService(Date datePlacedInService) { this.datePlacedInService = datePlacedInService; }

    public Double getCost() { return cost; }
    public void setCost(Double cost) { this.cost = cost; }

    public Double getNbv() { return nbv; }
    public void setNbv(Double nbv) { this.nbv = nbv; }

    public Double getDepreciationAmount() { return depreciationAmount; }
    public void setDepreciationAmount(Double depreciationAmount) { this.depreciationAmount = depreciationAmount; }

    public Double getYtdDepreciation() { return ytdDepreciation; }
    public void setYtdDepreciation(Double ytdDepreciation) { this.ytdDepreciation = ytdDepreciation; }

    public Double getDepreciationReserve() { return depreciationReserve; }
    public void setDepreciationReserve(Double depreciationReserve) { this.depreciationReserve = depreciationReserve; }

    public Double getSalvageValue() { return salvageValue; }
    public void setSalvageValue(Double salvageValue) { this.salvageValue = salvageValue; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCategoryDescription() { return categoryDescription; }
    public void setCategoryDescription(String categoryDescription) { this.categoryDescription = categoryDescription; }

    public String getLocationSegment1() { return locationSegment1; }
    public void setLocationSegment1(String locationSegment1) { this.locationSegment1 = locationSegment1; }

    public String getLocationSegment2() { return locationSegment2; }
    public void setLocationSegment2(String locationSegment2) { this.locationSegment2 = locationSegment2; }

    public String getLocationSegment3() { return locationSegment3; }
    public void setLocationSegment3(String locationSegment3) { this.locationSegment3 = locationSegment3; }

    public String getLocationSegment4() { return locationSegment4; }
    public void setLocationSegment4(String locationSegment4) { this.locationSegment4 = locationSegment4; }

    public String getLocations() { return locations; }
    public void setLocations(String locations) { this.locations = locations; }

    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public Double getMonthlyDepreciationAmt() { return monthlyDepreciationAmt; }
    public void setMonthlyDepreciationAmt(Double monthlyDepreciationAmt) { this.monthlyDepreciationAmt = monthlyDepreciationAmt; }

    public Double getAccumulatedDepreciationAmt() { return accumulatedDepreciationAmt; }
    public void setAccumulatedDepreciationAmt(Double accumulatedDepreciationAmt) { this.accumulatedDepreciationAmt = accumulatedDepreciationAmt; }

    public Date getDepreciationDate() { return depreciationDate; }
    public void setDepreciationDate(Date depreciationDate) { this.depreciationDate = depreciationDate; }

    public Double getNetCost() { return netCost; }
    public void setNetCost(Double netCost) { this.netCost = netCost; }

    public String getStatusFlag() { return statusFlag; }
    public void setStatusFlag(String statusFlag) { this.statusFlag = statusFlag; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public String getInsertedBy() { return insertedBy; }
    public void setInsertedBy(String insertedBy) { this.insertedBy = insertedBy; }

    public String getFinancialApproval() { return financialApproval; }
    public void setFinancialApproval(String financialApproval) { this.financialApproval = financialApproval; }

    public Date getChangedDate() { return changedDate; }
    public void setChangedDate(Date changedDate) { this.changedDate = changedDate; }

    public String getMapped() { return mapped; }
public void setMapped(String mapped) { this.mapped = mapped; }
}