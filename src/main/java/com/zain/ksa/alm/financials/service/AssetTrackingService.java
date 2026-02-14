package com.zain.ksa.alm.financials.service;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import com.zain.ksa.alm.financials.entity.AssetTracking;

import net.minidev.json.JSONObject;

public interface AssetTrackingService {

	List<AssetTracking> findAll();

	List<AssetTracking> findBySerialNumber(String serialNumber);

	List<AssetTracking> findBySiteId(String siteId);

	AssetTracking save(AssetTracking paramtb_FinancialReport);

	JSONObject addAssetTracking(String req) throws ParseException;

	Map<String, Object> getAssetTracking(JSONObject assetRequest);
}
