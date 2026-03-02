package com.zain.ksa.alm.financials.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(
    name = "tb_unmapped_active_inventory",
    indexes = {
        @Index(name = "idx_uai_serialNumber", columnList = "serialNumber"),
        @Index(name = "idx_uai_siteId",       columnList = "siteId"),
        @Index(name = "idx_uai_recordDateTime", columnList = "recordDateTime")
    }
)

public class UnmappedActiveInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordNo;

    @Column(nullable = false)
    private LocalDateTime recordDateTime;

    private String nodeId;
    private String nodeName;
    private String nodeType;

    @Column(nullable = false)
    private String serialNumber;

    private String model;
    private String partNumber;
    private String siteId;
    private String manufacturer;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDate manufacturingDate;
    private LocalDate installationDate;
    private LocalDate assetInsertionDate;
    private String warranty;

    
    public UnmappedActiveInventory() {
    }
    public UnmappedActiveInventory(Long recordNo, LocalDateTime recordDateTime, String nodeId, String nodeName,
            String nodeType, String serialNumber, String model, String partNumber, String siteId, String manufacturer,
            String description, LocalDate manufacturingDate, LocalDate installationDate, LocalDate assetInsertionDate,
            String warranty) {
        this.recordNo = recordNo;
        this.recordDateTime = recordDateTime;
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.serialNumber = serialNumber;
        this.model = model;
        this.partNumber = partNumber;
        this.siteId = siteId;
        this.manufacturer = manufacturer;
        this.description = description;
        this.manufacturingDate = manufacturingDate;
        this.installationDate = installationDate;
        this.assetInsertionDate = assetInsertionDate;
        this.warranty = warranty;
    }
    public Long getRecordNo() {
        return recordNo;
    }
    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
    }
    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }
    public void setRecordDateTime(LocalDateTime recordDateTime) {
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
    public LocalDate getManufacturingDate() {
        return manufacturingDate;
    }
    public void setManufacturingDate(LocalDate manufacturingDate) {
        this.manufacturingDate = manufacturingDate;
    }
    public LocalDate getInstallationDate() {
        return installationDate;
    }
    public void setInstallationDate(LocalDate installationDate) {
        this.installationDate = installationDate;
    }
    public LocalDate getAssetInsertionDate() {
        return assetInsertionDate;
    }
    public void setAssetInsertionDate(LocalDate assetInsertionDate) {
        this.assetInsertionDate = assetInsertionDate;
    }
    public String getWarranty() {
        return warranty;
    }
    public void setWarranty(String warranty) {
        this.warranty = warranty;
    }

    
}