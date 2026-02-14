package com.zain.ksa.alm.financials.service;

import net.minidev.json.JSONObject;

public interface AssetMaintenanceService {
	JSONObject updateWarrantyDetails(JSONObject request);

	JSONObject updateDepreciationDetails(JSONObject request);

	JSONObject getAssetDepreciation(JSONObject request);

	JSONObject disposeAsset(JSONObject request);
}
