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
@Table(name = "`AssetDepreciation`", indexes = { @Index(name = "PRIMARY", columnList = "recordId", unique = false) })
public class AssetDepreciation implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recordId")
	private int recordId;

	@Column(name = "recordDatetime")
	private Date recordDatetime;

	@Column(name = "assetCode")
	private String assetCode;

	@Column(name = "depreciationDate")
	private String depreciationDate;

	@Column(name = "assetBookValue")
	private double assetBookValue;

	@Column(name = "accumulatedDepreciation")
	private double accumulatedDepreciation;

	public AssetDepreciation() {
	}

	public AssetDepreciation(int recordId, Date recordDatetime, String assetCode, String depreciationDate,
			double assetBookValue, double accumulatedDepreciation) {
	}

	public int getRecordId() {
		return this.recordId;
	}

	public void setRecordId(int recordId) {
		this.recordId = recordId;
	}

	public Date getRecordDatetime() {
		return this.recordDatetime;
	}

	public void setRecordDatetime(Date recordDatetime) {
		this.recordDatetime = recordDatetime;
	}

	public String getAssetCode() {
		return this.assetCode;
	}

	public void setAssetCode(String assetCode) {
		this.assetCode = assetCode;
	}

	public String getDepreciationDate() {
		return this.depreciationDate;
	}

	public void setDepreciationDate(String depreciationDate) {
		this.depreciationDate = depreciationDate;
	}

	public double getAssetBookValue() {
		return this.assetBookValue;
	}

	public void setAssetBookValue(double assetBookValue) {
		this.assetBookValue = assetBookValue;
	}

	public double getAccumulatedDepreciation() {
		return accumulatedDepreciation;
	}

	public void setAccumulatedDepreciation(double accumulatedDepreciation) {
		this.accumulatedDepreciation = accumulatedDepreciation;
	}

}
