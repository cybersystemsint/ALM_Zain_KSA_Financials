package com.zain.ksa.alm.financials.service.impl;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.constant.AppConstants;
import com.zain.ksa.alm.financials.entity.Asset;
import com.zain.ksa.alm.financials.entity.Item;
import com.zain.ksa.alm.financials.service.AssetService;
import com.zain.ksa.alm.financials.service.AssetTransferManagementService;
import com.zain.ksa.alm.financials.service.ItemService;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

@Service
public class AssetTransferManagementServiceImpl implements AssetTransferManagementService {

	private static final Logger LOGGER = LogManager.getLogger(AssetTransferManagementServiceImpl.class);

	private final AssetService assetService;
	private final ItemService itemService;

	@Autowired
	public AssetTransferManagementServiceImpl(AssetService assetService, ItemService itemService) {
		this.assetService = assetService;
		this.itemService = itemService;
	}

	@Override
	public JSONObject transferAsset(JSONObject assetRequest) {
		JSONObject jsonObjectResponse = new JSONObject();
		try {
			Asset tbasset = new Asset();
			LOGGER.info("RECEIVED ASSET TRANSFER REQUEST{}", assetRequest);
			if (!assetRequest.isEmpty()) {
				String assetCode = assetRequest.getAsString("assetCode");
				String inventoryId = assetRequest.getAsString("inventoryId");
				String serialNumber = assetRequest.getAsString("serialNumber");
				float purchasePrice = assetRequest.getAsNumber("purchasePrice").floatValue();
				String poId = assetRequest.getAsString("poId");
				String createdBy = assetRequest.getAsString("createdBy");
				String supplierId = assetRequest.getAsString("supplierId");
				String purchaseDate = assetRequest.getAsString("purchaseDate");
				String warrantyDetails = assetRequest.getAsString("warrantyDetails");
				String warrantExpiryDate = assetRequest.getAsString("warrantExpiryDate");
				String details = assetRequest.getAsString("details");
				String status = assetRequest.getAsString("status");
				SimpleDateFormat format = new SimpleDateFormat(AppConstants.DATE_FORMAT_YYYY_MM_DD);
				tbasset.setAssetCode(assetCode);
				tbasset.setCreatedBy(createdBy);
				tbasset.setInventoryId(inventoryId);
				tbasset.setPoId(poId);
				tbasset.setSalvageValue("0");
				tbasset.setPurchaseDate(format.parse(purchaseDate));
				tbasset.setPurchasePrice(purchasePrice);
				tbasset.setRecordDatetime(format.parse(purchaseDate));
				tbasset.setWarrantExpiryDate(warrantExpiryDate);
				tbasset.setSerialNumber(serialNumber);
				tbasset.setApproved(false);
				tbasset.setUsefulLife("0");
				tbasset.setStatus(status);
				tbasset.setSupplierId(supplierId);
				tbasset.setWarrantyDetails(warrantyDetails);
				assetService.save(tbasset);
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS_ALT);
				jsonObjectResponse.put("responseMessage", "Asset transfered Successfully");
			} else {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
				jsonObjectResponse.put("responseMessage", "Error occured, AssetCode already exist");
			}
		} catch (Exception ex) {
			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
			jsonObjectResponse.put("responseMessage", "Error occured, AssetCode already exist");
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public JSONArray getApprovedAssets(JSONObject request) {
		String status = request.getAsString("status");
		JSONArray jsonObjectResponse = new JSONArray();
		try {
			LOGGER.info("GET ASSETS FOR APPROVAL - status: {}", status);
			List<Asset> assetsToApprove = assetService.findByStatus(status);
			for (Asset tbasset : assetsToApprove) {
				JSONObject singleAssetObj = new JSONObject();
				Item item = itemService.findByItemCode(tbasset.getAssetCode());
				singleAssetObj.put("assetCode", tbasset.getAssetCode());
				singleAssetObj.put("inventoryId", tbasset.getInventoryId());
				singleAssetObj.put("serialNumber", tbasset.getSerialNumber());
				singleAssetObj.put("purchasePrice", Float.valueOf(tbasset.getPurchasePrice()));
				singleAssetObj.put("poId", tbasset.getPoId());
				singleAssetObj.put("createdBy", tbasset.getCreatedBy());
				singleAssetObj.put("supplierId", tbasset.getSupplierId());
				singleAssetObj.put("purchaseDate", tbasset.getPurchaseDate());
				singleAssetObj.put("warrantyDetails", tbasset.getWarrantyDetails());
				singleAssetObj.put("warrantExpiryDate", tbasset.getWarrantExpiryDate());
				if (item != null) {
					singleAssetObj.put("depreciationMethod", item.getDepreciationMethod());
				} else {
					singleAssetObj.put("depreciationMethod", "Not Set");
				}
				LocalDate localDate1 = tbasset.getRecordDatetime().toInstant().atZone(ZoneId.systemDefault())
						.toLocalDate();
				LocalDate localDate2 = LocalDate.now();
				Period period = Period.between(localDate1, localDate2);
				int yearDiff = period.getYears();
				double depreciationRate = 0.25D;
				double bookValue = 0.0D;
				double accumulatedDepreciation = 0.0D;
				if (item != null) {
					if (item.getDepreciationMethod().startsWith("R") || item.getDepreciationMethod().startsWith("r")) {
						for (int year = 1; year <= yearDiff; year++) {
							double depreciation = (tbasset.getPurchasePrice() - accumulatedDepreciation)
									* depreciationRate;
							accumulatedDepreciation += depreciation;
							bookValue = tbasset.getPurchasePrice() - accumulatedDepreciation;
						}
					} else {
						bookValue = tbasset.getPurchasePrice() - calculateTotalDepreciation(tbasset.getPurchasePrice(),
								Double.parseDouble(tbasset.getSalvageValue()),
								Integer.valueOf(tbasset.getUsefulLife()).intValue(), yearDiff);
					}
				} else {
					bookValue = tbasset.getPurchasePrice() - calculateTotalDepreciation(tbasset.getPurchasePrice(),
							Double.parseDouble(tbasset.getSalvageValue()),
							Integer.valueOf(tbasset.getUsefulLife()).intValue(), yearDiff);
				}
				if (bookValue < Double.parseDouble(tbasset.getSalvageValue())) {
					bookValue = Double.parseDouble(tbasset.getSalvageValue());
				}
				singleAssetObj.put("bookValue", Integer.valueOf((int) Math.round(bookValue)));
				singleAssetObj.put("details", tbasset.getDetails());
				jsonObjectResponse.add(singleAssetObj);
			}
		} catch (Exception ex) {
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public JSONObject approveAssetTransfer(JSONObject assetRequest) {
		JSONObject jsonObjectResponse = new JSONObject();
		try {
			LOGGER.info("GET ASSET TRANSFER APPROVAL REQUEST{}", assetRequest);
			String serialNumber = assetRequest.getAsString("serialNumber");
			String status = assetRequest.getAsString("status");
			Asset tbasset = assetService.findBySerialNumber(serialNumber);
			if (!assetRequest.isEmpty()) {
				String approvedBy = assetRequest.getAsString("approvedBy");
				Date date = new Date();
				tbasset.setApprovalDate(date);
				tbasset.setStatus(status);
				tbasset.setApprovedBy(approvedBy);
				tbasset.setApproved(true);
				assetService.save(tbasset);
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS_ALT);
				jsonObjectResponse.put("responseMessage", "Asset Transfer Approved Successfully");
			} else {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
				jsonObjectResponse.put("responseMessage", "Status cannot be null.");
			}
		} catch (Exception ex) {
			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE_ALT);
			jsonObjectResponse.put("responseMessage", "Error Occurred");
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public JSONArray getAssetsForApproval(JSONObject request) {
		String status = request.getAsString("status");
		JSONArray jsonObjectResponse = new JSONArray();
		try {
			LOGGER.info("GET ASSETS FOR APPROVAL - status: {}", status);
			List<Asset> assetsToApprove = assetService.findByStatus(status);
			for (Asset tbasset : assetsToApprove) {
				JSONObject singleAssetObj = new JSONObject();
				Item item = itemService.findByItemCode(tbasset.getAssetCode());
				singleAssetObj.put("assetCode", tbasset.getAssetCode());
				singleAssetObj.put("inventoryId", tbasset.getInventoryId());
				singleAssetObj.put("serialNumber", tbasset.getSerialNumber());
				singleAssetObj.put("purchasePrice", Float.valueOf(tbasset.getPurchasePrice()));
				singleAssetObj.put("poId", tbasset.getPoId());
				singleAssetObj.put("createdBy", tbasset.getCreatedBy());
				singleAssetObj.put("supplierId", tbasset.getSupplierId());
				singleAssetObj.put("purchaseDate", tbasset.getPurchaseDate());
				singleAssetObj.put("warrantyDetails", tbasset.getWarrantyDetails());
				singleAssetObj.put("warrantExpiryDate", tbasset.getWarrantExpiryDate());
				if (item != null) {
					singleAssetObj.put("depreciationMethod", item.getDepreciationMethod());
				} else {
					singleAssetObj.put("depreciationMethod", "Not Set");
				}
				singleAssetObj.put("details", tbasset.getDetails());
				jsonObjectResponse.add(singleAssetObj);
			}
		} catch (Exception ex) {
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	private double calculateTotalDepreciation(double initialCost, double salvageValue, int usefulLife, int yearDiff) {
		double annualDepreciation = 0.0D;
		double totalDepreciation = 0.0D;
		if (usefulLife != 0) {
			annualDepreciation = (initialCost - salvageValue) / usefulLife;
			totalDepreciation = annualDepreciation * yearDiff;
		}
		return totalDepreciation;
	}
}
