package com.zain.ksa.alm.financials.service;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public interface AssetAllocationManagementService {
	JSONObject allocateAsset(JSONObject request);

	JSONObject approveAllocation(JSONObject request);

	JSONArray getUnapprovedAllocations(JSONObject request);
}
