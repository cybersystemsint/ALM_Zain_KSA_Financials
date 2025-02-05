/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics;

import com.opencsv.exceptions.CsvValidationException;
import com.telkom.co.ke.almoptics.entities.tb_Asset;
import com.telkom.co.ke.almoptics.entities.tb_Asset_Depreciation;
import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.services.AssetService;
import com.telkom.co.ke.almoptics.services.tb_Asset_DepreciationService;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.services.FarReportService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 *
 * @author jgithu
 */
@Component
public class DepreciationScheduler {

    private static final Logger LOGGER = LogManager.getLogger(DepreciationScheduler.class.getName());

    @Autowired
    private tb_Asset_DepreciationService tb_Asset_DepreciationService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private FinancialReportService financialReportService;

    @Autowired
    private FarReportService farReportService;

    //@Scheduled(cron = "0 */20 * * * ?", zone = "Africa/Nairobi") // Run every 20 minutes
    public void readCards() throws ParserConfigurationException, IOException, CsvValidationException, ParseException {
        LOGGER.info("SCHEDULER STARTED FOR DEPRECIATION : ");
        int currentYear = Calendar.getInstance().get(1);
        LocalDate localDate = LocalDate.now();
        int purchaseYear = localDate.getYear();
        List<tb_FarReport> assetsHere = this.farReportService.findAll();
        double accumulatedDepreciation = 0.0D;
        for (int i = 0; i < assetsHere.size(); i++) {
            tb_FarReport assetsFinancials = assetsHere.get(i);
            // tb_Asset_Depreciation tbassetDepreciation = new tb_Asset_Depreciation();
            double initialCost = assetsFinancials.getCost();
            double salvageValue = assetsFinancials.getSalvageValue();
            int usefulLife = assetsFinancials.getLife();
            Date dateInservice = assetsFinancials.getDatePlacedInService();

            //we dont have adjustment in new FAR
            // double adjustment = assetsFinancials.getAdjustment();
            // Calculate monthly depreciation if not provided
            double monthlyDepreciation = assetsFinancials.getDepreciationAmount() != null ? assetsFinancials.getDepreciationAmount() : 0;
            if (monthlyDepreciation == 0) {
                //run depreciation 
                monthlyDepreciation = (initialCost - salvageValue) / usefulLife;

                // Calculate the number of months utilised
                LocalDate dateOfServiceLocal = dateInservice.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate dateNextMonth = dateOfServiceLocal.withDayOfMonth(1).plusMonths(1);
                LocalDate currentDate = LocalDate.now();
                long numberOfMonthsUtilised = ChronoUnit.MONTHS.between(dateNextMonth, currentDate);

                // Calculate accumulated depreciation
                //accumulatedDepreciation = (monthlyDepreciation * numberOfMonthsUtilised) + adjustment;
                accumulatedDepreciation = (monthlyDepreciation * numberOfMonthsUtilised);

                double netCost = initialCost - accumulatedDepreciation;

                double bookValue = initialCost - accumulatedDepreciation;

                // Check if asset code exists in tb_Asset_Depreciation table
                tb_Asset_Depreciation tbassetDepreciation = this.tb_Asset_DepreciationService.findByAssetCode(assetsFinancials.getAssetId());
                if (tbassetDepreciation == null) {
                    tbassetDepreciation = new tb_Asset_Depreciation();
                    tbassetDepreciation.setAssetCode(assetsFinancials.getAssetId());
                }
                // Update depreciation record
                tbassetDepreciation.setAssetBookValue(netCost);
                tbassetDepreciation.setDepreciationDate(currentDate.toString());
                tbassetDepreciation.setRecordDatetime(new Date());
                tbassetDepreciation.setAssetBookValue(bookValue);

                this.tb_Asset_DepreciationService.save(tbassetDepreciation);
                //Update financial report with new values
                //REMEMBER TO UPDATE HERE 
                assetsFinancials.setMonthlyDepreciationAmt(monthlyDepreciation);
                assetsFinancials.setAccumulatedDepreciationAmt(accumulatedDepreciation);
                assetsFinancials.setNetCost(netCost);
                assetsFinancials.setDepreciationDate(new Date());
                this.farReportService.save(assetsFinancials);
            }
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
}
