package com.telkom.co.ke.almoptics.entities;
import com.fasterxml.jackson.annotation.JsonFormat;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Transient;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "tb_unmapped_IT_Inventory")
public class UnmappedITInventory implements Serializable {

     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recordDatetime", updatable = false, nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @CreationTimestamp
    private LocalDateTime recordDatetime;
    @Transient
     private int rowNumber;
    private String objectId;
    private String siteId;
    private String hostSerialNumber;
    private Integer inventoryTypeId;
    private String inventoryType;
    private String hostTypeName;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private String firstScan;
    @Column(columnDefinition = "TEXT")
    private String ipAddress;
    private Integer osId;
    private String osName;
    private Integer hardwareVendorId;
    private String hardwareVendorName;
    private String model;
    @Column(name = "isVirtual")
    private Integer virtual;
    private Integer hostTypeId;
    private String category;

    
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
    public int getRowNumber() {
    return rowNumber;
}

public void setRowNumber(int rowNumber) {
    this.rowNumber = rowNumber;
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

    public Integer getVirtual() {
        return virtual;
    }
    public void setVirtual(Integer virtual) {
        this.virtual = virtual;
    }




    
}
