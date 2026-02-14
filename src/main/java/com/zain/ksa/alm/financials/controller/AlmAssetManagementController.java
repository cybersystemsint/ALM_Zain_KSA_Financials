package com.zain.ksa.alm.financials.controller;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zain.ksa.alm.financials.service.AssetAllocationManagementService;
import com.zain.ksa.alm.financials.service.AssetMaintenanceService;
import com.zain.ksa.alm.financials.service.AssetReportService;
import com.zain.ksa.alm.financials.service.AssetTransferManagementService;
import com.zain.ksa.alm.financials.util.FarReportExcelExporter;
import com.zain.ksa.alm.financials.service.FinancialReportManagementService;
import com.zain.ksa.alm.financials.service.LicenseManagementService;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

@CrossOrigin(origins = { "*" }, maxAge = 3600L)
@RestController
@RequestMapping({ "/" })
public class AlmAssetManagementController {

	private static final Logger LOGGER = LogManager.getLogger(AlmAssetManagementController.class);

	private final AssetTransferManagementService assetTransferManagementService;
	private final AssetAllocationManagementService assetAllocationManagementService;
	private final AssetMaintenanceService assetMaintenanceService;
	private final AssetReportService assetReportService;
	private final FinancialReportManagementService financialReportManagementService;
	private final LicenseManagementService licenseManagementService;
	private final FarReportExcelExporter farReportExcelExporter;

	@Autowired
	public AlmAssetManagementController(AssetTransferManagementService assetTransferManagementService,
			AssetAllocationManagementService assetAllocationManagementService,
			AssetMaintenanceService assetMaintenanceService, AssetReportService assetReportService,
			FinancialReportManagementService financialReportManagementService,
			LicenseManagementService licenseManagementService,
			FarReportExcelExporter farReportExcelExporter) {
		this.assetTransferManagementService = assetTransferManagementService;
		this.assetAllocationManagementService = assetAllocationManagementService;
		this.assetMaintenanceService = assetMaintenanceService;
		this.assetReportService = assetReportService;
		this.financialReportManagementService = financialReportManagementService;
		this.licenseManagementService = licenseManagementService;
		this.farReportExcelExporter = farReportExcelExporter;
	}

	@PostMapping(value = "uploadLicenses", produces = "application/json")
	public JSONObject uploadLicenses(@RequestBody String req, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: uploadLicenses");
		return licenseManagementService.uploadLicenses(req);
	}

	@GetMapping("/getLicenses")
	public JSONArray getLicenses(HttpServletResponse httpResponse) {
		LOGGER.info("Received request: getLicenses");
		return licenseManagementService.getAllLicenses();
	}

	@GetMapping({ "getUnmappedActiveAssets" })
	public JSONArray getUnmappedActiveAssets(HttpServletResponse httpResponse) {
		LOGGER.info("Received request: getUnmappedActiveAssets");
		return assetReportService.getUnmappedActiveAssets();
	}

	@PostMapping(value = "getFAR", produces = "application/json")
	public Map<String, Object> getFARKSA(@RequestBody JSONObject assetRequest) {
		LOGGER.info("Received request: getFAR");
		return financialReportManagementService.getFARReport(assetRequest);
	}

	@RequestMapping({ "uploadFAR" })
	public JSONObject uploadFAR(@RequestBody String req, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: uploadFAR");
		return financialReportManagementService.uploadFAR(req);
	}

	@RequestMapping({ "uploadFR" })
	public JSONObject uploadFR(@RequestBody String req, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: uploadFR");
		return financialReportManagementService.uploadFinancialReport(req);
	}

	@RequestMapping({ "getFinancialReport" })
	public JSONArray getFinancialReport(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: getFinancialReport");
		return financialReportManagementService.getFinancialReport(assetRequest);
	}

	@RequestMapping({ "assetTransfer" })
	public JSONObject assetTransfer(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: assetTransfer");
		return assetTransferManagementService.transferAsset(assetRequest);
	}

	@RequestMapping({ "getApprovedAssets" })
	public JSONArray getApprovedAssets(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: getApprovedAssets");
		return assetTransferManagementService.getApprovedAssets(assetRequest);
	}

	@RequestMapping({ "getAssetsForApproval" })
	public JSONArray getAssetsForApproval(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: getAssetsForApproval");
		return assetTransferManagementService.getAssetsForApproval(assetRequest);
	}

	@RequestMapping({ "approveAssetTransfer" })
	public JSONObject approveAssetTransfer(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: approveAssetTransfer");
		return assetTransferManagementService.approveAssetTransfer(assetRequest);
	}

	@RequestMapping({ "allocateAsset" })
	public JSONObject allocateAsset(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: allocateAsset");
		return assetAllocationManagementService.allocateAsset(assetRequest);
	}

	@RequestMapping({ "getUnapprovedAllocatedAsset" })
	public JSONArray getUnapprovedAllocatedAsset(@RequestBody JSONObject assetRequest,
			HttpServletResponse httpResponse) {
		LOGGER.info("Received request: getUnapprovedAllocatedAsset");
		return assetAllocationManagementService.getUnapprovedAllocations(assetRequest);
	}

	@RequestMapping({ "approveAssetAllocation" })
	public JSONObject approveAssetAllocation(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: approveAssetAllocation");
		return assetAllocationManagementService.approveAllocation(assetRequest);
	}

	@RequestMapping({ "assetJournal" })
	public void assetCaptureJournal(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: assetJournal");
		assetReportService.captureAssetJournal(assetRequest);
	}

	@RequestMapping({ "getAssetJournal" })
	public JSONArray getAssetJournal(HttpServletResponse httpResponse) {
		LOGGER.info("Received request: getAssetJournal");
		return assetReportService.getAssetJournal();
	}

	@RequestMapping({ "updateWarrantDetails" })
	public JSONObject updateWarrantDetails(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: updateWarrantDetails");
		return assetMaintenanceService.updateWarrantyDetails(assetRequest);
	}

	@RequestMapping({ "updateDepreciationDetails" })
	public JSONObject updateDepreciationDetails(@RequestBody JSONObject assetRequest,
			HttpServletResponse httpResponse) {
		LOGGER.info("Received request: updateDepreciationDetails");
		return assetMaintenanceService.updateDepreciationDetails(assetRequest);
	}

	@RequestMapping({ "getAssetDepreciation" })
	public JSONObject getAssetDepreciation(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: getAssetDepreciation");
		return assetMaintenanceService.getAssetDepreciation(assetRequest);
	}

	@RequestMapping({ "assetDisposal" })
	public JSONObject doAssetDisposal(@RequestBody JSONObject assetRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Received request: assetDisposal");
		return assetMaintenanceService.disposeAsset(assetRequest);
	}

	@GetMapping("/exportFarReport")
	public void exportFarReport(HttpServletResponse httpResponse) throws IOException {
		LOGGER.info("Received request: exportFarReport");
		farReportExcelExporter.exportFarReportToResponse(httpResponse);
	}
}
