package com.zain.ksa.alm.financials.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.constant.AppConstants;
import com.zain.ksa.alm.financials.entity.AssetAllocation;
import com.zain.ksa.alm.financials.entity.Item;
import com.zain.ksa.alm.financials.service.AssetAllocationManagementService;
import com.zain.ksa.alm.financials.service.AssetAllocationService;
import com.zain.ksa.alm.financials.service.ItemService;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

@Service
public class AssetAllocationManagementServiceImpl implements AssetAllocationManagementService {

	private static final Logger LOGGER = LogManager.getLogger(AssetAllocationManagementServiceImpl.class);

	private final AssetAllocationService assetAllocationService;
	private final ItemService itemService;

	@Autowired
	public AssetAllocationManagementServiceImpl(AssetAllocationService assetAllocationService,
			ItemService itemService) {
		this.assetAllocationService = assetAllocationService;
		this.itemService = itemService;
	}

	@Override
	public JSONObject allocateAsset(JSONObject assetRequest) {
		JSONObject jsonObjectResponse = new JSONObject();
		try {
			LOGGER.info("RECEIVE ALLOCATE ASSETS REQUEST: {}", assetRequest);

			String assetCode = assetRequest.getAsString("assetCode");
			String locationId = assetRequest.getAsString("locationId");
			String personId = assetRequest.getAsString("personId");
			String status = assetRequest.getAsString("status");
			String details = assetRequest.getAsString("details");

			AssetAllocation allocation = new AssetAllocation();
			allocation.setAllocationDate(new Date());
			allocation.setAssetCode(assetCode);
			allocation.setDetails(details);
			allocation.setLocationId(locationId);
			allocation.setPersonId(personId);
			allocation.setStatus(status);
			allocation.setRecordDatetime(new Date());

			assetAllocationService.save(allocation);

			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS_ALT);
			jsonObjectResponse.put("responseMessage", "Asset Allocated Successfully");
		} catch (Exception ex) {
			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
			jsonObjectResponse.put("responseMessage", "Asset Allocation Failed");
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public JSONObject approveAllocation(JSONObject assetRequest) {
		JSONObject jsonObjectResponse = new JSONObject();
		try {
			LOGGER.info("GET ASSET ALLOCATION APPROVAL REQUEST: {}", assetRequest);

			String locationId = assetRequest.getAsString("locationId");
			String personId = assetRequest.getAsString("personId");
			String status = assetRequest.getAsString("status");

			AssetAllocation allocation;
			if (locationId != null && !locationId.isEmpty()) {
				allocation = assetAllocationService.findByLocationId(locationId);
			} else if (personId != null && !personId.isEmpty()) {
				allocation = assetAllocationService.findByPersonId(personId);
			} else {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
				jsonObjectResponse.put("responseMessage", "Either locationId or personId is required");
				return jsonObjectResponse;
			}

			if (allocation == null) {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
				jsonObjectResponse.put("responseMessage", "Asset allocation not found");
				return jsonObjectResponse;
			}

			allocation.setStatus(status);
			assetAllocationService.save(allocation);

			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS_ALT);
			jsonObjectResponse.put("responseMessage", "Asset Allocation Approved Successfully");
		} catch (Exception ex) {
			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
			jsonObjectResponse.put("responseMessage", "Asset Allocation Approval Failed");
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public JSONArray getUnapprovedAllocations(JSONObject request) {
		JSONArray jsonObjectResponse = new JSONArray();
		try {
			String status = request.getAsString("status");
			LOGGER.info("GET UNAPPROVED ALLOCATIONS - status: {}", status);
			List<AssetAllocation> allocations = assetAllocationService.findBystatus(status);

			for (AssetAllocation allocation : allocations) {
				JSONObject singleAssetObj = new JSONObject();
				Item item = itemService.findByItemCode(allocation.getAssetCode());

				singleAssetObj.put("assetCode", allocation.getAssetCode());
				singleAssetObj.put("locationId", allocation.getLocationId());
				singleAssetObj.put("personId", allocation.getPersonId());
				singleAssetObj.put("details", allocation.getDetails());
				singleAssetObj.put("depreciationMethod", item != null ? item.getDepreciationMethod() : "Not Set");

				jsonObjectResponse.add(singleAssetObj);
			}
		} catch (Exception ex) {
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}
}
