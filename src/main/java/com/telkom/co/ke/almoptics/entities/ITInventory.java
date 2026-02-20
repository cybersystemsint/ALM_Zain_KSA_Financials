package com.telkom.co.ke.almoptics.entities;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Transient;

@Entity
@Table(name = "vw_IT_Inventory")
public class ITInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo; // This field will not be used as the unique identifier.
    @Transient // This field is not mapped to the database and will be generated dynamically.
    private String recordId;
    private LocalDateTime recordDateTime;
    private String siteId;
    private String firstScan;
    private String ipAddress;
    private String objectId;
    private Integer osId;
    private String osName;
    private Integer hardwareVendorId;
    private String hardwareVendorName;
    private String model;
    @Column(name = "`virtual`")
    private Boolean virtual;
    private Integer hostTypeId;
    private String hostTypeName;
    private String hostSerialNumber;

    private Integer inventoryTypeId;
    private String inventoryType;
    private String category;
    private Boolean isMapped;


   public ITInventory() {
    }

    
    public ITInventory(Integer recordNo, String recordId, LocalDateTime recordDateTime, String siteId, String firstScan,
            String ipAddress, String objectId, Integer osId, String osName, Integer hardwareVendorId,
            String hardwareVendorName, String model, Boolean virtual, Integer hostTypeId, String hostTypeName,
            String hostSerialNumber, Integer inventoryTypeId, String inventoryType, String category, Boolean isMapped) {
        this.recordNo = recordNo;
        this.recordId = recordId;
        this.recordDateTime = recordDateTime;
        this.siteId = siteId;
        this.firstScan = firstScan;
        this.ipAddress = ipAddress;
        this.objectId = objectId;
        this.osId = osId;
        this.osName = osName;
        this.hardwareVendorId = hardwareVendorId;
        this.hardwareVendorName = hardwareVendorName;
        this.model = model;
        this.virtual = virtual;
        this.hostTypeId = hostTypeId;
        this.hostTypeName = hostTypeName;
        this.hostSerialNumber = hostSerialNumber;
        this.inventoryTypeId = inventoryTypeId;
        this.inventoryType = inventoryType;
        this.category = category;
        this.isMapped = isMapped;
    }
    //Getters and Setters
    public Integer getRecordNo() {
        return recordNo;
    }
    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
    }
    public String getRecordId() {
        return recordId;
    }
    public void setRecordId(String recordId) {
        this.recordId = recordId;
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
    public String getFirstScan() {
        return firstScan;
    }
    public void setFirstScan(String firstScan) {
        this.firstScan = firstScan;
    }
    public String getIpAddress() {
        return ipAddress;
    }
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    public String getObjectId() {
        return objectId;
    }
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }
    public Integer getOsId() {
        return osId;
    }
    public void setOsId(Integer osId) {
        this.osId = osId;
    }
    public String getOsName() {
        return osName;
    }
    public void setOsName(String osName) {
        this.osName = osName;
    }
    public Integer getHardwareVendorId() {
        return hardwareVendorId;
    }
    public void setHardwareVendorId(Integer hardwareVendorId) {
        this.hardwareVendorId = hardwareVendorId;
    }
    public String getHardwareVendorName() {
        return hardwareVendorName;
    }
    public void setHardwareVendorName(String hardwareVendorName) {
        this.hardwareVendorName = hardwareVendorName;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public Boolean getVirtual() {
        return virtual;
    }
    public void setVirtual(Boolean virtual) {
        this.virtual = virtual;
    }
    public Integer getHostTypeId() {
        return hostTypeId;
    }
    public void setHostTypeId(Integer hostTypeId) {
        this.hostTypeId = hostTypeId;
    }
    public String getHostTypeName() {
        return hostTypeName;
    }
    public void setHostTypeName(String hostTypeName) {
        this.hostTypeName = hostTypeName;
    }
    public String getHostSerialNumber() {
        return hostSerialNumber;
    }
    public void setHostSerialNumber(String hostSerialNumber) {
        this.hostSerialNumber = hostSerialNumber;
    }
    public Integer getInventoryTypeId() {
        return inventoryTypeId;
    }
    public void setInventoryTypeId(Integer inventoryTypeId) {
        this.inventoryTypeId = inventoryTypeId;
    }
    public String getInventoryType() {
        return inventoryType;
    }
    public void setInventoryType(String inventoryType) {
        this.inventoryType = inventoryType;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public Boolean getIsMapped() {
        return isMapped;
    }
    public void setIsMapped(Boolean isMapped) {
        this.isMapped = isMapped;
    }


}

    
