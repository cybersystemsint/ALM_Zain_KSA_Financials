package com.zain.ksa.alm.financials.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.zain.ksa.alm.financials.entity.AssetDepreciation;
import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.service.AssetDepreciationService;
import com.zain.ksa.alm.financials.service.FarReportService;

@Component
public class DepreciationScheduler {

	private static final Logger LOGGER = LogManager.getLogger(DepreciationScheduler.class);
	private static final int PAGE_SIZE = 2500;

	private final AssetDepreciationService assetDepreciationService;
	private final FarReportService farReportService;

	@Autowired
	public DepreciationScheduler(AssetDepreciationService assetDepreciationService, FarReportService farReportService) {
		this.assetDepreciationService = assetDepreciationService;
		this.farReportService = farReportService;
	}

	// @Scheduled(cron = "0 0 0 L * ?", zone = "Africa/Nairobi")
	public void processDepreciation() {
		try {
			LOGGER.info("Depreciation scheduler started");

			int pageNumber = 0;
			Page<FarReport> page;

			do {
				Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE);
				page = farReportService.findAll(pageable);
				processRecords(page.getContent());
				pageNumber++;
			} while (page.hasNext());

			LOGGER.info("Depreciation scheduler completed");
		} catch (Exception ex) {
			LOGGER.error("Exception occurred during depreciation scheduler: {}", ex.getMessage(), ex);
		}
	}

	private void processRecords(List<FarReport> assets) {
		assets.forEach(asset -> {
			try {
				double initialCost = asset.getCost();
				double salvageValue = asset.getSalvageValue();
				int usefulLife = asset.getLife();
				Date dateInService = asset.getDatePlacedInService();

				double monthlyDepreciation = asset.getDepreciationAmount() != null ? asset.getDepreciationAmount()
						: (initialCost - salvageValue) / usefulLife;

				LocalDate serviceDate = dateInService.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				LocalDate startDate = serviceDate.withDayOfMonth(1).plusMonths(1);
				LocalDate currentDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

				long monthsUtilized = ChronoUnit.MONTHS.between(startDate, currentDate);
				monthsUtilized = Math.min(monthsUtilized, usefulLife);

				double accumulatedDepreciation = monthlyDepreciation * monthsUtilized;
				double netCost = initialCost - accumulatedDepreciation;

				if (netCost > 0) {
					saveDepreciationRecord(asset.getAssetId(), currentDate, netCost, accumulatedDepreciation);
					updateAssetRecord(asset, monthlyDepreciation, accumulatedDepreciation, netCost);
				}
			} catch (Exception ex) {
				LOGGER.error("Error processing asset {}: {}", asset.getAssetId(), ex.getMessage(), ex);
			}
		});
	}

	private void saveDepreciationRecord(String assetId, LocalDate currentDate, double netCost,
			double accumulatedDepreciation) {
		AssetDepreciation depreciation = assetDepreciationService.findByAssetCodeAndDepreciationDate(assetId,
				currentDate.toString());

		if (depreciation == null) {
			depreciation = new AssetDepreciation();
			depreciation.setAssetCode(assetId);
		}

		depreciation.setAssetBookValue(netCost);
		depreciation.setDepreciationDate(currentDate.toString());
		depreciation.setRecordDatetime(new Date());
		depreciation.setAccumulatedDepreciation(accumulatedDepreciation);

		assetDepreciationService.save(depreciation);
	}

	private void updateAssetRecord(FarReport asset, double monthlyDepreciation, double accumulatedDepreciation,
			double netCost) {
		asset.setMonthlyDepreciationAmt(monthlyDepreciation);
		asset.setAccumulatedDepreciationAmt(accumulatedDepreciation);
		asset.setNetCost(netCost);
		asset.setDepreciationDate(new Date());
		farReportService.save(asset);
	}
}
