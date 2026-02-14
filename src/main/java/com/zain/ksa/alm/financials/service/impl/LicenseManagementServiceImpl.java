package com.zain.ksa.alm.financials.service.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.License;
import com.zain.ksa.alm.financials.entity.Node;
import com.zain.ksa.alm.financials.entity.NodeType;
import com.zain.ksa.alm.financials.service.LicenseManagementService;
import com.zain.ksa.alm.financials.service.LicenseService;
import com.zain.ksa.alm.financials.util.AsyncHttpClient;
import com.zain.ksa.alm.financials.service.NodeService;
import com.zain.ksa.alm.financials.service.NodeTypeService;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

@Service
public class LicenseManagementServiceImpl implements LicenseManagementService {

	private static final Logger LOGGER = LoggerFactory.getLogger(LicenseManagementServiceImpl.class);
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	private final LicenseService licenseService;
	private final NodeService nodeService;
	private final NodeTypeService nodeTypeService;
	private final AsyncHttpClient asyncHttpClient;

	public LicenseManagementServiceImpl(LicenseService licenseService, NodeService nodeService,
			NodeTypeService nodeTypeService, AsyncHttpClient asyncHttpClient) {
		this.licenseService = licenseService;
		this.nodeService = nodeService;
		this.nodeTypeService = nodeTypeService;
		this.asyncHttpClient = asyncHttpClient;
	}

	@Override
	public JSONObject uploadLicenses(String req) {
		JSONObject response = new JSONObject();
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(req);
			for (int i = 0; i < jsonArray.length(); i++) {
				org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
				LOGGER.info("Processing license request");

				long recordNo = Long.parseLong(jsonObject.getString("recordNo"));
				String licenseID = jsonObject.getString("licenseId");
				String nodeId = jsonObject.getString("nodeId");

				if (recordNo == 0) {
					String error = createLicense(jsonObject, licenseID, nodeId);
					if (error != null) {
						response.put("error", error);
						break;
					}
				} else {
					String error = updateLicense(jsonObject, licenseID);
					if (error != null) {
						response.put("error", error);
						break;
					}
				}
			}

			if (response.containsKey("error")) {
				response.put("responseCode", "1");
				response.put("responseMessage", response.getAsString("error"));
			} else {
				response.put("responseCode", "0");
				response.put("responseMessage", "License successfully created/updated");
			}
		} catch (Exception ex) {
			response.put("responseCode", "1");
			response.put("responseMessage", ex.getMessage());
			LOGGER.error("Exception in uploadLicenses: {}", ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public JSONArray getAllLicenses() {
		JSONArray response = new JSONArray();
		try {
			LOGGER.info("Fetching all licenses");
			List<License> allLicenses = licenseService.findAll();

			allLicenses.forEach(license -> {
				JSONObject licenseJson = new JSONObject();
				licenseJson.put("recordNo", license.getId());
				licenseJson.put("nodeName", license.getNodeName());
				licenseJson.put("nodeType", license.getNodeType());
				licenseJson.put("neSiteName", license.getNeSiteName());
				licenseJson.put("siteId", license.getSiteId());
				licenseJson.put("zone", license.getZone());
				licenseJson.put("licenseId", license.getLicenseId());
				licenseJson.put("licenseDetail", license.getLicenseDetail());
				licenseJson.put("allocated", license.getAllocated());
				licenseJson.put("usage", license.getUsage());
				licenseJson.put("usagePercentage", license.getUsagePercentage());
				licenseJson.put("config", license.getConfig());
				licenseJson.put("unit", license.getUnit());
				licenseJson.put("insertDate", license.getInsertDate());
				licenseJson.put("lastChangeDate", license.getLastChangeDate());
				licenseJson.put("licenseDetailValue", license.getLicenseDetailValue());
				licenseJson.put("technology", license.getTechnology());
				licenseJson.put("manufacturer", license.getManufacturer());
				licenseJson.put("isMapped", license.getIsMapped());
				licenseJson.put("createdById", license.getCreatedById());
				licenseJson.put("createdByName", license.getCreatedByName());
				response.add(licenseJson);
			});
		} catch (Exception ex) {
			LOGGER.error("Exception in getAllLicenses: {}", ex.getMessage(), ex);
		}
		return response;
	}

	private String createLicense(org.json.JSONObject jsonObject, String licenseID, String nodeId)
			throws ParseException {
		if (licenseID.isEmpty()) {
			return "Missing License ID detected";
		}

		List<License> existingLicenses = licenseService.findByLicenseId(licenseID);
		if (!existingLicenses.isEmpty()) {
			return "License ID already exists: " + licenseID;
		}

		ensureNodeExists(jsonObject, nodeId);

		License license = buildLicenseFromJson(jsonObject);
		licenseService.save(license);

		trackAssetAction(jsonObject, "ADD LICENCE ID " + licenseID);
		return null;
	}

	private String updateLicense(org.json.JSONObject jsonObject, String licenseID) throws ParseException {
		if (licenseID.isEmpty()) {
			return "Missing License ID detected";
		}

		List<License> existingLicenses = licenseService.findByLicenseId(licenseID);
		if (existingLicenses.isEmpty()) {
			return "License ID not found: " + licenseID;
		}

		License license = existingLicenses.get(0);
		updateLicenseFromJson(license, jsonObject);
		licenseService.save(license);

		trackAssetAction(jsonObject, "EDIT LICENCE ID " + licenseID);
		return null;
	}

	private void ensureNodeExists(org.json.JSONObject jsonObject, String nodeId) throws ParseException {
		List<Node> nodelist = nodeService.findByNode(nodeId);
		if (nodelist.isEmpty()) {
			Node node = new Node();
			node.setRecordDateTime(getCurrentSqlDate());
			node.setNode(nodeId);
			node.setSiteId(Integer.parseInt(jsonObject.getString("siteId")));
			NodeType nodeType = nodeTypeService.findByNodeType(jsonObject.getString("nodeType"));
			node.setNodeTypeId(nodeType.getId());
			nodeService.save(node);
		}
	}

	private License buildLicenseFromJson(org.json.JSONObject jsonObject) throws ParseException {
		License license = new License();
		updateLicenseFromJson(license, jsonObject);
		license.setCreatedById(jsonObject.getInt("createdById"));
		license.setCreatedByName(jsonObject.getString("createdByName").trim());
		return license;
	}

	private void updateLicenseFromJson(License license, org.json.JSONObject jsonObject) throws ParseException {
		license.setNodeName(jsonObject.getString("nodeId"));
		license.setNodeType(jsonObject.getString("nodeType"));
		license.setNeSiteName(jsonObject.getString("siteId"));
		license.setSiteId(jsonObject.getString("siteId"));
		license.setZone(jsonObject.getString("zone"));
		license.setLicenseId(jsonObject.getString("licenseId"));
		license.setLicenseDetail(jsonObject.getString("licenseDetail"));
		license.setAllocated(jsonObject.getInt("allocated"));
		license.setUsage(jsonObject.getInt("usage"));
		license.setUsagePercentage(jsonObject.getDouble("usagePercentage"));
		license.setConfig(jsonObject.getString("config"));
		license.setUnit(jsonObject.getString("unit"));
		license.setInsertDate(parseDate(jsonObject.getString("insertDate")));
		license.setLastChangeDate(parseDate(jsonObject.getString("lastChangeDate")));
		license.setLicenseDetailValue(jsonObject.optDouble("licenseDetailValue", 0.0));
		license.setTechnology(jsonObject.getString("technology"));
		license.setManufacturer(jsonObject.getString("manufacturer"));
	}

	private void trackAssetAction(org.json.JSONObject jsonObject, String actionType) {
		try {
			JSONObject assetTrack = new JSONObject();
			assetTrack.put("serialNumber", jsonObject.getString("licenseId"));
			assetTrack.put("siteId", jsonObject.getString("siteId"));
			assetTrack.put("actionType", actionType);
			assetTrack.put("username", jsonObject.getString("createdByName"));

			org.json.JSONArray trackRequest = new org.json.JSONArray();
			trackRequest.put(assetTrack);

			String ipAddress = getIPAddress();
			asyncHttpClient.httpPOST("http://" + ipAddress + ":8080/alm_zain_ksa_financials/addAssetTracking",
					trackRequest.toString());
		} catch (Exception ex) {
			LOGGER.warn("Failed to track asset action: {}", ex.getMessage());
		}
	}

	private java.sql.Date parseDate(String dateStr) throws ParseException {
		java.util.Date utilDate = DATE_FORMAT.parse(dateStr);
		return new java.sql.Date(utilDate.getTime());
	}

	private java.sql.Date getCurrentSqlDate() throws ParseException {
		LocalDateTime now = LocalDateTime.now();
		java.util.Date parsedDate = DATE_FORMAT.parse(now.toString());
		return new java.sql.Date(parsedDate.getTime());
	}

	private String getIPAddress() {
		try {
			return java.net.InetAddress.getLocalHost().getHostAddress();
		} catch (Exception ex) {
			LOGGER.warn("Failed to get IP address: {}", ex.getMessage());
			return "localhost";
		}
	}
}
