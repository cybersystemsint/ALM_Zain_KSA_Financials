/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author jgithu
 */
@Entity
@Table(name = "tb_AssetTracking")
public class tbAssetTracking implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordNo;

    private Date recordDatetime;

    @Column(name = "serialNumber")
    private String serialNumber;

    @Column(name = "rfid")
    private String rfid;

    @Column(name = "tagNumber")
    private String tagNumber;

    @Column(name = "siteId")
    private String siteId;

    @Column(name = "neStatus")
    private String neStatus;

    @Column(name = "changeDate")
    private Date changeDate;

    @Column(name = "username")
    private String username;

    @Column(name = "actionType")
    private String actionType;

    @Column(name = "model")
    private String model;

    @Column(name = "node")
    private String node;

    @Column(name = "nodeType")
    private String nodeType;

    @Column(name = "oldSite")
    private String oldSite;

    @Column(name = "portHolder")
    private String portHolder;

    @Column(name = "slotHolder")
    private String slotHolder;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "wareHouseSourceId")
    private String wareHouseSourceId;

    @Column(name = "wareHouseSourceName", length = 255)
    private String wareHouseSourceName;

    @Column(name = "wareHouseDestinationId")
    private String wareHouseDestinationId;

    @Column(name = "wareHouseDestinationName", length = 255)
    private String wareHouseDestinationName;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "manufacturerDate")
    private Date manufacturerDate;

    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
    }

    public Date getRecordDatetime() {
        return recordDatetime;
    }

    public void setRecordDatetime(Date recordDatetime) {
        this.recordDatetime = recordDatetime;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getRfid() {
        return rfid;
    }

    public void setRfid(String rfid) {
        this.rfid = rfid;
    }

    public String getTagNumber() {
        return tagNumber;
    }

    public void setTagNumber(String tagNumber) {
        this.tagNumber = tagNumber;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getNeStatus() {
        return neStatus;
    }

    public void setNeStatus(String neStatus) {
        this.neStatus = neStatus;
    }

    public Date getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(Date changeDate) {
        this.changeDate = changeDate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getOldSite() {
        return oldSite;
    }

    public void setOldSite(String oldSite) {
        this.oldSite = oldSite;
    }

    public String getPortHolder() {
        return portHolder;
    }

    public void setPortHolder(String portHolder) {
        this.portHolder = portHolder;
    }

    public String getSlotHolder() {
        return slotHolder;
    }

    public void setSlotHolder(String slotHolder) {
        this.slotHolder = slotHolder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWareHouseSourceId() {
        return wareHouseSourceId;
    }

    public void setWareHouseSourceId(String wareHouseSourceId) {
        this.wareHouseSourceId = wareHouseSourceId;
    }

    public String getWareHouseSourceName() {
        return wareHouseSourceName;
    }

    public void setWareHouseSourceName(String wareHouseSourceName) {
        this.wareHouseSourceName = wareHouseSourceName;
    }

    public String getWareHouseDestinationId() {
        return wareHouseDestinationId;
    }

    public void setWareHouseDestinationId(String wareHouseDestinationId) {
        this.wareHouseDestinationId = wareHouseDestinationId;
    }

    public String getWareHouseDestinationName() {
        return wareHouseDestinationName;
    }

    public void setWareHouseDestinationName(String wareHouseDestinationName) {
        this.wareHouseDestinationName = wareHouseDestinationName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Date getManufacturerDate() {
        return manufacturerDate;
    }

    public void setManufacturerDate(Date manufacturerDate) {
        this.manufacturerDate = manufacturerDate;
    }

}
