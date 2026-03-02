package com.zain.ksa.alm.financials.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "tb_unmapped_passive_inventory",
    indexes = {
        @Index(name = "idx_upi_serialNumber", columnList = "serialNumber"),
        @Index(name = "idx_upi_siteId",       columnList = "siteId")
    }
)

public class UnmappedPassiveInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime recordDateTime;
    private String inventoryId;
    private String objectId;
    private String parentName;
    private String siteId;
    private String itemBarCode;
    private String serialNumber;
    private String model;

    @Column(columnDefinition = "TEXT")
    private String note;

    private String part;
    private String entryUser;
    private String entryDate;
    private String itemStatus;
    private String categoryInNEP;
    private String scrapStatus;
    private String inventoryType;
    private Integer inventoryTypeId;
    private String locationSubType;
    private String locationClassification;
    private String itemClassification;
    private String itemClassification2;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String prPoNo;
    

    public UnmappedPassiveInventory() {
    }

    public UnmappedPassiveInventory(Long id, LocalDateTime recordDateTime, String inventoryId, String objectId,
            String parentName, String siteId, String itemBarCode, String serialNumber, String model, String note,
            String part, String entryUser, String entryDate, String itemStatus, String categoryInNEP,
            String scrapStatus, String inventoryType, Integer inventoryTypeId, String locationSubType,
            String locationClassification, String itemClassification, String itemClassification2, String notes,
            String prPoNo) {
        this.id = id;
        this.recordDateTime = recordDateTime;
        this.inventoryId = inventoryId;
        this.objectId = objectId;
        this.parentName = parentName;
        this.siteId = siteId;
        this.itemBarCode = itemBarCode;
        this.serialNumber = serialNumber;
        this.model = model;
        this.note = note;
        this.part = part;
        this.entryUser = entryUser;
        this.entryDate = entryDate;
        this.itemStatus = itemStatus;
        this.categoryInNEP = categoryInNEP;
        this.scrapStatus = scrapStatus;
        this.inventoryType = inventoryType;
        this.inventoryTypeId = inventoryTypeId;
        this.locationSubType = locationSubType;
        this.locationClassification = locationClassification;
        this.itemClassification = itemClassification;
        this.itemClassification2 = itemClassification2;
        this.notes = notes;
        this.prPoNo = prPoNo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(LocalDateTime recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    public String getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(String inventoryId) {
        this.inventoryId = inventoryId;
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

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getItemBarCode() {
        return itemBarCode;
    }

    public void setItemBarCode(String itemBarCode) {
        this.itemBarCode = itemBarCode;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
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

    public String getEntryUser() {
        return entryUser;
    }

    public void setEntryUser(String entryUser) {
        this.entryUser = entryUser;
    }

    public String getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(String entryDate) {
        this.entryDate = entryDate;
    }

    public String getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(String itemStatus) {
        this.itemStatus = itemStatus;
    }

    public String getCategoryInNEP() {
        return categoryInNEP;
    }

    public void setCategoryInNEP(String categoryInNEP) {
        this.categoryInNEP = categoryInNEP;
    }

    public String getScrapStatus() {
        return scrapStatus;
    }

    public void setScrapStatus(String scrapStatus) {
        this.scrapStatus = scrapStatus;
    }

    public String getInventoryType() {
        return inventoryType;
    }

    public void setInventoryType(String inventoryType) {
        this.inventoryType = inventoryType;
    }

    public Integer getInventoryTypeId() {
        return inventoryTypeId;
    }

    public void setInventoryTypeId(Integer inventoryTypeId) {
        this.inventoryTypeId = inventoryTypeId;
    }

    public String getLocationSubType() {
        return locationSubType;
    }

    public void setLocationSubType(String locationSubType) {
        this.locationSubType = locationSubType;
    }

    public String getLocationClassification() {
        return locationClassification;
    }

    public void setLocationClassification(String locationClassification) {
        this.locationClassification = locationClassification;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPrPoNo() {
        return prPoNo;
    }

    public void setPrPoNo(String prPoNo) {
        this.prPoNo = prPoNo;
    }


    
}