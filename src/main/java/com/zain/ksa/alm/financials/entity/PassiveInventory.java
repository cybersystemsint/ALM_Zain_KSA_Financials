package com.zain.ksa.alm.financials.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;


@Entity
@Table(
    name = "tb_Passive_Inventory",
    indexes = {
        @Index(name = "idx_pi_serialNumber", columnList = "serialNumber"),
        @Index(name = "idx_pi_siteId",       columnList = "siteId"),
        @Index(name = "idx_pi_isMapped",     columnList = "isMapped")
    }
)

public class PassiveInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    private LocalDateTime recordDateTime;
    private String siteId;
    private String objectId;
    private String parentName;
    private Integer status;
    private String actualLatitude;
    private String actualLongitude;
    private String auditDate;
    private String auditUser;
    private String entryDate;
    private String entryUser;
    private String internalReference;
    private String itemBarCode;
    private String itemCapacity;
    private String itemClassification;
    private String itemClassification2;
    private String itemCode;
    private String itemMake;
    private String itemStatus;
    private String itemSupplier;
    private String lastModifiedDate;
    private String lastModifiedUser;
    private String locationAddress;
    private String locationClassification;
    private String locationSubType;
    private String model;

    @Column(columnDefinition = "TEXT")
    private String note;

    private String part;
    private String serialNumber;
    private String shelterVendor;
    private String shelterRoomId;
    private String UOM;
    private String categoryInNEP;
    private String ownership;
    private String scrapStatus;
    private String scrapUser;
    private String scrapDate;
    private String lastPVStatus;
    private String lastPVUser;
    private String lastPVDate;
    private String PRPONo;
    private Integer inventoryType;
    private String changedBy;
    private LocalDateTime changedDate;
    private Boolean isMapped;

    
    public PassiveInventory() {
    }

    public PassiveInventory(Integer recordNo, LocalDateTime recordDateTime, String siteId, String objectId,
            String parentName, Integer status, String actualLatitude, String actualLongitude, String auditDate,
            String auditUser, String entryDate, String entryUser, String internalReference, String itemBarCode,
            String itemCapacity, String itemClassification, String itemClassification2, String itemCode,
            String itemMake, String itemStatus, String itemSupplier, String lastModifiedDate, String lastModifiedUser,
            String locationAddress, String locationClassification, String locationSubType, String model, String note,
            String part, String serialNumber, String shelterVendor, String shelterRoomId, String uOM,
            String categoryInNEP, String ownership, String scrapStatus, String scrapUser, String scrapDate,
            String lastPVStatus, String lastPVUser, String lastPVDate, String pRPONo, Integer inventoryType,
            String changedBy, LocalDateTime changedDate, Boolean isMapped) {
        this.recordNo = recordNo;
        this.recordDateTime = recordDateTime;
        this.siteId = siteId;
        this.objectId = objectId;
        this.parentName = parentName;
        this.status = status;
        this.actualLatitude = actualLatitude;
        this.actualLongitude = actualLongitude;
        this.auditDate = auditDate;
        this.auditUser = auditUser;
        this.entryDate = entryDate;
        this.entryUser = entryUser;
        this.internalReference = internalReference;
        this.itemBarCode = itemBarCode;
        this.itemCapacity = itemCapacity;
        this.itemClassification = itemClassification;
        this.itemClassification2 = itemClassification2;
        this.itemCode = itemCode;
        this.itemMake = itemMake;
        this.itemStatus = itemStatus;
        this.itemSupplier = itemSupplier;
        this.lastModifiedDate = lastModifiedDate;
        this.lastModifiedUser = lastModifiedUser;
        this.locationAddress = locationAddress;
        this.locationClassification = locationClassification;
        this.locationSubType = locationSubType;
        this.model = model;
        this.note = note;
        this.part = part;
        this.serialNumber = serialNumber;
        this.shelterVendor = shelterVendor;
        this.shelterRoomId = shelterRoomId;
        UOM = uOM;
        this.categoryInNEP = categoryInNEP;
        this.ownership = ownership;
        this.scrapStatus = scrapStatus;
        this.scrapUser = scrapUser;
        this.scrapDate = scrapDate;
        this.lastPVStatus = lastPVStatus;
        this.lastPVUser = lastPVUser;
        this.lastPVDate = lastPVDate;
        PRPONo = pRPONo;
        this.inventoryType = inventoryType;
        this.changedBy = changedBy;
        this.changedDate = changedDate;
        this.isMapped = isMapped;
    }
    public Integer getRecordNo() {
        return recordNo;
    }
    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
    }
    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }
    public void setRecordDateTime(LocalDateTime recordDateTime) {
        this.recordDateTime = recordDateTime;
    }
    public String getSiteId() {
        return siteId;
    }
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }
    public String getObjectId() {
        return objectId;
    }
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }
    public String getParentName() {
        return parentName;
    }
    public void setParentName(String parentName) {
        this.parentName = parentName;
    }
    public Integer getStatus() {
        return status;
    }
    public void setStatus(Integer status) {
        this.status = status;
    }
    public String getActualLatitude() {
        return actualLatitude;
    }
    public void setActualLatitude(String actualLatitude) {
        this.actualLatitude = actualLatitude;
    }
    public String getActualLongitude() {
        return actualLongitude;
    }
    public void setActualLongitude(String actualLongitude) {
        this.actualLongitude = actualLongitude;
    }
    public String getAuditDate() {
        return auditDate;
    }
    public void setAuditDate(String auditDate) {
        this.auditDate = auditDate;
    }
    public String getAuditUser() {
        return auditUser;
    }
    public void setAuditUser(String auditUser) {
        this.auditUser = auditUser;
    }
    public String getEntryDate() {
        return entryDate;
    }
    public void setEntryDate(String entryDate) {
        this.entryDate = entryDate;
    }
    public String getEntryUser() {
        return entryUser;
    }
    public void setEntryUser(String entryUser) {
        this.entryUser = entryUser;
    }
    public String getInternalReference() {
        return internalReference;
    }
    public void setInternalReference(String internalReference) {
        this.internalReference = internalReference;
    }
    public String getItemBarCode() {
        return itemBarCode;
    }
    public void setItemBarCode(String itemBarCode) {
        this.itemBarCode = itemBarCode;
    }
    public String getItemCapacity() {
        return itemCapacity;
    }
    public void setItemCapacity(String itemCapacity) {
        this.itemCapacity = itemCapacity;
    }
    public String getItemClassification() {
        return itemClassification;
    }
    public void setItemClassification(String itemClassification) {
        this.itemClassification = itemClassification;
    }
    public String getItemClassification2() {
        return itemClassification2;
    }
    public void setItemClassification2(String itemClassification2) {
        this.itemClassification2 = itemClassification2;
    }
    public String getItemCode() {
        return itemCode;
    }
    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }
    public String getItemMake() {
        return itemMake;
    }
    public void setItemMake(String itemMake) {
        this.itemMake = itemMake;
    }
    public String getItemStatus() {
        return itemStatus;
    }
    public void setItemStatus(String itemStatus) {
        this.itemStatus = itemStatus;
    }
    public String getItemSupplier() {
        return itemSupplier;
    }
    public void setItemSupplier(String itemSupplier) {
        this.itemSupplier = itemSupplier;
    }
    public String getLastModifiedDate() {
        return lastModifiedDate;
    }
    public void setLastModifiedDate(String lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
    public String getLastModifiedUser() {
        return lastModifiedUser;
    }
    public void setLastModifiedUser(String lastModifiedUser) {
        this.lastModifiedUser = lastModifiedUser;
    }
    public String getLocationAddress() {
        return locationAddress;
    }
    public void setLocationAddress(String locationAddress) {
        this.locationAddress = locationAddress;
    }
    public String getLocationClassification() {
        return locationClassification;
    }
    public void setLocationClassification(String locationClassification) {
        this.locationClassification = locationClassification;
    }
    public String getLocationSubType() {
        return locationSubType;
    }
    public void setLocationSubType(String locationSubType) {
        this.locationSubType = locationSubType;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public String getNote() {
        return note;
    }
    public void setNote(String note) {
        this.note = note;
    }
    public String getPart() {
        return part;
    }
    public void setPart(String part) {
        this.part = part;
    }
    public String getSerialNumber() {
        return serialNumber;
    }
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
    public String getShelterVendor() {
        return shelterVendor;
    }
    public void setShelterVendor(String shelterVendor) {
        this.shelterVendor = shelterVendor;
    }
    public String getShelterRoomId() {
        return shelterRoomId;
    }
    public void setShelterRoomId(String shelterRoomId) {
        this.shelterRoomId = shelterRoomId;
    }
    public String getUOM() {
        return UOM;
    }
    public void setUOM(String uOM) {
        UOM = uOM;
    }
    public String getCategoryInNEP() {
        return categoryInNEP;
    }
    public void setCategoryInNEP(String categoryInNEP) {
        this.categoryInNEP = categoryInNEP;
    }
    public String getOwnership() {
        return ownership;
    }
    public void setOwnership(String ownership) {
        this.ownership = ownership;
    }
    public String getScrapStatus() {
        return scrapStatus;
    }
    public void setScrapStatus(String scrapStatus) {
        this.scrapStatus = scrapStatus;
    }
    public String getScrapUser() {
        return scrapUser;
    }
    public void setScrapUser(String scrapUser) {
        this.scrapUser = scrapUser;
    }
    public String getScrapDate() {
        return scrapDate;
    }
    public void setScrapDate(String scrapDate) {
        this.scrapDate = scrapDate;
    }
    public String getLastPVStatus() {
        return lastPVStatus;
    }
    public void setLastPVStatus(String lastPVStatus) {
        this.lastPVStatus = lastPVStatus;
    }
    public String getLastPVUser() {
        return lastPVUser;
    }
    public void setLastPVUser(String lastPVUser) {
        this.lastPVUser = lastPVUser;
    }
    public String getLastPVDate() {
        return lastPVDate;
    }
    public void setLastPVDate(String lastPVDate) {
        this.lastPVDate = lastPVDate;
    }
    public String getPRPONo() {
        return PRPONo;
    }
    public void setPRPONo(String pRPONo) {
        PRPONo = pRPONo;
    }
    public Integer getInventoryType() {
        return inventoryType;
    }
    public void setInventoryType(Integer inventoryType) {
        this.inventoryType = inventoryType;
    }
    public String getChangedBy() {
        return changedBy;
    }
    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
    public LocalDateTime getChangedDate() {
        return changedDate;
    }
    public void setChangedDate(LocalDateTime changedDate) {
        this.changedDate = changedDate;
    }
    public Boolean getIsMapped() {
        return isMapped;
    }
    public void setIsMapped(Boolean isMapped) {
        this.isMapped = isMapped;
    }


    
}