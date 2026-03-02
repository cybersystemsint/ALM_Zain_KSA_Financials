package com.zain.ksa.alm.financials.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.AssetJournal;
import com.zain.ksa.alm.financials.entity.UnmappedActiveAsset;
import com.zain.ksa.alm.financials.service.AssetJournalService;
import com.zain.ksa.alm.financials.service.AssetReportService;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

@Service
public class AssetReportServiceImpl implements AssetReportService {

	private static final Logger LOGGER = LogManager.getLogger(AssetReportServiceImpl.class);

	private final AssetJournalService assetJournalService;





	public AssetReportServiceImpl(AssetJournalService assetJournalService) {
		this.assetJournalService = assetJournalService;
	}

	@Override
	public void captureAssetJournal(JSONObject assetRequest) {
		try {
			LOGGER.info("RECEIVE ASSETS JOURNAL REQUEST {}", assetRequest);

			AssetJournal journal = new AssetJournal();
			journal.setActivity(assetRequest.getAsString("activity"));
			journal.setTrackingDate(new Date());
			journal.setRecordDatetime(new Date());
			journal.setAssetCode(assetRequest.getAsString("assetCode"));
			journal.setActivityBy(assetRequest.getAsString("activityBy"));
			journal.setLocationId(assetRequest.getAsString("locationId"));

			assetJournalService.save(journal);
		} catch (Exception ex) {
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
	}

	@Override
	public JSONArray getAssetJournal() {
		JSONArray resArray = new JSONArray();
		try {
			LOGGER.info("RECEIVE GET ASSETS JOURNAL REQUEST");
			List<AssetJournal> assetJournalList = assetJournalService.findAll();

			for (AssetJournal journal : assetJournalList) {
				JSONObject resMessage = new JSONObject();
				resMessage.put("assetCode", journal.getAssetCode());
				resMessage.put("activity", journal.getActivity());
				resMessage.put("details", journal.getDetails());
				resMessage.put("activityBy", journal.getActivityBy());
				resMessage.put("locationId", journal.getLocationId());
				resMessage.put("trackingDate", journal.getTrackingDate());
				resArray.add(resMessage);
			}
		} catch (Exception ex) {
			LOGGER.error("EXCEPTION: {}", ex.getMessage(), ex);
		}
		return resArray;
	}
}
