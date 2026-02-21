package com.zain.ksa.alm.financials.service.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.AssetTracking;
import com.zain.ksa.alm.financials.entity.Node;
import com.zain.ksa.alm.financials.repository.AssetTrackingRepository;
import com.zain.ksa.alm.financials.service.AssetTrackingService;
import com.zain.ksa.alm.financials.service.NodeService;

import net.minidev.json.JSONObject;

/**
 * Asset Tracking Service Implementation
 */
@Service
@Transactional
public class AssetTrackingServiceImpl implements AssetTrackingService {

	private static final Logger LOGGER = LogManager.getLogger(AssetTrackingServiceImpl.class);

	private final AssetTrackingRepository trackingRepository;
	private final NodeService nodeService;
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	public AssetTrackingServiceImpl(AssetTrackingRepository trackingRepository, NodeService nodeService,
			JdbcTemplate jdbcTemplate) {
		this.trackingRepository = trackingRepository;
		this.nodeService = nodeService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<AssetTracking> findAll() {
		return trackingRepository.findAll();
	}

	@Override
	public AssetTracking save(AssetTracking assetTracking) {
		return trackingRepository.save(assetTracking);
	}

	@Override
	public List<AssetTracking> findBySerialNumber(String serialNumber) {
		return trackingRepository.findBySerialNumber(serialNumber);
	}

	@Override
	public List<AssetTracking> findBySiteId(String siteId) {
		return trackingRepository.findBySiteId(siteId);
	}

	@Override
	public JSONObject addAssetTracking(String req) throws ParseException {
		JSONObject jsonObjectResponse = new JSONObject();
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		LocalDateTime now = LocalDateTime.now();
		java.util.Date parsedDate = format.parse(now.toString());
		java.sql.Date newDate = new java.sql.Date(parsedDate.getTime());

		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(req);
			for (int i = 0; i < jsonArray.length(); i++) {
				org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
				LOGGER.info("Processing asset tracking request");

				Node nodes = nodeService.findBySerialNumber(jsonObject.getString("serialNumber"));
				AssetTracking tracking = new AssetTracking();
				tracking.setRecordDatetime(newDate);
				tracking.setSerialNumber(jsonObject.getString("serialNumber"));
				tracking.setSiteId(jsonObject.getString("siteId"));
				tracking.setActionType(jsonObject.getString("actionType"));
				tracking.setUsername(jsonObject.getString("username"));

				if (nodes != null && nodes.getSerialNumber() != null && !nodes.getSerialNumber().isEmpty()) {
					tracking.setModel(nodes.getModel());
					tracking.setManufacturer(String.valueOf(nodes.getManufacturerId()));
					tracking.setManufacturerDate(nodes.getManufacturingDate());
				}

				tracking.setChangeDate(newDate);
				trackingRepository.save(tracking);
				LOGGER.info("Asset tracking saved successfully");
				jsonObjectResponse.put("responseCode", "0");
				jsonObjectResponse.put("responseMessage", "Success");
			}
		} catch (JSONException ex) {
			jsonObjectResponse.put("responseCode", "1");
			jsonObjectResponse.put("responseMessage", ex.getMessage());
			LOGGER.error("Exception in addAssetTracking: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public Map<String, Object> getAssetTracking(JSONObject assetRequest) {
		String serialNumber = assetRequest.containsKey("serialNumber") ? assetRequest.getAsString("serialNumber") : "";
		String siteId = assetRequest.containsKey("siteId") ? assetRequest.getAsString("siteId") : "";
		String username = assetRequest.containsKey("username") ? assetRequest.getAsString("username") : "";
		String columnName = assetRequest.containsKey("columnName") ? assetRequest.getAsString("columnName") : "";
		String searchQuery = assetRequest.containsKey("searchQuery") ? assetRequest.getAsString("searchQuery") : "";

		List<Object> params = new ArrayList<>();
		String whereClause = " WHERE 1=1";

		if (serialNumber != null && !serialNumber.isEmpty()) {
			whereClause += " AND serialNumber = ?";
			params.add(serialNumber);
		}

		if (siteId != null && !siteId.isEmpty()) {
			whereClause += " AND siteId = ?";
			params.add(siteId);
		}

		if (username != null && !username.isEmpty()) {
			whereClause += " AND username = ?";
			params.add(username);
		}

		if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
			whereClause += " AND " + columnName.toLowerCase() + " LIKE ?";
			params.add("%" + searchQuery + "%");
		}

		String countDetails = "SELECT COUNT(*) FROM tb_AssetTracking" + whereClause;
		int totalRecords = jdbcTemplate.queryForObject(countDetails, Integer.class, params.toArray());

		int page = Math.max(assetRequest.containsKey("page") ? assetRequest.getAsNumber("page").intValue() : 1, 1);
		int size = Math.max(assetRequest.containsKey("size") ? assetRequest.getAsNumber("size").intValue() : 500, 1);

		page = Math.max(page, 0);
		size = Math.max(size, 0);

		String paginationSql = "";

		if (page == 0 && size == 0) {
			paginationSql = "";
		} else if (page == 1 && size == 20000) {
			page = 0;
			size = totalRecords;
			page = Math.max(page, 1);
			size = Math.max(size, 1);
			int offset = (page - 1) * size;
			paginationSql = " LIMIT " + size + " OFFSET " + offset;
		} else {
			page = Math.max(page, 1);
			size = Math.max(size, 1);
			int offset = (page - 1) * size;
			paginationSql = " LIMIT " + size + " OFFSET " + offset;
		}

		String newScript = "SELECT * FROM `tb_AssetTracking` " + whereClause + paginationSql;
		List<Map<String, Object>> result = jdbcTemplate.queryForList(newScript, params.toArray());

		Map<String, Object> response = new HashMap<>();
		response.put("data", result);
		response.put("totalRecords", totalRecords);
		response.put("currentPage", page);
		response.put("pageSize", size);
		response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

		return response;
	}
}
