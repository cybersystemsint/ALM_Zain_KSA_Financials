/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.*;

/**
 *
 * @author jgithu
 */
@Entity
@Table(name = "tb_Storage")
public class tbStorage implements Serializable {
    
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    private Timestamp recordDateTime;

    private Integer siteId;

    private String firstScan;

  
    private String ipAddress;

  
    private String objectId;

       private Integer osId;

  
    private Integer hardwareVendorId;

 
    private String model;

 
    private Boolean virtual;

 
    private Integer hostTypeId;


    private String hostSerialNumber;

  
    private String diskDriveType;

   
    private String diskDriveModel;

    private String diskDriveSerialNumber;

    private String diskDriveSize;

    private String lastUpdateSuccess;

    private Integer inventoryType;

    private String changedBy;

    private Timestamp changedDate;
    
      
    private Boolean isMapped;

    public Boolean getIsMapped() {
        return isMapped;
    }

    public void setIsMapped(Boolean isMapped) {
        this.isMapped = isMapped;
    }


    public Integer getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
    }

    public Timestamp getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(Timestamp recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    public Integer getSiteId() {
        return siteId;
    }

    public void setSiteId(Integer siteId) {
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

    public Integer getHardwareVendorId() {
        return hardwareVendorId;
    }

    public void setHardwareVendorId(Integer hardwareVendorId) {
        this.hardwareVendorId = hardwareVendorId;
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

    public String getHostSerialNumber() {
        return hostSerialNumber;
    }

    public void setHostSerialNumber(String hostSerialNumber) {
        this.hostSerialNumber = hostSerialNumber;
    }

    public String getDiskDriveType() {
        return diskDriveType;
    }

    public void setDiskDriveType(String diskDriveType) {
        this.diskDriveType = diskDriveType;
    }

    public String getDiskDriveModel() {
        return diskDriveModel;
    }

    public void setDiskDriveModel(String diskDriveModel) {
        this.diskDriveModel = diskDriveModel;
    }

    public String getDiskDriveSerialNumber() {
        return diskDriveSerialNumber;
    }

    public void setDiskDriveSerialNumber(String diskDriveSerialNumber) {
        this.diskDriveSerialNumber = diskDriveSerialNumber;
    }

    public String getDiskDriveSize() {
        return diskDriveSize;
    }

    public void setDiskDriveSize(String diskDriveSize) {
        this.diskDriveSize = diskDriveSize;
    }

    public String getLastUpdateSuccess() {
        return lastUpdateSuccess;
    }

    public void setLastUpdateSuccess(String lastUpdateSuccess) {
        this.lastUpdateSuccess = lastUpdateSuccess;
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

    public Timestamp getChangedDate() {
        return changedDate;
    }

    public void setChangedDate(Timestamp changedDate) {
        this.changedDate = changedDate;
    }
    
}
