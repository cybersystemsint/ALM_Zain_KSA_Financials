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
 * Stores monthly depreciation snapshots for every asset.
 *
 * <p>The unique constraint on (assetId, depreciationPeriod) guarantees
 * exactly one record per asset per calendar month. The scheduler uses
 * an upsert pattern so re-runs within the same month overwrite rather
 * than duplicate.</p>
 *
 * <p>depreciationPeriod is stored as "YYYY-MM" (e.g. "2025-06") to make
 * uniqueness checks simple and index-friendly.</p>
 *
 * <p>Expected volume: ~3 million rows/month. Indexes are chosen to support
 * the most common access patterns: lookup by asset, by serial number,
 * by date range, and by depreciation period for upserts.</p>
 *
 * <h3>Performance Optimizations (v2)</h3>
 * <ul>
 *   <li><b>Unique constraint</b> on (assetId, depreciationPeriod) enforces
 *       1 record per asset per month at DB level — ensures upsert correctness
 *       and eliminates duplicate lookups.</li>
 *   <li><b>Reduced index count</b> from 9 to 4:
 *       <ul>
 *         <li>Composite index idx_dh_asset_period (assetId, depreciationPeriod)
 *             — used by scheduler bulk-fetch and upserts</li>
 *         <li>idx_dh_depreciationDate — used for date-range export filters</li>
 *         <li>idx_dh_serialNumber — used for serial number lookups in reconciliation</li>
 *         <li>idx_dh_depreciationPeriod — used for period-based archival/cleanup</li>
 *       </ul>
 *       Fewer indexes = faster INSERT/UPDATE batches (less overhead per write).</li>
 *   <li>Removed single-column indexes on category, statusFlag, nodeType, mapped
 *       — these are low-cardinality and rarely used for filtering in production.
 *       If needed, add composite indexes with depreciationDate or assetId.</li>
 * </ul>
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
        // Composite index for scheduler upsert bulk-fetch
        @Index(
            name = "idx_dh_asset_period",
            columnList = "assetId, depreciationPeriod"
        ),
        
        // Date-range queries for export filters
        @Index(
            name = "idx_dh_depreciationDate",
            columnList = "depreciationDate"
        ),
        
        // Serial number lookups in reconciliation
        @Index(
            name = "idx_dh_serialNumber",
            columnList = "serialNumber"
        ),
        
        // Period-based archival/cleanup
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

    private LocalDateTime recordDatetime;
    private String book;

    @Column(nullable = false)
    private String assetId;

    /**
     * Calendar month key in "YYYY-MM" format.
     * Combined with assetId forms the business unique key.
     */
    @Column(nullable = false, length = 7)
    private String depreciationPeriod;

    private Integer quantity;

    @Column(length = 512)
    private String description;

    private LocalDateTime creationDate;
    private String serialNumber;
    private String assetType;
    private String tagNumber;
    private String picStatus;
    private LocalDateTime picDate;
    private LocalDateTime cipDeliveryDate;
    private String linkId;
    private String acceptanceNumber;
    private String depreciateFlag;
    private String cipEu;
    private String invoiceNumber;
    private String poNumber;
    private String poLineNumber;
    private String uplLine;
    private String transferToNewFar;
    private String assetStatus;

    @Column(name = "value")
    private Double value;

    private String partNumber;
    private String vendorName;
    private String vendorNumber;
    private String mergedCode;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    private String costAccount;
    private String accumulatedDepreAccount;
    private String cipCostAccount;
    private String expenseCostCenter;
    private String expenseAccount;

    private Integer life;
    private LocalDateTime datePlacedInService;

    private Double cost;
    private Double nbv;
    private Double depreciationAmount;
    private Double ytdDepreciation;
    private Double depreciationReserve;
    private Double salvageValue;

    private String category;
    private String categoryDescription;

    private String locationSegment1;
    private String locationSegment2;
    private String locationSegment3;
    private String locationSegment4;
    private String locations;

    private Integer sequenceNumber;
    private Double monthlyDepreciationAmt;
    private Double accumulatedDepreciationAmt;

    private LocalDateTime depreciationDate;
    private Double netCost;
    private String statusFlag;

    private String changedBy;
    private String insertedBy;
    private String financialApproval;
    private LocalDateTime changedDate;

    private String nodeType;
    private String createdBy;
    private String updatedBy;
    private String mapped;

    public DepreciationHistory() {
    }

    // --- Getters and Setters ---

    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
    }

    public LocalDateTime getRecordDatetime() {
        return recordDatetime;
    }

    public void setRecordDatetime(LocalDateTime recordDatetime) {
        this.recordDatetime = recordDatetime;
    }

    public String getBook() {
        return book;
    }

    public void setBook(String book) {
        this.book = book;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getTagNumber() {
        return tagNumber;
    }

    public void setTagNumber(String tagNumber) {
        this.tagNumber = tagNumber;
    }

    public String getPicStatus() {
        return picStatus;
    }

    public void setPicStatus(String picStatus) {
        this.picStatus = picStatus;
    }

    public LocalDateTime getPicDate() {
        return picDate;
    }

    public void setPicDate(LocalDateTime picDate) {
        this.picDate = picDate;
    }

    public LocalDateTime getCipDeliveryDate() {
        return cipDeliveryDate;
    }

    public void setCipDeliveryDate(LocalDateTime cipDeliveryDate) {
        this.cipDeliveryDate = cipDeliveryDate;
    }

    public String getLinkId() {
        return linkId;
    }

    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    public String getAcceptanceNumber() {
        return acceptanceNumber;
    }

    public void setAcceptanceNumber(String acceptanceNumber) {
        this.acceptanceNumber = acceptanceNumber;
    }

    public String getDepreciateFlag() {
        return depreciateFlag;
    }

    public void setDepreciateFlag(String depreciateFlag) {
        this.depreciateFlag = depreciateFlag;
    }

    public String getCipEu() {
        return cipEu;
    }

    public void setCipEu(String cipEu) {
        this.cipEu = cipEu;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getPoLineNumber() {
        return poLineNumber;
    }

    public void setPoLineNumber(String poLineNumber) {
        this.poLineNumber = poLineNumber;
    }

    public String getUplLine() {
        return uplLine;
    }

    public void setUplLine(String uplLine) {
        this.uplLine = uplLine;
    }

    public String getTransferToNewFar() {
        return transferToNewFar;
    }

    public void setTransferToNewFar(String transferToNewFar) {
        this.transferToNewFar = transferToNewFar;
    }

    public String getAssetStatus() {
        return assetStatus;
    }

    public void setAssetStatus(String assetStatus) {
        this.assetStatus = assetStatus;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getVendorNumber() {
        return vendorNumber;
    }

    public void setVendorNumber(String vendorNumber) {
        this.vendorNumber = vendorNumber;
    }

    public String getMergedCode() {
        return mergedCode;
    }

    public void setMergedCode(String mergedCode) {
        this.mergedCode = mergedCode;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getCostAccount() {
        return costAccount;
    }

    public void setCostAccount(String costAccount) {
        this.costAccount = costAccount;
    }

    public String getAccumulatedDepreAccount() {
        return accumulatedDepreAccount;
    }

    public void setAccumulatedDepreAccount(String accumulatedDepreAccount) {
        this.accumulatedDepreAccount = accumulatedDepreAccount;
    }

    public String getCipCostAccount() {
        return cipCostAccount;
    }

    public void setCipCostAccount(String cipCostAccount) {
        this.cipCostAccount = cipCostAccount;
    }

    public String getExpenseCostCenter() {
        return expenseCostCenter;
    }

    public void setExpenseCostCenter(String expenseCostCenter) {
        this.expenseCostCenter = expenseCostCenter;
    }

    public String getExpenseAccount() {
        return expenseAccount;
    }

    public void setExpenseAccount(String expenseAccount) {
        this.expenseAccount = expenseAccount;
    }

    public Integer getLife() {
        return life;
    }

    public void setLife(Integer life) {
        this.life = life;
    }

    public LocalDateTime getDatePlacedInService() {
        return datePlacedInService;
    }

    public void setDatePlacedInService(LocalDateTime datePlacedInService) {
        this.datePlacedInService = datePlacedInService;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public Double getNbv() {
        return nbv;
    }

    public void setNbv(Double nbv) {
        this.nbv = nbv;
    }

    public Double getDepreciationAmount() {
        return depreciationAmount;
    }

    public void setDepreciationAmount(Double depreciationAmount) {
        this.depreciationAmount = depreciationAmount;
    }

    public Double getYtdDepreciation() {
        return ytdDepreciation;
    }

    public void setYtdDepreciation(Double ytdDepreciation) {
        this.ytdDepreciation = ytdDepreciation;
    }

    public Double getDepreciationReserve() {
        return depreciationReserve;
    }

    public void setDepreciationReserve(Double depreciationReserve) {
        this.depreciationReserve = depreciationReserve;
    }

    public Double getSalvageValue() {
        return salvageValue;
    }

    public void setSalvageValue(Double salvageValue) {
        this.salvageValue = salvageValue;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategoryDescription() {
        return categoryDescription;
    }

    public void setCategoryDescription(String categoryDescription) {
        this.categoryDescription = categoryDescription;
    }

    public String getLocationSegment1() {
        return locationSegment1;
    }

    public void setLocationSegment1(String locationSegment1) {
        this.locationSegment1 = locationSegment1;
    }

    public String getLocationSegment2() {
        return locationSegment2;
    }

    public void setLocationSegment2(String locationSegment2) {
        this.locationSegment2 = locationSegment2;
    }

    public String getLocationSegment3() {
        return locationSegment3;
    }

    public void setLocationSegment3(String locationSegment3) {
        this.locationSegment3 = locationSegment3;
    }

    public String getLocationSegment4() {
        return locationSegment4;
    }

    public void setLocationSegment4(String locationSegment4) {
        this.locationSegment4 = locationSegment4;
    }

    public String getLocations() {
        return locations;
    }

    public void setLocations(String locations) {
        this.locations = locations;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
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

    public LocalDateTime getDepreciationDate() {
        return depreciationDate;
    }

    public void setDepreciationDate(LocalDateTime depreciationDate) {
        this.depreciationDate = depreciationDate;
    }

    public Double getNetCost() {
        return netCost;
    }

    public void setNetCost(Double netCost) {
        this.netCost = netCost;
    }

    public String getStatusFlag() {
        return statusFlag;
    }

    public void setStatusFlag(String statusFlag) {
        this.statusFlag = statusFlag;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getInsertedBy() {
        return insertedBy;
    }

    public void setInsertedBy(String insertedBy) {
        this.insertedBy = insertedBy;
    }

    public String getFinancialApproval() {
        return financialApproval;
    }

    public void setFinancialApproval(String financialApproval) {
        this.financialApproval = financialApproval;
    }

    public LocalDateTime getChangedDate() {
        return changedDate;
    }

    public void setChangedDate(LocalDateTime changedDate) {
        this.changedDate = changedDate;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getMapped() {
        return mapped;
    }

    public void setMapped(String mapped) {
        this.mapped = mapped;
    }
}