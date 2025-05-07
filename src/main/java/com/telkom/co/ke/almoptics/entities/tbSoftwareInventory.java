/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import javax.persistence.*;
import java.sql.Timestamp;

/**
 *
 * @author jgithu
 */
@Entity
@Table(name = "tb_Software_Inventory")
public class tbSoftwareInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    private Timestamp recordDateTime;

    private String softwareInventoryType;

    private String productVersion;

    private String fullVersion;

    private String fullVersionProvenance;

    private String ipAddress;

    private String hostName;

    private Integer inventoryType;

    private String changedBy;

    private Timestamp changedDate;

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

    public String getSoftwareInventoryType() {
        return softwareInventoryType;
    }

    public void setSoftwareInventoryType(String softwareInventoryType) {
        this.softwareInventoryType = softwareInventoryType;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public String getFullVersion() {
        return fullVersion;
    }

    public void setFullVersion(String fullVersion) {
        this.fullVersion = fullVersion;
    }

    public String getFullVersionProvenance() {
        return fullVersionProvenance;
    }

    public void setFullVersionProvenance(String fullVersionProvenance) {
        this.fullVersionProvenance = fullVersionProvenance;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
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
