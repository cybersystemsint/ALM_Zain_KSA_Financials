package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;


@Entity
@Table(name = "tb_unmapped_active_inventory")
public class UnmappedActiveInventory implements Serializable {
private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;
    @Transient
   private int rowNumber;
   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Column(nullable = false)
    private Timestamp recordDateTime;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    @Column(nullable = false)
    private String serialNumber;
    private String model;
    private String partNumber;
    private String siteId;
    private String manufacturer;
    private String description;
   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date manufacturingDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date installationDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date assetInsertionDate;


    //Getters and Setters

    public static long getSerialversionuid() {
        return serialVersionUID;
    }


    public Integer getRecordNo() {
        return recordNo;
    }


    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
    }
public int getRowNumber() {
    return rowNumber;
}

public void setRowNumber(int rowNumber) {
    this.rowNumber = rowNumber;
}

    public Timestamp getRecordDateTime() {
        return recordDateTime;
    }


    public void setRecordDateTime(Timestamp recordDateTime) {
        this.recordDateTime = recordDateTime;
    }


    public String getNodeId() {
        return nodeId;
    }


    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }


    public String getNodeName() {
        return nodeName;
    }


    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }


    public String getNodeType() {
        return nodeType;
    }


    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
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


    public String getPartNumber() {
        return partNumber;
    }


    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }


    public String getSiteId() {
        return siteId;
    }


    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }


    public String getManufacturer() {
        return manufacturer;
    }


    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }


    public String getDescription() {
        return description;
    }


    public void setDescription(String description) {
        this.description = description;
    }


    public Date getManufacturingDate() {
        return manufacturingDate;
    }


    public void setManufacturingDate(Date manufacturingDate) {
        this.manufacturingDate = manufacturingDate;
    }


    public Date getInstallationDate() {
        return installationDate;
    }


    public void setInstallationDate(Date installationDate) {
        this.installationDate = installationDate;
    }


    public Date getAssetInsertionDate() {
        return assetInsertionDate;
    }


    public void setAssetInsertionDate(Date assetInsertionDate) {
        this.assetInsertionDate = assetInsertionDate;
    }


  
}
