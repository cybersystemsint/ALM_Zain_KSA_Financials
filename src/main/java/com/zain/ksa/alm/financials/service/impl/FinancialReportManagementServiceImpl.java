package com.zain.ksa.alm.financials.service.impl;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.constant.AppConstants;
import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.entity.FinancialReport;
import com.zain.ksa.alm.financials.service.FarReportService;
import com.zain.ksa.alm.financials.service.FinancialReportManagementService;
import com.zain.ksa.alm.financials.service.FinancialReportService;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

@Service
public class FinancialReportManagementServiceImpl implements FinancialReportManagementService {

	private static final Logger LOGGER = LogManager.getLogger(FinancialReportManagementServiceImpl.class);

	private final JdbcTemplate jdbcTemplate;
	private final FarReportService farReportService;
	private final FinancialReportService financialReportService;

	public FinancialReportManagementServiceImpl(JdbcTemplate jdbcTemplate, FarReportService farReportService,
			FinancialReportService financialReportService) {
		this.jdbcTemplate = jdbcTemplate;
		this.farReportService = farReportService;
		this.financialReportService = financialReportService;
	}

	@Override
	public Map<String, Object> getFARReport(JSONObject assetRequest) {
		String status = assetRequest.getAsString("assetId");
		String columnName = assetRequest.containsKey("columnName") ? assetRequest.getAsString("columnName") : "";
		String searchQuery = assetRequest.containsKey("searchQuery") ? assetRequest.getAsString("searchQuery") : "";
		String dateFrom = assetRequest.containsKey("dateFrom") ? assetRequest.getAsString("dateFrom") : null;
		String dateTo = assetRequest.containsKey("dateTo") ? assetRequest.getAsString("dateTo") : null;

		LOGGER.info("GET ALL ASSETS {}", assetRequest);

		int page = Math.max(assetRequest.containsKey("page") ? assetRequest.getAsNumber("page").intValue() : 1, 1);
		int size = Math.max(assetRequest.containsKey("size") ? assetRequest.getAsNumber("size").intValue() : 500, 1);

		String whereClause = buildWhereClause(status, columnName, searchQuery, dateFrom, dateTo);
		List<Object> params = buildParams(status, columnName, searchQuery, dateFrom, dateTo);

		String countSql = "SELECT COUNT(*) FROM FarReport " + whereClause;
		int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());

		BigDecimal filteredCost = getAggregateValue("SUM(cost)", whereClause, params);
		BigDecimal filteredNBV = getAggregateValue("SUM(netCost)", whereClause, params);
		BigDecimal filteredDepreciation = getAggregateValue("SUM(accumulatedDepreciationAmt)", whereClause, params);

		BigDecimal totalCost = getAggregateValue("SUM(cost)", "", new ArrayList<>());
		BigDecimal totalNBV = getAggregateValue("SUM(netCost)", "", new ArrayList<>());
		BigDecimal totalDepreciation = getAggregateValue("SUM(accumulatedDepreciationAmt)", "", new ArrayList<>());

		String paginationSql = buildPaginationSql(page, size);
		String sql = "SELECT recordNo, recordDatetime, book, assetId, quantity, description, creationDate, serialNumber, "
				+ "tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, depreciateFlag, "
				+ "cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, assetStatus, value, "
				+ "partNumber, vendorName, vendorNumber, mergedCode, costAccount, accumulatedDepreAccount, "
				+ "cipCostAccount, expenseCostCenter, expenseAccount, Life, datePlacedInService, cost, nbv, "
				+ "depreciationAmount, ytdDepreciation, depreciationReserve, salvageValue, category, categoryDescription, "
				+ "locationSegment1, locationSegment2, locationSegment3, locationSegment4, locations, sequenceNumber, "
				+ "createdBy, createdDate, updatedBy, updatedDate, monthlyDepreciationAmt, accumulatedDepreciationAmt, "
				+ "depreciationDate, netCost FROM FarReport " + whereClause + paginationSql;

		List<Map<String, Object>> result = new ArrayList<>();
		jdbcTemplate.query(sql, rs -> {
			Map<String, Object> row = new LinkedHashMap<>();
			for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
				row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
			}
			result.add(row);
		}, params.toArray());

		Map<String, Object> response = new HashMap<>();
		response.put("data", result);
		response.put("totalRecords", totalRecords);
		response.put("currentPage", page);
		response.put("pageSize", size);
		response.put("totalCost", totalCost);
		response.put("totalNBV", totalNBV);
		response.put("totalDepreciation", totalDepreciation);
		response.put("filteredCost", filteredCost);
		response.put("filteredNBV", filteredNBV);
		response.put("filteredDepreciation", filteredDepreciation);
		response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

		return response;
	}

	private String buildWhereClause(String status, String columnName, String searchQuery, String dateFrom,
			String dateTo) {
		StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
		if (!status.equalsIgnoreCase("")) {
			whereClause.append(" AND assetId = ? ");
		}
		if (!columnName.equalsIgnoreCase("") && !searchQuery.equalsIgnoreCase("")
				&& !columnName.equalsIgnoreCase("recordDatetime")) {
			whereClause.append(" AND ").append(columnName.toLowerCase()).append(" LIKE ? ");
		}
		return whereClause.toString();
	}

	private List<Object> buildParams(String status, String columnName, String searchQuery, String dateFrom,
			String dateTo) {
		List<Object> params = new ArrayList<>();
		if (!status.equalsIgnoreCase("")) {
			params.add(status);
		}
		if (!columnName.equalsIgnoreCase("") && !searchQuery.equalsIgnoreCase("")
				&& !columnName.equalsIgnoreCase("recordDatetime")) {
			params.add("%" + searchQuery + "%");
		}
		if (dateFrom != null && !dateFrom.isEmpty()) {
			params.add(dateFrom);
		}
		if (dateTo != null && !dateTo.isEmpty()) {
			params.add(dateTo);
		}
		return params;
	}

	private BigDecimal getAggregateValue(String aggregateFunction, String whereClause, List<Object> params) {
		String sql = "SELECT " + aggregateFunction + " FROM FarReport " + whereClause;
		return jdbcTemplate.queryForObject(sql, BigDecimal.class, params.toArray());
	}

	private String buildPaginationSql(int page, int size) {
		int offset = (page - 1) * size;
		return " LIMIT " + size + " OFFSET " + offset;
	}

	@Override
	public JSONObject uploadFAR(String req) {
		JSONObject jsonObjectResponse = new JSONObject();
		SimpleDateFormat format = new SimpleDateFormat(AppConstants.DATE_FORMAT_YYYY_MM_DD);

		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(req);
			for (int i = 0; i < jsonArray.length(); i++) {
				org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
				LOGGER.info("RECEIVED NEW KSA FAR REQUEST {}", req);

				long recordNo = Integer.parseInt(jsonObject.getString("recordNo"));
				String assetID = jsonObject.getString("assetId");

				if (recordNo == 0) {
					handleFARCreate(jsonObject, assetID, format, jsonObjectResponse);
				} else {
					handleFARUpdate(jsonObject, assetID, format, jsonObjectResponse);
				}
			}

			if (jsonObjectResponse.containsKey("error")) {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE);
				jsonObjectResponse.put("responseMessage", "Error occured, AssetCode already exist");
			} else if (jsonObjectResponse.containsKey("errorcode")) {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE);
				jsonObjectResponse.put("responseMessage", "Missing Asset Code detected");
			} else {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS);
				jsonObjectResponse.put("responseMessage", "Financial Report successfully created/updated");
			}
		} catch (Exception ex) {
			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE);
			jsonObjectResponse.put("responseMessage", ex.getMessage());
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	private void handleFARCreate(org.json.JSONObject jsonObject, String assetID, SimpleDateFormat format,
			JSONObject response) throws ParseException {
		if (assetID.equalsIgnoreCase("")) {
			response.put("errorcode", "Missing Asset Code detected");
			return;
		}

		List<FarReport> financeList = farReportService.findByAssetId(assetID);
		if (!financeList.isEmpty()) {
			response.put("error", "Error occured, AssetCode already exist " + assetID);
		} else if (jsonObject.getString("datePlacedInService").isEmpty()) {
			response.put("error", "Error occured, Date of service cannot be empty " + assetID);
		} else if (jsonObject.getDouble("cost") < 1) {
			response.put("error", "Error occured, Input a valid initial cost for the asset id " + assetID);
		} else {
			FarReport newFinance = mapJsonToFAR(jsonObject, format, new FarReport());
			newFinance.setStatusFlag(AppConstants.STATUS_NEW);
			farReportService.save(newFinance);
		}
	}

	private void handleFARUpdate(org.json.JSONObject jsonObject, String assetID, SimpleDateFormat format,
			JSONObject response) throws ParseException {
		if (assetID.equalsIgnoreCase("")) {
			response.put("errorcode", "Missing Asset Code detected");
			return;
		}

		List<FarReport> financeList = farReportService.findByAssetId(assetID);
		if (financeList.isEmpty()) {
			response.put("error", "Error occurred, Asset ID not found: " + assetID);
		} else if (jsonObject.getString("datePlacedInService").isEmpty()) {
			response.put("error", "Error occured, Date of service cannot be empty " + assetID);
		} else if (jsonObject.getDouble("cost") < 1) {
			response.put("error", "Error occured, Input a valid initial cost for the asset id " + assetID);
		} else {
			FarReport financeUpdate = mapJsonToFAR(jsonObject, format, financeList.get(0));
			financeUpdate.setStatusFlag(AppConstants.STATUS_EXISTING);
			farReportService.save(financeUpdate);
		}
	}

	private FarReport mapJsonToFAR(org.json.JSONObject json, SimpleDateFormat format, FarReport report)
			throws ParseException {
		report.setBook(json.getString("book"));
		report.setAssetId(json.getString("assetId"));
		report.setQuantity(json.getInt("quantity"));
		report.setDescription(json.getString("description"));
		report.setCreationDate(format.parse(json.getString("creationDate")));
		report.setSerialNumber(json.getString("serialNumber"));
		report.setTagNumber(json.getString("tagNumber"));
		report.setPicStatus(json.getString("picStatus"));

		String picDate = json.getString("picDate");
		if (!picDate.isEmpty())
			report.setPicDate(format.parse(picDate));

		String cipDeliveryDate = json.getString("cipDeliveryDate");
		if (!cipDeliveryDate.isEmpty())
			report.setCipDeliveryDate(format.parse(cipDeliveryDate));

		report.setLinkId(json.getString("linkId"));
		report.setAcceptanceNumber(json.getString("acceptanceNumber"));
		report.setDepreciateFlag(json.getString("depreciateFlag"));
		report.setCipEu(json.getString("cipEu"));
		report.setInvoiceNumber(json.getString("invoiceNumber"));
		report.setPoNumber(json.getString("poNumber"));
		report.setPoLineNumber(json.getString("poLineNumber"));
		report.setUplLine(json.getString("uplLine"));
		report.setTransferToNewFar(json.getString("transferToNewFar"));
		report.setAssetStatus(json.getString("assetStatus"));

		String value = json.getString("value");
		if (!value.isEmpty())
			report.setValue(Double.parseDouble(value));

		report.setPartNumber(json.getString("partNumber"));
		report.setVendorName(json.getString("vendorName"));
		report.setVendorNumber(json.getString("vendorNumber"));
		report.setMergedCode(json.getString("mergedCode"));
		report.setCostAccount(json.getString("costAccount"));
		report.setAccumulatedDepreAccount(json.getString("accumulatedDepreAccount"));
		report.setCipCostAccount(json.getString("cipCostAccount"));
		report.setExpenseCostCenter(json.getString("expenseCostCenter"));
		report.setExpenseAccount(json.getString("expenseAccount"));
		report.setLife(json.getInt("life"));

		String datePlacedInService = json.getString("datePlacedInService");
		if (!datePlacedInService.isEmpty())
			report.setDatePlacedInService(format.parse(datePlacedInService));

		report.setCost(json.getDouble("cost"));
		report.setNbv(json.getDouble("nbv"));
		report.setDepreciationAmount(json.getDouble("depreciationAmount"));
		report.setYtdDepreciation(json.getDouble("ytdDepreciation"));
		report.setDepreciationReserve(json.getDouble("depreciationReserve"));
		report.setSalvageValue(json.getDouble("salvageValue"));
		report.setCategory(json.getString("category"));
		report.setCategoryDescription(json.getString("categoryDescription"));
		report.setLocationSegment1(json.getString("locationSegment1"));
		report.setLocationSegment2(json.getString("locationSegment2"));
		report.setLocationSegment3(json.getString("locationSegment3"));
		report.setLocationSegment4(json.getString("locationSegment4"));
		report.setLocations(json.getString("locations"));
		report.setSequenceNumber(json.getInt("sequenceNumber"));

		return report;
	}

	@Override
	public JSONObject uploadFinancialReport(String req) {
		JSONObject jsonObjectResponse = new JSONObject();
		SimpleDateFormat format = new SimpleDateFormat(AppConstants.DATE_FORMAT_YYYY_MM_DD);

		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(req);
			for (int i = 0; i < jsonArray.length(); i++) {
				org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
				LOGGER.info("RECEIVED FINANCIAL REPORT REQUEST {}", req);

				long recordNo = Integer.parseInt(jsonObject.getString("recordNo"));
				String assetID = jsonObject.getString("assetId");

				if (recordNo == 0) {
					handleFRCreate(jsonObject, assetID, format, jsonObjectResponse);
				} else {
					handleFRUpdate(jsonObject, assetID, format, jsonObjectResponse);
				}
			}

			if (jsonObjectResponse.containsKey("error")) {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE);
				jsonObjectResponse.put("responseMessage", "Error occured, AssetCode already exist");
			} else if (jsonObjectResponse.containsKey("errorcode")) {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE);
				jsonObjectResponse.put("responseMessage", "Missing Asset Code detected");
			} else {
				jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_SUCCESS);
				jsonObjectResponse.put("responseMessage", "Financial Report successfully created/updated");
			}
		} catch (Exception ex) {
			jsonObjectResponse.put("responseCode", AppConstants.RESPONSE_CODE_FAILURE);
			jsonObjectResponse.put("responseMessage", ex.getMessage());
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	private void handleFRCreate(org.json.JSONObject json, String assetID, SimpleDateFormat format, JSONObject response)
			throws ParseException {
		if (assetID.equalsIgnoreCase("")) {
			response.put("errorcode", "Missing Asset Code detected");
			return;
		}

		List<FinancialReport> financeList = financialReportService.findByAssetId(assetID);
		if (!financeList.isEmpty()) {
			response.put("error", "Error occured, AssetCode already exist" + assetID);
		} else if (json.getString("dateOfService").isEmpty()) {
			response.put("error", "Error occured, Date of service cannot be empty " + assetID);
		} else if (json.getDouble("initialCost") < 1) {
			response.put("error", "Error occured, Input a valid initial cost for the asset id " + assetID);
		} else {
			FinancialReport newFinance = mapJsonToFR(json, format, new FinancialReport());
			newFinance.setApprovalStatus(AppConstants.STATUS_CREATED);
			financialReportService.save(newFinance);
		}
	}

	private void handleFRUpdate(org.json.JSONObject json, String assetID, SimpleDateFormat format, JSONObject response)
			throws ParseException {
		if (assetID.equalsIgnoreCase("")) {
			response.put("errorcode", "Missing Asset Code detected");
			return;
		}

		List<FinancialReport> financeList = financialReportService.findByAssetId(assetID);
		if (financeList.isEmpty()) {
			response.put("error", "Error occurred, Asset ID not found: " + assetID);
		} else if (json.getString("dateOfService").isEmpty()) {
			response.put("error", "Error occured, Date of service cannot be empty " + assetID);
		} else if (json.getDouble("initialCost") < 1) {
			response.put("error", "Error occured, Input a valid initial cost for the asset id " + assetID);
		} else {
			FinancialReport finance = mapJsonToFR(json, format, financeList.get(0));
			finance.setApprovalStatus(AppConstants.STATUS_UPDATED);
			financialReportService.save(finance);
		}
	}

	private FinancialReport mapJsonToFR(org.json.JSONObject json, SimpleDateFormat format, FinancialReport report)
			throws ParseException {
		report.setSerialNumber(json.getString("serialNumber"));
		report.setRfid(json.getString("rfid"));
		report.setTag(json.getString("tag"));
		report.setAssetId(json.getString("assetId"));
		report.setAssetType(json.getString("assetType"));
		report.setNodeType(json.getString("nodeType"));
		report.setInstallationDate(format.parse(json.getString("installationDate")));
		report.setInitialCost(json.getDouble("initialCost"));
		report.setSalvageValue(json.getDouble("salvageValue"));
		report.setPoNumber(json.getString("poNumber"));
		report.setPoDate(format.parse(json.getString("poDate")));
		report.setNewFACategory(json.getString("newFACategory"));
		report.setL1(json.getString("L1"));
		report.setL2(json.getString("L2"));
		report.setL3(json.getString("L3"));
		report.setL4(json.getString("L4"));
		report.setAccDepreciationCode(json.getString("accDepreciationCode"));
		report.setDepreciationCode(json.getString("depreciationCode"));
		report.setUserfulLife(json.getInt("userfulLife"));
		report.setVendorName(json.getString("vendorName"));
		report.setVendorNumber(json.getString("vendorNumber"));
		report.setProjectNumber(json.getString("projectNumber"));
		report.setDateOfService(format.parse(json.getString("dateOfService")));
		report.setOldFACategory(json.getString("oldFACategory"));
		report.setCostCenter(json.getString("costCenter"));
		report.setAdjustment(json.getDouble("adjustment"));
		report.setInvoiceNumber(json.getString("invoiceNumber"));
		report.setTaskId(json.getString("taskId"));
		report.setPoLineNumber(json.getString("poLineNumber"));
		return report;
	}

	@Override
	public JSONArray getFinancialReport(JSONObject assetRequest) {
		JSONArray jsonObjectResponse = new JSONArray();
		try {
			LOGGER.info("GET ALL ASSETS {}", assetRequest);
			String status = assetRequest.getAsString("assetId");

			List<FinancialReport> allReport = (!status.isEmpty() && !status.equalsIgnoreCase(""))
					? financialReportService.findByAssetId(status)
					: financialReportService.findAll();

			for (FinancialReport financeRPT : allReport) {
				JSONObject singleAssetObj = new JSONObject();
				singleAssetObj.put("recordNo", financeRPT.getRecordNo());
				singleAssetObj.put("recordDatetime", financeRPT.getRecordDatetime());
				singleAssetObj.put("serialNumber", financeRPT.getSerialNumber());
				singleAssetObj.put("rfid", financeRPT.getRfid());
				singleAssetObj.put("tag", financeRPT.getTag());
				singleAssetObj.put("assetId", financeRPT.getAssetId());
				singleAssetObj.put("assetType", financeRPT.getAssetType());
				singleAssetObj.put("nodeType", financeRPT.getNodeType());
				singleAssetObj.put("installationDate", financeRPT.getInstallationDate());
				singleAssetObj.put("initialCost", financeRPT.getInitialCost());
				singleAssetObj.put("salvageValue", financeRPT.getSalvageValue());
				singleAssetObj.put("poNumber", financeRPT.getPoNumber());
				singleAssetObj.put("poDate", financeRPT.getPoDate());
				singleAssetObj.put("newFACategory", financeRPT.getNewFACategory());
				singleAssetObj.put("L1", financeRPT.getL1());
				singleAssetObj.put("L2", financeRPT.getL2());
				singleAssetObj.put("L3", financeRPT.getL3());
				singleAssetObj.put("L4", financeRPT.getL4());
				singleAssetObj.put("accDepreciationCode", financeRPT.getAccDepreciationCode());
				singleAssetObj.put("depreciationCode", financeRPT.getDepreciationCode());
				singleAssetObj.put("userfulLife", financeRPT.getUserfulLife());
				singleAssetObj.put("vendorName", financeRPT.getVendorName());
				singleAssetObj.put("vendorNumber", financeRPT.getVendorNumber());
				singleAssetObj.put("projectNumber", financeRPT.getProjectNumber());
				singleAssetObj.put("dateOfService", financeRPT.getDateOfService());
				singleAssetObj.put("oldFACategory", financeRPT.getOldFACategory());
				singleAssetObj.put("costCenter", financeRPT.getCostCenter());
				singleAssetObj.put("adjustment", financeRPT.getAdjustment());
				singleAssetObj.put("invoiceNumber", financeRPT.getInvoiceNumber());
				singleAssetObj.put("taskId", financeRPT.getTaskId());
				singleAssetObj.put("poLineNumber", financeRPT.getPoLineNumber());
				singleAssetObj.put("monthlyDepreciationAmt",
						financeRPT.getMonthlyDepreciationAmt() != null ? financeRPT.getMonthlyDepreciationAmt() : 0.0);
				singleAssetObj.put("accumulatedDepreciationAmt",
						financeRPT.getAccumulatedDepreciationAmt() != null ? financeRPT.getAccumulatedDepreciationAmt()
								: 0.0);
				singleAssetObj.put("netCost", financeRPT.getNetCost() != null ? financeRPT.getNetCost() : 0.0);
				singleAssetObj.put("approvalStatus", financeRPT.getApprovalStatus());
				singleAssetObj.put("depreciationDate", financeRPT.getDepreciationDate());
				jsonObjectResponse.add(singleAssetObj);
			}
		} catch (Exception ex) {
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return jsonObjectResponse;
	}

	@Override
	public JSONArray getUnmappedActiveAssets() {
		return new JSONArray();
	}
}
