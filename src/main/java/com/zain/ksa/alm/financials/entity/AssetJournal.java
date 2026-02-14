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
@Table(name = "`AssetJournal`", indexes = { @Index(name = "PRIMARY", columnList = "recordNo", unique = false) })
public class AssetJournal implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recordNo")
	private int recordNo;

	@Column(name = "recordDatetime")
	private Date recordDatetime;

	@Column(name = "trackingDate")
	private Date trackingDate;

	@Column(name = "assetCode")
	private String assetCode;

	@Column(name = "activity")
	private String activity;

	@Column(name = "details")
	private String details;

	@Column(name = "locationId")
	private String locationId;

	@Column(name = "activityBy")
	private String activityBy;

	public AssetJournal() {
	}

	public AssetJournal(int recordNo, Date recordDateTime, String assetCode, String activity, String locationId,
			String activityBy, String details) {
		this.recordNo = recordNo;
		this.recordDatetime = recordDateTime;
		this.assetCode = assetCode;
		this.activity = activity;
		this.details = details;
		this.locationId = locationId;
		this.activityBy = activityBy;
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

	public Date getTrackingDate() {
		return this.trackingDate;
	}

	public void setTrackingDate(Date trackingDate) {
		this.trackingDate = trackingDate;
	}

	public String getAssetCode() {
		return this.assetCode;
	}

	public void setAssetCode(String assetCode) {
		this.assetCode = assetCode;
	}

	public String getActivity() {
		return this.activity;
	}

	public void setActivity(String activity) {
		this.activity = activity;
	}

	public String getDetails() {
		return this.details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public String getLocationId() {
		return this.locationId;
	}

	public void setLocationId(String locationId) {
		this.locationId = locationId;
	}

	public String getActivityBy() {
		return this.activityBy;
	}

	public void setActivityBy(String activityBy) {
		this.activityBy = activityBy;
	}
}
