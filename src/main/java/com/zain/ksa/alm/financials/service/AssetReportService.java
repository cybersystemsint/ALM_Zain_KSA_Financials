package com.zain.ksa.alm.financials.service;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public interface AssetReportService {

	JSONArray getUnmappedActiveAssets();

	void captureAssetJournal(JSONObject request);

	JSONArray getAssetJournal();
}
