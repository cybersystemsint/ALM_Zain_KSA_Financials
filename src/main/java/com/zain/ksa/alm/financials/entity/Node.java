package com.zain.ksa.alm.financials.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;


@Entity
@Table(
    name = "tb_Node",
    indexes = {
        @Index(name = "idx_node_serialNumber", columnList = "serialNumber"),
        @Index(name = "idx_node_siteId",       columnList = "siteId"),
        @Index(name = "idx_node_isMapped",     columnList = "isMapped")
    }
)
public class Node implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // DB: timestamp DEFAULT CURRENT_TIMESTAMP
    @Column(name = "recordDateTime")
    private LocalDateTime recordDateTime;

    // DB column name is "node" — mapped to nodeName field
    @Column(name = "node")
    private String nodeName;

    private Integer siteId;

    // Extra columns visible in DB screenshot
    private String latitude;
    private String longitude;

    private String technologySupported;
    private Integer nodeTypeId;
    private Integer manufacturerId;

    @Column(name = "networkElement", length = 100)
    private String networkElement;

    private String partNumber;
    private String model;
    private String inventoryFlag;
    private String serialNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    // DB type is "date" — java.util.Date maps cleanly
    @Temporal(TemporalType.DATE)
    private Date manufacturingDate;

    private String issueNumber;

    @Column(name = "insertDate")
    private LocalDateTime insertDate;

    @Column(name = "updateDate")
    private LocalDateTime updateDate;

    // DB column inventoryType is int (column 20 in screenshot)
    @Column(name = "inventoryType")
    private Integer inventoryType;

    private String changedBy;
    private Date changedDate;

    // DB: tinyint(1)
    private Boolean isMapped;

    public Node() {}

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public LocalDateTime getRecordDateTime() { return recordDateTime; }
    public void setRecordDateTime(LocalDateTime recordDateTime) { this.recordDateTime = recordDateTime; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public Integer getSiteId() { return siteId; }
    public void setSiteId(Integer siteId) { this.siteId = siteId; }

    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }

    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }

    public String getTechnologySupported() { return technologySupported; }
    public void setTechnologySupported(String technologySupported) { this.technologySupported = technologySupported; }

    public Integer getNodeTypeId() { return nodeTypeId; }
    public void setNodeTypeId(Integer nodeTypeId) { this.nodeTypeId = nodeTypeId; }

    public Integer getManufacturerId() { return manufacturerId; }
    public void setManufacturerId(Integer manufacturerId) { this.manufacturerId = manufacturerId; }

    public String getNetworkElement() { return networkElement; }
    public void setNetworkElement(String networkElement) { this.networkElement = networkElement; }

    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getInventoryFlag() { return inventoryFlag; }
    public void setInventoryFlag(String inventoryFlag) { this.inventoryFlag = inventoryFlag; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(Date manufacturingDate) { this.manufacturingDate = manufacturingDate; }

    public String getIssueNumber() { return issueNumber; }
    public void setIssueNumber(String issueNumber) { this.issueNumber = issueNumber; }

    public LocalDateTime getInsertDate() { return insertDate; }
    public void setInsertDate(LocalDateTime insertDate) { this.insertDate = insertDate; }

    public LocalDateTime getUpdateDate() { return updateDate; }
    public void setUpdateDate(LocalDateTime updateDate) { this.updateDate = updateDate; }

    public Integer getInventoryType() { return inventoryType; }
    public void setInventoryType(Integer inventoryType) { this.inventoryType = inventoryType; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public Date getChangedDate() { return changedDate; }
    public void setChangedDate(Date changedDate) { this.changedDate = changedDate; }

    public Boolean getIsMapped() { return isMapped; }
    public void setIsMapped(Boolean isMapped) { this.isMapped = isMapped; }
}