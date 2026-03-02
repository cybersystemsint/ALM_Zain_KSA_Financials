package com.zain.ksa.alm.financials.entity;


import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "vw_IT_Inventory")
public class ITInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private Long recordNo;

    @Column(name = "recordDateTime", nullable = false)
    private LocalDateTime recordDateTime;

    @Column(name = "siteId", length = 50)
    private String siteId;

    @Column(name = "firstScan", length = 255)
    private String firstScan;

    @Column(name = "IPAddress", columnDefinition = "MEDIUMTEXT")
    private String ipAddress;

    @Column(name = "objectId", length = 255)
    private String objectId;

    // In the VIEW this is varchar(50) — keep as String
    @Column(name = "OSId", length = 50)
    private String osId;

    @Column(name = "osName", length = 255)
    private String osName;

    @Column(name = "hardwareVendorId")
    private Integer hardwareVendorId;

    @Column(name = "hardwareVendorName", length = 255)
    private String hardwareVendorName;

    @Column(name = "model", length = 255)
    private String model;

    // In the VIEW this is decimal(10,0) — map to BigDecimal or Integer
    @Column(name = "virtual", precision = 10, scale = 0)
    private BigDecimal virtual;

    @Column(name = "hostTypeId")
    private Integer hostTypeId;

    @Column(name = "hostTypeName", length = 255)
    private String hostTypeName;

    @Column(name = "hostSerialNumber", length = 255)
    private String hostSerialNumber;

    @Column(name = "inventoryTypeId")
    private Integer inventoryTypeId;

    @Column(name = "inventoryType", length = 12, nullable = false)
    private String inventoryType;

    @Column(name = "category", length = 17, nullable = false)
    private String category;

    @Column(name = "isMapped")
    private Long isMapped;

    public ITInventory() {}

    public Long getRecordNo() { return recordNo; }
    public void setRecordNo(Long recordNo) { this.recordNo = recordNo; }

    public LocalDateTime getRecordDateTime() { return recordDateTime; }
    public void setRecordDateTime(LocalDateTime recordDateTime) { this.recordDateTime = recordDateTime; }

    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }

    public String getFirstScan() { return firstScan; }
    public void setFirstScan(String firstScan) { this.firstScan = firstScan; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public String getOsId() { return osId; }
    public void setOsId(String osId) { this.osId = osId; }

    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }

    public Integer getHardwareVendorId() { return hardwareVendorId; }
    public void setHardwareVendorId(Integer hardwareVendorId) { this.hardwareVendorId = hardwareVendorId; }

    public String getHardwareVendorName() { return hardwareVendorName; }
    public void setHardwareVendorName(String hardwareVendorName) { this.hardwareVendorName = hardwareVendorName; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public BigDecimal getVirtual() { return virtual; }
    public void setVirtual(BigDecimal virtual) { this.virtual = virtual; }

    public Integer getHostTypeId() { return hostTypeId; }
    public void setHostTypeId(Integer hostTypeId) { this.hostTypeId = hostTypeId; }

    public String getHostTypeName() { return hostTypeName; }
    public void setHostTypeName(String hostTypeName) { this.hostTypeName = hostTypeName; }

    public String getHostSerialNumber() { return hostSerialNumber; }
    public void setHostSerialNumber(String hostSerialNumber) { this.hostSerialNumber = hostSerialNumber; }

    public Integer getInventoryTypeId() { return inventoryTypeId; }
    public void setInventoryTypeId(Integer inventoryTypeId) { this.inventoryTypeId = inventoryTypeId; }

    public String getInventoryType() { return inventoryType; }
    public void setInventoryType(String inventoryType) { this.inventoryType = inventoryType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Long getIsMapped() { return isMapped; }
    public void setIsMapped(Long isMapped) { this.isMapped = isMapped; }
}