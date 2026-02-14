package com.zain.ksa.alm.financials.controller;

import java.text.ParseException;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zain.ksa.alm.financials.service.AssetTrackingService;

import net.minidev.json.JSONObject;

@CrossOrigin(origins = { "*" }, maxAge = 3600L)
@RestController
@RequestMapping({ "/" })
public class AssetTrackingController {

	private static final Logger LOGGER = LogManager.getLogger(AssetTrackingController.class);

	private final AssetTrackingService assetTrackingService;

	@Autowired
	public AssetTrackingController(AssetTrackingService assetTrackingService) {
		this.assetTrackingService = assetTrackingService;
	}

	@RequestMapping({ "addAssetTracking" })
	public JSONObject addAssetTracking(@RequestBody String req, HttpServletResponse httpResponse)
			throws ParseException {
		LOGGER.info("Received request: addAssetTracking");
		return assetTrackingService.addAssetTracking(req);
	}

	@PostMapping(value = "getAssetTracking", produces = "application/json")
	public Map<String, Object> getAssetTracking(@RequestBody JSONObject assetRequest) {
		LOGGER.info("Received request: getAssetTracking");
		return assetTrackingService.getAssetTracking(assetRequest);
	}
}
