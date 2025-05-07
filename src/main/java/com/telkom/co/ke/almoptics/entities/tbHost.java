/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.entities;

/**
 *
 * @author jgithu
 */
import java.io.Serializable;
import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "tb_Host")
public class tbHost implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    private Timestamp recordDateTime;

    private String hostId;

    private Integer siteId;

    private Integer floorId;

    private String firstScan;

    private String lastUpdateSuccess;

    private String ipAddress;

    private String objectId;

    private Integer osId;

    private Integer hardwareVendorId;

    private String model;

    private Boolean virtual;

    private Integer hostTypeId;

    private String logicalRam;

    private String physicalRam;

    private String processorType;

    private String processorVendor;

    private Integer logicalProcessors;

    private Integer numberOfCores;

    private String uuid;

    private Integer coresPerProcessor;

    private String hostSerialNumber;

    private String macAddress;

    private String networkInterfaceName;

    private String hbaIdentifier;

    private String diskDriveType;

    private String diskDrives;

    private String diskDriveModels;

    private String diskDriveSerialNumbers;

    private String diskDriveSize;

    private String hardwareName;

    private String hardwareModel;

    private String hardwareSerialNumber;

    private String freePercentage;

    private String size;

    private String usedPercentage;

    private String esxiName;

    private String skuNumber;

    private String memoryPartNumber;

    private String hostContainerName;

    private String domain;

    private String supplier;

    private String warranty;

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

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public Integer getSiteId() {
        return siteId;
    }

    public void setSiteId(Integer siteId) {
        this.siteId = siteId;
    }

    public Integer getFloorId() {
        return floorId;
    }

    public void setFloorId(Integer floorId) {
        this.floorId = floorId;
    }

    public String getFirstScan() {
        return firstScan;
    }

    public void setFirstScan(String firstScan) {
        this.firstScan = firstScan;
    }

    public String getLastUpdateSuccess() {
        return lastUpdateSuccess;
    }

    public void setLastUpdateSuccess(String lastUpdateSuccess) {
        this.lastUpdateSuccess = lastUpdateSuccess;
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

    public String getLogicalRam() {
        return logicalRam;
    }

    public void setLogicalRam(String logicalRam) {
        this.logicalRam = logicalRam;
    }

    public String getPhysicalRam() {
        return physicalRam;
    }

    public void setPhysicalRam(String physicalRam) {
        this.physicalRam = physicalRam;
    }

    public String getProcessorType() {
        return processorType;
    }

    public void setProcessorType(String processorType) {
        this.processorType = processorType;
    }

    public String getProcessorVendor() {
        return processorVendor;
    }

    public void setProcessorVendor(String processorVendor) {
        this.processorVendor = processorVendor;
    }

    public Integer getLogicalProcessors() {
        return logicalProcessors;
    }

    public void setLogicalProcessors(Integer logicalProcessors) {
        this.logicalProcessors = logicalProcessors;
    }

    public Integer getNumberOfCores() {
        return numberOfCores;
    }

    public void setNumberOfCores(Integer numberOfCores) {
        this.numberOfCores = numberOfCores;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Integer getCoresPerProcessor() {
        return coresPerProcessor;
    }

    public void setCoresPerProcessor(Integer coresPerProcessor) {
        this.coresPerProcessor = coresPerProcessor;
    }

    public String getHostSerialNumber() {
        return hostSerialNumber;
    }

    public void setHostSerialNumber(String hostSerialNumber) {
        this.hostSerialNumber = hostSerialNumber;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getNetworkInterfaceName() {
        return networkInterfaceName;
    }

    public void setNetworkInterfaceName(String networkInterfaceName) {
        this.networkInterfaceName = networkInterfaceName;
    }

    public String getHbaIdentifier() {
        return hbaIdentifier;
    }

    public void setHbaIdentifier(String hbaIdentifier) {
        this.hbaIdentifier = hbaIdentifier;
    }

    public String getDiskDriveType() {
        return diskDriveType;
    }

    public void setDiskDriveType(String diskDriveType) {
        this.diskDriveType = diskDriveType;
    }

    public String getDiskDrives() {
        return diskDrives;
    }

    public void setDiskDrives(String diskDrives) {
        this.diskDrives = diskDrives;
    }

    public String getDiskDriveModels() {
        return diskDriveModels;
    }

    public void setDiskDriveModels(String diskDriveModels) {
        this.diskDriveModels = diskDriveModels;
    }

    public String getDiskDriveSerialNumbers() {
        return diskDriveSerialNumbers;
    }

    public void setDiskDriveSerialNumbers(String diskDriveSerialNumbers) {
        this.diskDriveSerialNumbers = diskDriveSerialNumbers;
    }

    public String getDiskDriveSize() {
        return diskDriveSize;
    }

    public void setDiskDriveSize(String diskDriveSize) {
        this.diskDriveSize = diskDriveSize;
    }

    public String getHardwareName() {
        return hardwareName;
    }

    public void setHardwareName(String hardwareName) {
        this.hardwareName = hardwareName;
    }

    public String getHardwareModel() {
        return hardwareModel;
    }

    public void setHardwareModel(String hardwareModel) {
        this.hardwareModel = hardwareModel;
    }

    public String getHardwareSerialNumber() {
        return hardwareSerialNumber;
    }

    public void setHardwareSerialNumber(String hardwareSerialNumber) {
        this.hardwareSerialNumber = hardwareSerialNumber;
    }

    public String getFreePercentage() {
        return freePercentage;
    }

    public void setFreePercentage(String freePercentage) {
        this.freePercentage = freePercentage;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getUsedPercentage() {
        return usedPercentage;
    }

    public void setUsedPercentage(String usedPercentage) {
        this.usedPercentage = usedPercentage;
    }

    public String getEsxiName() {
        return esxiName;
    }

    public void setEsxiName(String esxiName) {
        this.esxiName = esxiName;
    }

    public String getSkuNumber() {
        return skuNumber;
    }

    public void setSkuNumber(String skuNumber) {
        this.skuNumber = skuNumber;
    }

    public String getMemoryPartNumber() {
        return memoryPartNumber;
    }

    public void setMemoryPartNumber(String memoryPartNumber) {
        this.memoryPartNumber = memoryPartNumber;
    }

    public String getHostContainerName() {
        return hostContainerName;
    }

    public void setHostContainerName(String hostContainerName) {
        this.hostContainerName = hostContainerName;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public String getWarranty() {
        return warranty;
    }

    public void setWarranty(String warranty) {
        this.warranty = warranty;
    }

    public Integer getInventoryType() {
        return inventoryType;
    }

    public void setInventoryType(Integer inventoryType) {
        this.inventoryType = inventoryType;
    }

}
