package com.zain.ksa.alm.financials.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

@Entity
@Table(name = "`AssetAllocation`", indexes = { @Index(name = "PRIMARY", columnList = "recordNo", unique = false) })
public class AssetAllocation implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recordNo")
	private int recordNo;

	@Column(name = "recordDatetime")
	private Date recordDatetime;

	@Column(name = "allocationDate")
	private Date allocationDate;

	@Column(name = "assetCode")
	private String assetCode;

	@Column(name = "locationId")
	private String locationId;

	@Column(name = "personId")
	private String personId;

	@Column(name = "status")
	private String status;

	@Column(name = "details")
	private String details;

	public AssetAllocation() {
	}

	public AssetAllocation(int recordNo, Date recordDatetime, Date allocationDate, String assetCode, String locationId,
			String personId, String status, String details) {
	}

	public int getRecordNo() {
		return this.recordNo;
	}

	public void setRecordNo(int recordNo) {
		this.recordNo = recordNo;
	}

	public Date getRecordDatetime() {
		return this.recordDatetime;
	}

	public void setRecordDatetime(Date recordDatetime) {
		this.recordDatetime = recordDatetime;
	}

	public Date getAllocationDate() {
		return this.allocationDate;
	}

	public void setAllocationDate(Date allocationDate) {
		this.allocationDate = allocationDate;
	}

	public String getAssetCode() {
		return this.assetCode;
	}

	public void setAssetCode(String assetCode) {
		this.assetCode = assetCode;
	}

	public String getLocationId() {
		return this.locationId;
	}

	public void setLocationId(String locationId) {
		this.locationId = locationId;
	}

	public String getPersonId() {
		return this.personId;
	}

	public void setPersonId(String personId) {
		this.personId = personId;
	}

	public String getStatus() {
		return this.status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDetails() {
		return this.details;
	}

	public void setDetails(String details) {
		this.details = details;
	}
}
