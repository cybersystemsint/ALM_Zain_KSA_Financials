package com.zain.ksa.alm.financials.service;

import java.util.Map;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public interface FinancialReportManagementService {

	/**
	 * Get FAR (Fixed Asset Register) report with pagination and filtering
	 */
	Map<String, Object> getFARReport(JSONObject request);

	/**
	 * Upload/Create or Update FAR report records
	 */
	JSONObject uploadFAR(String requestBody);

	/**
	 * Upload/Create or Update Financial Report records
	 */
	JSONObject uploadFinancialReport(String requestBody);

	/**
	 * Get Financial Report records with optional filtering
	 */
	JSONArray getFinancialReport(JSONObject request);

	/**
	 * Get all unmapped active assets
	 */
	JSONArray getUnmappedActiveAssets();
}
