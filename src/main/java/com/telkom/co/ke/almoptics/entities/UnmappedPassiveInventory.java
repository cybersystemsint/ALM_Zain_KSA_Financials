package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.TemporalType;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "tb_unmapped_passive_inventory")
public class UnmappedPassiveInventory implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
     @Transient
    private int rowNumber;
   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date recordDateTime;
    private String inventoryId;
    private String objectId;
    private String parentName;
    private String siteId;
    private String itemBarCode;
    private String serialNumber;
    private String model;
    private String note;
    private String part;
    private String entryUser;
    private String entryDate;
    private String itemStatus;
    private String categoryInNEP;
    private String scrapStatus;
    private String inventoryType;
    private int inventoryTypeId;
    private String locationSubType;
    private String locationClassification;
    private String itemClassification;
    private String itemClassification2;
    private String notes;
    private String prPoNo;

     
    //Getters and Setters
    public static long getSerialversionuid() {
        return serialVersionUID;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    
    public Date getRecordDateTime() {
        return recordDateTime;
    }
    public void setRecordDateTime(Date recordDateTime) {
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
    public int getInventoryTypeId() {
        return inventoryTypeId;
    }
    public void setInventoryTypeId(int inventoryTypeId) {
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

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }




    
}
