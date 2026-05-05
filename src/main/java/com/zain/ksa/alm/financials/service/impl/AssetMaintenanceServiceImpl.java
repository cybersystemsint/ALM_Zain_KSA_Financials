package com.zain.ksa.alm.financials.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.constant.AppConstants;
import com.zain.ksa.alm.financials.entity.Asset;
import com.zain.ksa.alm.financials.entity.AssetAllocation;
import com.zain.ksa.alm.financials.service.AssetAllocationService;
import com.zain.ksa.alm.financials.service.AssetMaintenanceService;
import com.zain.ksa.alm.financials.service.AssetService;
import com.zain.ksa.alm.financials.util.AsyncHttpClient;

import net.minidev.json.JSONObject;

@Service
public class AssetMaintenanceServiceImpl implements AssetMaintenanceService {

	private static final Logger LOGGER = LogManager.getLogger(AssetMaintenanceServiceImpl.class);

	private static final double REDUCING_BALANCE_RATE = 0.25;

	private final AssetService assetService;
	private final AssetAllocationService assetAllocationService;
	private final AsyncHttpClient asyncHttpClient;

	@Autowired
	public AssetMaintenanceServiceImpl(AssetService assetService, AssetAllocationService assetAllocationService,
			AsyncHttpClient asyncHttpClient) {
		this.assetService = assetService;
		this.assetAllocationService = assetAllocationService;
		this.asyncHttpClient = asyncHttpClient;
	}

	@Override
	public JSONObject updateWarrantyDetails(JSONObject assetRequest) {
		JSONObject jsonObjectResponse = new JSONObject();
		try {
			String assetCode = assetRequest.getAsString("assetCode");
			Asset asset = assetService.findByAssetCode(assetCode);

			if (asset == null) {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
				jsonObjectResponse.put("responseMessage", "Asset not found");
				return jsonObjectResponse;
			}

			String warrantyDetails = assetRequest.getAsString("warrantyDetails");
			String warrantExpiryDate = assetRequest.getAsString("warrantExpiryDate");

			asset.setWarrantExpiryDate(warrantExpiryDate);
			asset.setWarrantyDetails(warrantyDetails);
			assetService.save(asset);

			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS_ALT);
			jsonObjectResponse.put("responseMessage", "Asset Warrant Updated Successfully");
		} catch (Exception ex) {
			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
			jsonObjectResponse.put("responseMessage", ex.getMessage());
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public JSONObject updateDepreciationDetails(JSONObject assetRequest) {
		JSONObject jsonObjectResponse = new JSONObject();
		try {
			String assetCode = assetRequest.getAsString("assetCode");
			Asset asset = assetService.findByAssetCode(assetCode);

			if (asset == null) {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
				jsonObjectResponse.put("responseMessage", "Asset not found");
				return jsonObjectResponse;
			}

			String depreciationModel = assetRequest.getAsString("depreciationModel");
			String salvageValue = assetRequest.getAsString("salvageValue");
			String usefulLife = assetRequest.getAsString("usefulLife");

			asset.setDepreciationModel(depreciationModel);
			asset.setSalvageValue(salvageValue);
			asset.setUsefulLife(usefulLife);
			assetService.save(asset);

			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS_ALT);
			jsonObjectResponse.put("responseMessage", "Asset Depreciation Updated Successfully");
		} catch (Exception ex) {
			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
			jsonObjectResponse.put("responseMessage", ex.getMessage());
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public JSONObject getAssetDepreciation(JSONObject request) {
		JSONObject resObject = new JSONObject();
		try {
			String assetCode = request.getAsString("assetCode");
			LOGGER.info("GET ASSET DEPRECIATION - assetCode: {}", assetCode);

			Asset asset = assetService.findByAssetCode(assetCode);
			if (asset == null) {
				resObject.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
				resObject.put("responseMessage", "Asset not found");
				return resObject;
			}

			double initialCost = asset.getPurchasePrice();
			String salvageValue = asset.getSalvageValue();

			LOGGER.info("salvageValue: {}", salvageValue);
			LOGGER.info("initialCost: {}", initialCost);

			int currentYear = Calendar.getInstance().get(Calendar.YEAR);
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			LocalDate ld = LocalDate.parse(asset.getPurchaseDate().toString().substring(0, 10), formatter);
			int yearsLived = currentYear - ld.getYear();

			LOGGER.info("usefulLife: {}", yearsLived);

			double totalDepreciation;
			if (asset.getDepreciationModel().equalsIgnoreCase("straightline")) {
				totalDepreciation = calculateTotalDepreciation(initialCost, Double.parseDouble(salvageValue),
						Integer.parseInt(asset.getUsefulLife()), yearsLived);
			} else {
				totalDepreciation = calculateReducingBalanceDepreciation(initialCost, yearsLived);
			}

			double bookValue = initialCost - totalDepreciation;

			LOGGER.info("BOOKVALUE: {}", bookValue);
			LOGGER.info("totalDepreciation: {}", totalDepreciation);

			resObject.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS_ALT);
			resObject.put("responseMessage", "Successful");
			resObject.put("bookValue", "KES:" + bookValue);
			resObject.put("totalDepreciation", "KES:" + totalDepreciation);
			resObject.put("assetCode", asset.getAssetCode());
			resObject.put("serialNumber", asset.getSerialNumber());
			resObject.put("salvageValue", asset.getSalvageValue());
		} catch (Exception ex) {
			resObject.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
			resObject.put("responseMessage", "failed");
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return resObject;
	}

	@Override
	public JSONObject disposeAsset(JSONObject assetRequest) {
		JSONObject resObject = new JSONObject();
		try {
			String updatedBy = assetRequest.getAsString("dicommissionedBy");
			LOGGER.info("RECEIVE ASSETS DISPOSAL REQUEST");

			String assetCode = assetRequest.getAsString("assetCode");
			Asset asset = assetService.findByAssetCode(assetCode);
			if (asset == null) {
				resObject.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
				resObject.put("responseMessage", "Asset not found");
				return resObject;
			}

			asset.setStatus(AppConstants.STATUS_DECOMMISSIONED);
			assetService.save(asset);

			AssetAllocation allocation = assetAllocationService.findByAssetCode(asset.getAssetCode());
			if (allocation != null) {
				allocation.setStatus(AppConstants.STATUS_DECOMMISSIONED);
				assetAllocationService.save(allocation);
			}

			JSONObject warehouseRequest = new JSONObject();
			warehouseRequest.put("assetCode", asset.getAssetCode());
			warehouseRequest.put("statusName", AppConstants.STATUS_DECOMMISSIONED);
			warehouseRequest.put("updatedBy", updatedBy);
			asyncHttpClient.httpPOST(AppConstants.WAREHOUSE_UPDATE_URL, warehouseRequest.toString());

			resObject.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS_ALT);
			resObject.put("responseMessage", "Asset has been successfully decommissioned.");
		} catch (Exception ex) {
			resObject.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
			resObject.put("responseMessage", "failed");
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return resObject;
	}

	private double calculateTotalDepreciation(double initialCost, double salvageValue, int usefulLife, int yearDiff) {
		double annualDepreciation = 0.0;
		double totalDepreciation = 0.0;
		if (usefulLife != 0) {
			annualDepreciation = (initialCost - salvageValue) / usefulLife;
			totalDepreciation = annualDepreciation * yearDiff;
		}
		return totalDepreciation;
	}

	private double calculateReducingBalanceDepreciation(double initialCost, int usefulLife) {
		double accumulatedDepreciation = 0.0;
		for (int year = 1; year <= usefulLife; year++) {
			double depreciation = (initialCost - accumulatedDepreciation) * REDUCING_BALANCE_RATE;
			accumulatedDepreciation += depreciation;
		}
		return accumulatedDepreciation;
	}
}
