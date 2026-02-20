/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.telkom.co.ke.almoptics.entities.DepreciationHistory;
import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.services.AssetService;
import com.telkom.co.ke.almoptics.services.DepreciationHistoryService;
import com.telkom.co.ke.almoptics.services.FarReportService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import com.telkom.co.ke.almoptics.services.tb_Asset_DepreciationService;

/**
 *
 * @author jgithu
 */
@Component
public class DepreciationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DepreciationScheduler.class);
    private static final int PAGE_SIZE = 2000;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);


    @Autowired
    private FarReportService farReportService;
    @Autowired
    private DepreciationHistoryService depreciationHistoryService;



    // Run every day at 19:00:00 Africa/Nairobi
      @Scheduled(cron = "0 00 19 * * *", zone = "Africa/Nairobi")
     public void processDepreciation() {

        if (!RUNNING.compareAndSet(false, true)) {
            LOGGER.warn("Depreciation scheduler already running. Skipping this execution.");
            return;
        }

        LOGGER.info("==== DEPRECIATION SCHEDULER STARTED ====");

        try {
            LocalDate now = LocalDate.now();
            int month = now.getMonthValue();
            int year = now.getYear();

            if (depreciationHistoryService.existsForMonth(month, year)) {
        LOGGER.info(
        "Depreciation history already exists for {}/{}. Skipping processing.",
        month, year
        );
         return;
        }


            int pageNumber = 0;
            Page<tb_FarReport> page;

            do {
                Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE);
                page = farReportService.findAll(pageable);

                List<tb_FarReport> assets = page.getContent();
                LOGGER.info("Processing page {} with {} records", pageNumber + 1, assets.size());

                processRecordsSequential(assets);

                List<DepreciationHistory> histories = assets.stream()
                        .map(this::mapFarToHistory)
                        .toList();

                depreciationHistoryService.saveAll(histories);

                pageNumber++;

            } while (page.hasNext());

            LOGGER.info("==== DEPRECIATION SCHEDULER COMPLETED SUCCESSFULLY ====");

        } catch (Exception ex) {
            LOGGER.error("Depreciation scheduler failed", ex);
        } finally {
            RUNNING.set(false);
        }
    }
    
    private void processRecordsSequential(List<tb_FarReport> assets) {

        LocalDate currentDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        List<tb_FarReport> changedAssets = new ArrayList<>();

        for (tb_FarReport asset : assets) {
            try {
                if (asset.getLife() <= 0 || asset.getDatePlacedInService() == null) {
                    continue;
                }

                double cost = asset.getCost();
                double salvage = asset.getSalvageValue();
                int life = asset.getLife();

                double monthly = asset.getDepreciationAmount() != null
                        ? asset.getDepreciationAmount()
                        : (cost - salvage) / life;

                LocalDate inService = asset.getDatePlacedInService()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                long monthsUsed = ChronoUnit.MONTHS.between(
                        inService.withDayOfMonth(1).plusMonths(1),
                        currentDate
                );

                monthsUsed = Math.min(monthsUsed, life);

                double accumulated = monthly * monthsUsed;
                double netCost = Math.max(cost - accumulated, 0);

                if (!Objects.equals(asset.getAccumulatedDepreciationAmt(), accumulated)) {
                    asset.setMonthlyDepreciationAmt(monthly);
                    asset.setAccumulatedDepreciationAmt(accumulated);
                    asset.setNetCost(netCost);
                    asset.setDepreciationDate(new Date());
                    changedAssets.add(asset);
                }

            } catch (Exception e) {
                LOGGER.error("Failed processing asset {}", asset.getAssetId(), e);
            }
        }

        if (!changedAssets.isEmpty()) {
            farReportService.saveAll(changedAssets);
        }
    }


public double calculateSalvageValue(double initialCost, double accumulatedDepreciation) {
        double salvageValue = initialCost - accumulatedDepreciation;
        return salvageValue;
    }

    public double calculateTotalDepreciation(double initialCost, double salvageValue, int usefulLife) {
        double annualDepreciation = (initialCost - salvageValue) / usefulLife;
        double totalDepreciation = annualDepreciation * usefulLife;
        return totalDepreciation;
    }

    public double calculateReducingBlDepre(double initialCost, double salvageValue, int usefulLife) {
        double depreciationRate = 0.25D;
        double bookValue = 0.0D;
        double accumulatedDepreciation = 0.0D;
        for (int year = 1; year <= usefulLife; year++) {
            double depreciation = (initialCost - accumulatedDepreciation) * depreciationRate;
            accumulatedDepreciation += depreciation;
            bookValue = initialCost - accumulatedDepreciation;
            System.out.println("Year " + year + " - Depreciation: " + depreciation + ", Book Value: " + bookValue);
        }
        return bookValue;
    }

/**
 * Maps a tb_FarReport entity to a DepreciationHistory entity
 */
private DepreciationHistory mapFarToHistory(tb_FarReport far) {
    DepreciationHistory history = new DepreciationHistory();
    history.setAssetId(far.getAssetId());
    history.setBook(far.getBook());
    history.setQuantity(far.getQuantity());
    history.setDescription(far.getDescription());
    history.setCreationDate(far.getCreationDate());
    history.setSerialNumber(far.getSerialNumber());
    history.setAssetType(far.getAssetType());
    history.setTagNumber(far.getTagNumber());
    history.setPicStatus(far.getPicStatus());
    history.setPicDate(far.getPicDate());
    history.setCipDeliveryDate(far.getCipDeliveryDate());
    history.setLinkId(far.getLinkId());
    history.setAcceptanceNumber(far.getAcceptanceNumber());
    history.setDepreciateFlag(far.getDepreciateFlag());
    history.setCipEu(far.getCipEu());
    history.setInvoiceNumber(far.getInvoiceNumber());
    history.setPoNumber(far.getPoNumber());
    history.setPoLineNumber(far.getPoLineNumber());
    history.setUplLine(far.getUplLine());
    history.setTransferToNewFar(far.getTransferToNewFar());
    history.setAssetStatus(far.getAssetStatus());
    history.setValue(far.getValue());
    history.setPartNumber(far.getPartNumber());
    history.setVendorName(far.getVendorName());
    history.setVendorNumber(far.getVendorNumber());
    history.setMergedCode(far.getMergedCode());
    history.setCreatedDate(far.getCreatedDate());
    history.setUpdatedDate(far.getUpdatedDate());
    history.setCostAccount(far.getCostAccount());
    history.setAccumulatedDepreAccount(far.getAccumulatedDepreAccount());
    history.setCipCostAccount(far.getCipCostAccount());
    history.setExpenseCostCenter(far.getExpenseCostCenter());
    history.setExpenseAccount(far.getExpenseAccount());
    history.setLife(far.getLife());
    history.setDatePlacedInService(far.getDatePlacedInService());
    history.setCost(far.getCost());
    history.setNbv(far.getNbv());
    history.setDepreciationAmount(far.getDepreciationAmount());
    history.setYtdDepreciation(far.getYtdDepreciation());
    history.setDepreciationReserve(far.getDepreciationReserve());
    history.setSalvageValue(far.getSalvageValue());
    history.setCategory(far.getCategory());
    history.setCategoryDescription(far.getCategoryDescription());
    history.setLocationSegment1(far.getLocationSegment1());
    history.setLocationSegment2(far.getLocationSegment2());
    history.setLocationSegment3(far.getLocationSegment3());
    history.setLocationSegment4(far.getLocationSegment4());
    history.setLocations(far.getLocations());
    history.setSequenceNumber(far.getSequenceNumber());
    history.setMonthlyDepreciationAmt(far.getMonthlyDepreciationAmt());
    history.setAccumulatedDepreciationAmt(far.getAccumulatedDepreciationAmt());
    history.setDepreciationDate(far.getDepreciationDate());
    history.setNetCost(far.getNetCost());
    history.setStatusFlag(far.getStatusFlag());
    history.setChangedBy(far.getChangedBy());
    history.setInsertedBy(far.getInsertedBy());
    history.setFinancialApproval(far.getFinancialApproval());
    history.setChangedDate(far.getChangedDate());
    history.setNodeType(far.getNodeType());
    history.setCreatedBy(far.getCreatedBy());
    history.setUpdatedBy(far.getUpdatedBy());
    history.setMapped(far.getMapped());
    history.setRecordDatetime(new Date()); // timestamp for history
    return history;
}


}