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
    name = "tb_unmapped_IT_Inventory",
    indexes = {
        @Index(name = "idx_uiti_serialNumber", columnList = "hostSerialNumber"),
        @Index(name = "idx_uiti_siteId",       columnList = "siteId")
    }
)

public class UnmappedITInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime recordDatetime;

    private String objectId;
    private String siteId;
    private String hostSerialNumber;
    private Integer inventoryTypeId;
    private String inventoryType;
    private String hostTypeName;
    private String firstScan;

    @Column(columnDefinition = "TEXT")
    private String ipAddress;

    private Integer osId;
    private String osName;
    private Integer hardwareVendorId;
    private String hardwareVendorName;
    private String model;

    @Column(name = "`isVirtual`")
    private Boolean isVirtual;

    private Integer hostTypeId;
    private String category;

    
    public UnmappedITInventory() {
    }
    public UnmappedITInventory(Long id, LocalDateTime recordDatetime, String objectId, String siteId,
            String hostSerialNumber, Integer inventoryTypeId, String inventoryType, String hostTypeName,
            String firstScan, String ipAddress, Integer osId, String osName, Integer hardwareVendorId,
            String hardwareVendorName, String model, Boolean isVirtual, Integer hostTypeId, String category) {
        this.id = id;
        this.recordDatetime = recordDatetime;
        this.objectId = objectId;
        this.siteId = siteId;
        this.hostSerialNumber = hostSerialNumber;
        this.inventoryTypeId = inventoryTypeId;
        this.inventoryType = inventoryType;
        this.hostTypeName = hostTypeName;
        this.firstScan = firstScan;
        this.ipAddress = ipAddress;
        this.osId = osId;
        this.osName = osName;
        this.hardwareVendorId = hardwareVendorId;
        this.hardwareVendorName = hardwareVendorName;
        this.model = model;
        this.isVirtual = isVirtual;
        this.hostTypeId = hostTypeId;
        this.category = category;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public LocalDateTime getRecordDatetime() {
        return recordDatetime;
    }
    public void setRecordDatetime(LocalDateTime recordDatetime) {
        this.recordDatetime = recordDatetime;
    }
    public String getObjectId() {
        return objectId;
    }
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }
    public String getSiteId() {
        return siteId;
    }
    public void setSiteId(String siteId) {
        this.siteId = siteId;
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
    public String getHostTypeName() {
        return hostTypeName;
    }
    public void setHostTypeName(String hostTypeName) {
        this.hostTypeName = hostTypeName;
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
    public Boolean getIsVirtual() {
        return isVirtual;
    }
    public void setIsVirtual(Boolean isVirtual) {
        this.isVirtual = isVirtual;
    }
    public Integer getHostTypeId() {
        return hostTypeId;
    }
    public void setHostTypeId(Integer hostTypeId) {
        this.hostTypeId = hostTypeId;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }

    

}