// package com.zain.ksa.alm.financials.scheduler;

// import java.time.LocalDate;
// import java.time.LocalDateTime;
// import java.time.ZoneId;
// import java.time.format.DateTimeFormatter;
// import java.time.temporal.ChronoUnit;
// import java.util.ArrayList;
// import java.util.Date;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.atomic.AtomicBoolean;
// import java.util.stream.Collectors;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.PageRequest;
// import org.springframework.data.domain.Pageable;
// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Component;

// import com.zain.ksa.alm.financials.dto.request.ExportFormat;
// import com.zain.ksa.alm.financials.entity.DepreciationHistory;
// import com.zain.ksa.alm.financials.entity.FarReport;
// import com.zain.ksa.alm.financials.service.DepreciationHistoryService;
// import com.zain.ksa.alm.financials.service.FarReportService;
// import com.zain.ksa.alm.financials.service.impl.PreWarmExportJob;

// /**
//  * Processes monthly depreciation in database-level batches.

//  */
// @Component
// public class DepreciationScheduler {

//     private static final Logger log = LoggerFactory.getLogger(DepreciationScheduler.class);

//     /** Rows fetched from DB per JPA page query. Keep ≤ 5 000 to cap ResultSet memory. */
//     private static final int PAGE_SIZE = 2_500;

//     /**
//      * Rows flushed to DB in one batch call.
//      * Optimal JDBC batch size is typically 200–500.
//      * Must be ≤ PAGE_SIZE.
//      */
//     private static final int BATCH_SIZE = 500;

//     private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

//     private final DepreciationHistoryService depreciationHistoryService;
//     private final FarReportService           farReportService;
//     private final PreWarmExportJob           preWarmExportJob;

//     private final AtomicBoolean running = new AtomicBoolean(false);

//     public DepreciationScheduler(DepreciationHistoryService depreciationHistoryService,
//                                  FarReportService farReportService,
//                                  PreWarmExportJob preWarmExportJob) {
//         this.depreciationHistoryService = depreciationHistoryService;
//         this.farReportService           = farReportService;
//         this.preWarmExportJob           = preWarmExportJob;
//     }

//     public boolean isRunning() {
//         return running.get();
//     }

//     // ── Scheduled entry point ────────────────────────────────────────────────

//     @Scheduled(cron = "0 0 0 L * ?", zone = "Africa/Nairobi")
//     public void processDepreciation() {
//         processDepreciation(true);
//     }

//     /**
//      * @param refreshExportOnSuccess refresh pre-warmed export after a clean run
//      * @return true if the run completed without a fatal error
//      */
//     public boolean processDepreciation(boolean refreshExportOnSuccess) {
//         if (!running.compareAndSet(false, true)) {
//             log.warn("[DEPRECIATION] Run skipped — another run is already in progress");
//             return false;
//         }

//         boolean success = false;
//         try {
//             success = runDepreciationBatch();
//         } finally {
//             running.set(false);
//             if (success && refreshExportOnSuccess) {
//                 refreshPreWarmExport();
//             }
//         }

//         return success;
//     }

//     // ── Core batch loop ──────────────────────────────────────────────────────

//     private boolean runDepreciationBatch() {
//         log.info("[DEPRECIATION] ════════════════════════════════════════════════");
//         log.info("[DEPRECIATION] Run started  page_size={}  batch_size={}",
//                  PAGE_SIZE, BATCH_SIZE);
//         log.info("[DEPRECIATION] ════════════════════════════════════════════════");

//         int  pageNumber     = 0;
//         long totalProcessed = 0;
//         long totalSkipped   = 0;
//         long totalBatches   = 0;
//         long runStartMs     = System.currentTimeMillis();

//         try {
//             Page<FarReport> page;

//             do {
//                 Pageable pageable    = PageRequest.of(pageNumber, PAGE_SIZE);
//                 long     pageStartMs = System.currentTimeMillis();

//                 page = farReportService.findAll(pageable);

//                 log.info("[DEPRECIATION] ── Page {}/{} fetched  assets={}  ({}ms) ──",
//                          pageNumber + 1,
//                          page.getTotalPages(),
//                          page.getContent().size(),
//                          System.currentTimeMillis() - pageStartMs);

//                 // ── Split page into BATCH_SIZE chunks ────────────────────────
//                 List<FarReport> assets     = page.getContent();
//                 int             chunkCount = 0;

//                 for (int chunkStart = 0; chunkStart < assets.size(); chunkStart += BATCH_SIZE) {
//                     int             chunkEnd     = Math.min(chunkStart + BATCH_SIZE, assets.size());
//                     List<FarReport> chunk        = assets.subList(chunkStart, chunkEnd);
//                     long            chunkStartMs = System.currentTimeMillis();

//                     // ── Phase 1: Compute depreciation in memory ───────────────
//                     List<FarReport>          validAssets  = new ArrayList<>(chunk.size());
//                     List<DepreciationResult> validResults = new ArrayList<>(chunk.size());
//                     int                      chunkSkipped = 0;

//                     for (FarReport asset : chunk) {
//                         try {
//                             DepreciationResult result = computeDepreciation(asset);
//                             if (result == null) {
//                                 chunkSkipped++;
//                                 continue;
//                             }
//                             validAssets.add(asset);
//                             validResults.add(result);
//                         } catch (Exception ex) {
//                             chunkSkipped++;
//                             log.error("[DEPRECIATION]   Asset {} compute error: {}",
//                                       asset.getAssetId(), ex.getMessage(), ex);
//                         }
//                     }

//                     int chunkProcessed = 0;

//                     if (!validAssets.isEmpty()) {
//                         // ── Phase 2: ONE bulk query for existing records ──────
//                         String period = validResults.get(0).period();
//                         List<String> assetIds = validAssets.stream()
//                                 .map(FarReport::getAssetId)
//                                 .collect(Collectors.toList());

//                         Map<String, DepreciationHistory> existingMap =
//                                 depreciationHistoryService.findByAssetIdsAndPeriod(assetIds, period)
//                                         .stream()
//                                         .collect(Collectors.toMap(
//                                                 DepreciationHistory::getAssetId,
//                                                 d -> d,
//                                                 (a, b) -> a));   // keep first if duplicates

//                         // ── Phase 3: Build batch lists ────────────────────────
//                         List<DepreciationHistory> depreciationBatch = new ArrayList<>(validAssets.size());
//                         List<FarReport>           farUpdateBatch    = new ArrayList<>(validAssets.size());

//                         for (int i = 0; i < validAssets.size(); i++) {
//                             FarReport          asset  = validAssets.get(i);
//                             DepreciationResult result = validResults.get(i);

//                             try {
//                                 DepreciationHistory record = existingMap.getOrDefault(
//                                         asset.getAssetId(), new DepreciationHistory());

//                                 populateRecord(record, asset, result);
//                                 depreciationBatch.add(record);

//                                 applyDepreciationToFarReport(asset, result);
//                                 farUpdateBatch.add(asset);

//                                 chunkProcessed++;
//                             } catch (Exception ex) {
//                                 chunkSkipped++;
//                                 log.error("[DEPRECIATION]   Asset {} populate error: {}",
//                                           asset.getAssetId(), ex.getMessage(), ex);
//                             }
//                         }

//                         // ── Phase 4: Single batch flush to DB ─────────────────
//                         if (!depreciationBatch.isEmpty()) {
//                             depreciationHistoryService.saveAll(depreciationBatch);
//                             farReportService.saveAll(farUpdateBatch);
//                         }
//                     }

//                     long chunkElapsedMs = System.currentTimeMillis() - chunkStartMs;
//                     chunkCount++;
//                     totalBatches++;
//                     totalProcessed += chunkProcessed;
//                     totalSkipped   += chunkSkipped;

//                     log.info("[DEPRECIATION]   Batch {:>3} | size={} ok={} skip={} | flushed in {}ms",
//                              totalBatches, chunk.size(), chunkProcessed, chunkSkipped, chunkElapsedMs);
//                 }

//                 long pageElapsedMs = System.currentTimeMillis() - pageStartMs;
//                 log.info("[DEPRECIATION] ── Page {} complete  batches={}  ok={}  skip={}  ({}ms) ──",
//                          pageNumber + 1, chunkCount, totalProcessed, totalSkipped, pageElapsedMs);

//                 pageNumber++;

//             } while (page.hasNext());

//             long totalElapsedMs = System.currentTimeMillis() - runStartMs;
//             log.info("[DEPRECIATION] ════════════════════════════════════════════════");
//             log.info("[DEPRECIATION] Run COMPLETE");
//             log.info("[DEPRECIATION]   Pages        : {}", pageNumber);
//             log.info("[DEPRECIATION]   Total batches: {}", totalBatches);
//             log.info("[DEPRECIATION]   Processed    : {}", totalProcessed);
//             log.info("[DEPRECIATION]   Skipped      : {}", totalSkipped);
//             log.info("[DEPRECIATION]   Grand total  : {}", totalProcessed + totalSkipped);
//             log.info("[DEPRECIATION]   Elapsed      : {}ms  ({} min)",
//                      totalElapsedMs, totalElapsedMs / 60_000);
//             log.info("[DEPRECIATION] ════════════════════════════════════════════════");

//             return true;

//         } catch (Exception ex) {
//             long elapsedMs = System.currentTimeMillis() - runStartMs;
//             log.error("[DEPRECIATION] ════ FATAL ERROR ════════════════════════════════");
//             log.error("[DEPRECIATION] Run FAILED after {}ms  processed_before_failure={}",
//                       elapsedMs, totalProcessed);
//             log.error("[DEPRECIATION] Cause: {}", ex.getMessage(), ex);
//             return false;
//         }
//     }

//     // ── Depreciation computation ─────────────────────────────────────────────

//     /**
//      * Pure in-memory computation — no DB calls.
//      * Returns null when the asset must be skipped.
//      */
//     private DepreciationResult computeDepreciation(FarReport asset) {
//         if (asset.getCost() == null || asset.getLife() == null || asset.getLife() <= 0
//                 || asset.getDatePlacedInService() == null) {
//             return null;
//         }

//         double initialCost  = asset.getCost();
//         double salvageValue = asset.getSalvageValue() != null ? asset.getSalvageValue() : 0.0;
//         int    usefulLife   = asset.getLife();

//         double monthlyDepreciation = asset.getDepreciationAmount() != null
//                 ? asset.getDepreciationAmount()
//                 : (initialCost - salvageValue) / usefulLife;

//         LocalDate serviceDate = asset.getDatePlacedInService().toInstant()
//                 .atZone(ZoneId.systemDefault()).toLocalDate();
//         LocalDate startDate   = serviceDate.withDayOfMonth(1).plusMonths(1);
//         LocalDate currentDate = LocalDate.now();
//         LocalDate endOfMonth  = currentDate.withDayOfMonth(currentDate.lengthOfMonth());

//         long monthsUtilized = ChronoUnit.MONTHS.between(startDate, endOfMonth);
//         if (monthsUtilized < 0) return null;
//         monthsUtilized = Math.min(monthsUtilized, usefulLife);

//         double accumulatedDepreciation = monthlyDepreciation * monthsUtilized;
//         double netCost                 = initialCost - accumulatedDepreciation;
//         if (netCost <= 0) return null;

//         return new DepreciationResult(
//                 monthlyDepreciation,
//                 accumulatedDepreciation,
//                 netCost,
//                 endOfMonth.format(PERIOD_FORMAT),
//                 LocalDateTime.now()
//         );
//     }

//     /** Immutable value object — decouples compute logic from entity mutation. */
//     private record DepreciationResult(
//             double        monthlyDepreciation,
//             double        accumulatedDepreciation,
//             double        netCost,
//             String        period,
//             LocalDateTime computedAt
//     ) {}

//     // ── Entity population ────────────────────────────────────────────────────

//     private void populateRecord(DepreciationHistory record, FarReport asset,
//                                  DepreciationResult result) {
//         record.setAssetId(asset.getAssetId());
//         record.setDepreciationPeriod(result.period);
//         record.setDepreciationDate(result.computedAt);
//         record.setRecordDatetime(result.computedAt);
//         record.setMonthlyDepreciationAmt(result.monthlyDepreciation);
//         record.setAccumulatedDepreciationAmt(result.accumulatedDepreciation);
//         record.setNetCost(result.netCost);
//         record.setNbv(result.netCost);

//         record.setBook(asset.getBook());
//         record.setQuantity(asset.getQuantity());
//         record.setDescription(asset.getDescription());
//         record.setSerialNumber(asset.getSerialNumber());
//         record.setAssetType(asset.getAssetType());
//         record.setTagNumber(asset.getTagNumber());
//         record.setPicStatus(asset.getPicStatus());
//         record.setAssetStatus(asset.getAssetStatus());
//         record.setValue(asset.getValue());
//         record.setPartNumber(asset.getPartNumber());
//         record.setVendorName(asset.getVendorName());
//         record.setVendorNumber(asset.getVendorNumber());
//         record.setMergedCode(asset.getMergedCode());
//         record.setCostAccount(asset.getCostAccount());
//         record.setAccumulatedDepreAccount(asset.getAccumulatedDepreAccount());
//         record.setCipCostAccount(asset.getCipCostAccount());
//         record.setExpenseCostCenter(asset.getExpenseCostCenter());
//         record.setExpenseAccount(asset.getExpenseAccount());
//         record.setLife(asset.getLife());
//         record.setCost(asset.getCost());
//         record.setDepreciationAmount(asset.getDepreciationAmount());
//         record.setYtdDepreciation(asset.getYtdDepreciation());
//         record.setDepreciationReserve(asset.getDepreciationReserve());
//         record.setSalvageValue(asset.getSalvageValue());
//         record.setCategory(asset.getCategory());
//         record.setCategoryDescription(asset.getCategoryDescription());
//         record.setLocationSegment1(asset.getLocationSegment1());
//         record.setLocationSegment2(asset.getLocationSegment2());
//         record.setLocationSegment3(asset.getLocationSegment3());
//         record.setLocationSegment4(asset.getLocationSegment4());
//         record.setLocations(asset.getLocations());
//         record.setSequenceNumber(asset.getSequenceNumber());
//         record.setStatusFlag(asset.getStatusFlag());
//         record.setNodeType(asset.getNodeType());
//         record.setCreatedBy(asset.getCreatedBy());
//         record.setUpdatedBy(asset.getUpdatedBy());
//         record.setLinkId(asset.getLinkId());
//         record.setAcceptanceNumber(asset.getAcceptanceNumber());
//         record.setDepreciateFlag(asset.getDepreciateFlag());
//         record.setCipEu(asset.getCipEu());
//         record.setInvoiceNumber(asset.getInvoiceNumber());
//         record.setPoNumber(asset.getPoNumber());
//         record.setPoLineNumber(asset.getPoLineNumber());
//         record.setUplLine(asset.getUplLine());
//         record.setTransferToNewFar(asset.getTransferToNewFar());
//         record.setInsertedBy(asset.getInsertedBy());
//         record.setFinancialApproval(asset.getFinancialApproval());
//         record.setChangedBy(asset.getChangedBy());
//         record.setMapped(null);

//         record.setCreationDate(toLocalDateTime(asset.getCreationDate()));
//         record.setDatePlacedInService(toLocalDateTime(asset.getDatePlacedInService()));
//         record.setPicDate(toLocalDateTime(asset.getPicDate()));
//         record.setCipDeliveryDate(toLocalDateTime(asset.getCipDeliveryDate()));
//         record.setCreatedDate(toLocalDateTime(asset.getCreatedDate()));
//         record.setUpdatedDate(toLocalDateTime(asset.getUpdatedDate()));
//         record.setChangedDate(toLocalDateTime(asset.getChangedDate()));
//     }

//     private void applyDepreciationToFarReport(FarReport asset, DepreciationResult result) {
//         asset.setMonthlyDepreciationAmt(result.monthlyDepreciation);
//         asset.setAccumulatedDepreciationAmt(result.accumulatedDepreciation);
//         asset.setNetCost(result.netCost);
//         asset.setDepreciationDate(new Date());
//     }

//     // ── Pre-warm refresh ─────────────────────────────────────────────────────

//     private void refreshPreWarmExport() {
//         try {
//             log.info("[DEPRECIATION] Refreshing pre-warmed exports (depreciation + far_report)");
//             preWarmExportJob.warmSingle("depreciation", ExportFormat.EXCEL);
//             preWarmExportJob.warmSingle("far_report",   ExportFormat.EXCEL);
//         } catch (Exception ex) {
//             log.error("[DEPRECIATION] Pre-warm refresh failed: {}", ex.getMessage(), ex);
//         }
//     }

//     // ── Utility ──────────────────────────────────────────────────────────────

//     private LocalDateTime toLocalDateTime(Date date) {
//         if (date == null) return null;
//         return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
//     }
// }

package com.zain.ksa.alm.financials.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.entity.DepreciationHistory;
import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.service.DepreciationHistoryService;
import com.zain.ksa.alm.financials.service.FarReportService;
import com.zain.ksa.alm.financials.service.impl.PreWarmExportJob;

/**
 * Processes monthly depreciation in database-level batches.
 *
 * FIXES APPLIED:
 * 1. Fully depreciated assets (netCost <= 0) now get a record with monthlyDepreciation=0, netCost=0
 *    Previously: ~948,652 assets were silently dropped every run.
 * 2. Not-yet-in-service assets (monthsUtilized < 0) now get a record with zero depreciation.
 *    Previously: assets placed in service in the current month were dropped.
 * 3. Added diagnostic counters to track exactly why assets are skipped or categorized.
 */
@Component
public class DepreciationScheduler {

    private static final Logger log = LoggerFactory.getLogger(DepreciationScheduler.class);

    /** Rows fetched from DB per JPA page query. Keep ≤ 5 000 to cap ResultSet memory. */
    private static final int PAGE_SIZE = 2_500;

    /**
     * Rows flushed to DB in one batch call.
     * Optimal JDBC batch size is typically 200–500.
     * Must be ≤ PAGE_SIZE.
     */
    private static final int BATCH_SIZE = 500;

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DepreciationHistoryService depreciationHistoryService;
    private final FarReportService           farReportService;
    private final PreWarmExportJob           preWarmExportJob;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public DepreciationScheduler(DepreciationHistoryService depreciationHistoryService,
                                 FarReportService farReportService,
                                 PreWarmExportJob preWarmExportJob) {
        this.depreciationHistoryService = depreciationHistoryService;
        this.farReportService           = farReportService;
        this.preWarmExportJob           = preWarmExportJob;
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── Scheduled entry point ────────────────────────────────────────────────

    @Scheduled(cron = "0 0 0 L * ?", zone = "Africa/Nairobi")
    public void processDepreciation() {
        processDepreciation(true);
    }

    /**
     * @param refreshExportOnSuccess refresh pre-warmed export after a clean run
     * @return true if the run completed without a fatal error
     */
    public boolean processDepreciation(boolean refreshExportOnSuccess) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[DEPRECIATION] Run skipped — another run is already in progress");
            return false;
        }

        boolean success = false;
        try {
            success = runDepreciationBatch();
        } finally {
            running.set(false);
            if (success && refreshExportOnSuccess) {
                refreshPreWarmExport();
            }
        }

        return success;
    }

    // ── Core batch loop ──────────────────────────────────────────────────────

    private boolean runDepreciationBatch() {
        log.info("[DEPRECIATION] ════════════════════════════════════════════════");
        log.info("[DEPRECIATION] Run started  page_size={}  batch_size={}",
                 PAGE_SIZE, BATCH_SIZE);
        log.info("[DEPRECIATION] ════════════════════════════════════════════════");

        int  pageNumber     = 0;
        long totalProcessed = 0;
        long totalSkipped   = 0;
        long totalBatches   = 0;
        long runStartMs     = System.currentTimeMillis();

        // ── Diagnostic counters ──────────────────────────────────────────
        long totalSkippedNullCost       = 0;
        long totalSkippedNullLife       = 0;
        long totalSkippedNullService    = 0;
        long totalFullyDepreciated      = 0;
        long totalNotYetInService       = 0;
        long totalActivelyDepreciating  = 0;

        try {
            Page<FarReport> page;

            do {
                Pageable pageable    = PageRequest.of(pageNumber, PAGE_SIZE);
                long     pageStartMs = System.currentTimeMillis();

                page = farReportService.findAll(pageable);

                log.info("[DEPRECIATION] ── Page {}/{} fetched  assets={}  ({}ms) ──",
                         pageNumber + 1,
                         page.getTotalPages(),
                         page.getContent().size(),
                         System.currentTimeMillis() - pageStartMs);

                // ── Split page into BATCH_SIZE chunks ────────────────────────
                List<FarReport> assets     = page.getContent();
                int             chunkCount = 0;

                for (int chunkStart = 0; chunkStart < assets.size(); chunkStart += BATCH_SIZE) {
                    int             chunkEnd     = Math.min(chunkStart + BATCH_SIZE, assets.size());
                    List<FarReport> chunk        = assets.subList(chunkStart, chunkEnd);
                    long            chunkStartMs = System.currentTimeMillis();

                    // ── Phase 1: Compute depreciation in memory ───────────────
                    List<FarReport>          validAssets  = new ArrayList<>(chunk.size());
                    List<DepreciationResult> validResults = new ArrayList<>(chunk.size());
                    int                      chunkSkipped = 0;

                    for (FarReport asset : chunk) {
                        try {
                            // Pre-check to categorize skips with diagnostics
                            if (asset.getCost() == null) {
                                chunkSkipped++;
                                totalSkippedNullCost++;
                                continue;
                            }
                            if (asset.getLife() == null || asset.getLife() <= 0) {
                                chunkSkipped++;
                                totalSkippedNullLife++;
                                continue;
                            }
                            if (asset.getDatePlacedInService() == null) {
                                chunkSkipped++;
                                totalSkippedNullService++;
                                continue;
                            }

                            DepreciationResult result = computeDepreciation(asset);
                            if (result == null) {
                                // Should not happen after Gate 1 pre-checks, but safety net
                                chunkSkipped++;
                                continue;
                            }

                            // Track categories (for logging, not skipping)
                            if (result.netCost() <= 0) {
                                totalFullyDepreciated++;
                            } else if (result.monthlyDepreciation() == 0.0 && result.accumulatedDepreciation() == 0.0) {
                                totalNotYetInService++;
                            } else {
                                totalActivelyDepreciating++;
                            }

                            validAssets.add(asset);
                            validResults.add(result);
                        } catch (Exception ex) {
                            chunkSkipped++;
                            log.error("[DEPRECIATION]   Asset {} compute error: {}",
                                      asset.getAssetId(), ex.getMessage(), ex);
                        }
                    }

                    int chunkProcessed = 0;

                    if (!validAssets.isEmpty()) {
                        // ── Phase 2: ONE bulk query for existing records ──────
                        String period = validResults.get(0).period();
                        List<String> assetIds = validAssets.stream()
                                .map(FarReport::getAssetId)
                                .collect(Collectors.toList());

                        Map<String, DepreciationHistory> existingMap =
                                depreciationHistoryService.findByAssetIdsAndPeriod(assetIds, period)
                                        .stream()
                                        .collect(Collectors.toMap(
                                                DepreciationHistory::getAssetId,
                                                d -> d,
                                                (a, b) -> a));   // keep first if duplicates

                        // ── Phase 3: Build batch lists ────────────────────────
                        List<DepreciationHistory> depreciationBatch = new ArrayList<>(validAssets.size());
                        List<FarReport>           farUpdateBatch    = new ArrayList<>(validAssets.size());

                        for (int i = 0; i < validAssets.size(); i++) {
                            FarReport          asset  = validAssets.get(i);
                            DepreciationResult result = validResults.get(i);

                            try {
                                DepreciationHistory record = existingMap.getOrDefault(
                                        asset.getAssetId(), new DepreciationHistory());

                                populateRecord(record, asset, result);
                                depreciationBatch.add(record);

                                applyDepreciationToFarReport(asset, result);
                                farUpdateBatch.add(asset);

                                chunkProcessed++;
                            } catch (Exception ex) {
                                chunkSkipped++;
                                log.error("[DEPRECIATION]   Asset {} populate error: {}",
                                          asset.getAssetId(), ex.getMessage(), ex);
                            }
                        }

                        // ── Phase 4: Single batch flush to DB ─────────────────
                        if (!depreciationBatch.isEmpty()) {
                            depreciationHistoryService.saveAll(depreciationBatch);
                            farReportService.saveAll(farUpdateBatch);
                        }
                    }

                    long chunkElapsedMs = System.currentTimeMillis() - chunkStartMs;
                    chunkCount++;
                    totalBatches++;
                    totalProcessed += chunkProcessed;
                    totalSkipped   += chunkSkipped;

                    log.info("[DEPRECIATION]   Batch {:>3} | size={} ok={} skip={} | flushed in {}ms",
                             totalBatches, chunk.size(), chunkProcessed, chunkSkipped, chunkElapsedMs);
                }

                long pageElapsedMs = System.currentTimeMillis() - pageStartMs;
                log.info("[DEPRECIATION] ── Page {} complete  batches={}  ok={}  skip={}  ({}ms) ──",
                         pageNumber + 1, chunkCount, totalProcessed, totalSkipped, pageElapsedMs);

                pageNumber++;

            } while (page.hasNext());

            long totalElapsedMs = System.currentTimeMillis() - runStartMs;
            log.info("[DEPRECIATION] ════════════════════════════════════════════════");
            log.info("[DEPRECIATION] Run COMPLETE");
            log.info("[DEPRECIATION]   Pages                : {}", pageNumber);
            log.info("[DEPRECIATION]   Total batches        : {}", totalBatches);
            log.info("[DEPRECIATION]   Processed            : {}", totalProcessed);
            log.info("[DEPRECIATION]   Skipped              : {}", totalSkipped);
            log.info("[DEPRECIATION]   Grand total          : {}", totalProcessed + totalSkipped);
            log.info("[DEPRECIATION]   ── Skip breakdown ──");
            log.info("[DEPRECIATION]   Null cost            : {}", totalSkippedNullCost);
            log.info("[DEPRECIATION]   Null/zero life       : {}", totalSkippedNullLife);
            log.info("[DEPRECIATION]   Null service date    : {}", totalSkippedNullService);
            log.info("[DEPRECIATION]   ── Category breakdown ──");
            log.info("[DEPRECIATION]   Actively depreciating: {}", totalActivelyDepreciating);
            log.info("[DEPRECIATION]   Fully depreciated    : {}", totalFullyDepreciated);
            log.info("[DEPRECIATION]   Not yet in service   : {}", totalNotYetInService);
            log.info("[DEPRECIATION]   Elapsed              : {}ms  ({} min)",
                     totalElapsedMs, totalElapsedMs / 60_000);
            log.info("[DEPRECIATION] ════════════════════════════════════════════════");

            return true;

        } catch (Exception ex) {
            long elapsedMs = System.currentTimeMillis() - runStartMs;
            log.error("[DEPRECIATION] ════ FATAL ERROR ════════════════════════════════");
            log.error("[DEPRECIATION] Run FAILED after {}ms  processed_before_failure={}",
                      elapsedMs, totalProcessed);
            log.error("[DEPRECIATION] Cause: {}", ex.getMessage(), ex);
            return false;
        }
    }

    // ── Depreciation computation ─────────────────────────────────────────────

    /**
     * Pure in-memory computation — no DB calls.
     *
     * Returns null ONLY when the asset has genuinely invalid data (missing cost/life/serviceDate).
     * Gate 1 pre-checks in the caller handle those cases with diagnostic logging.
     *
     * Fully depreciated assets get a record with monthlyDepreciation=0, netCost=0.
     * Not-yet-in-service assets get a record with zero depreciation, netCost=initialCost.
     *
     * FIXES:
     * - Gate 2 (monthsUtilized < 0): Was return null → now returns zero-depreciation record
     * - Gate 3 (netCost <= 0):       Was return null → now returns record with netCost=0, monthly=0
     */
    private DepreciationResult computeDepreciation(FarReport asset) {
        // Gate 1: Genuinely unusable data — only valid reason to skip
        // (Pre-checked in caller with diagnostics, but kept here as safety net)
        if (asset.getCost() == null || asset.getLife() == null || asset.getLife() <= 0
                || asset.getDatePlacedInService() == null) {
            return null;
        }

        double initialCost  = asset.getCost();
        double salvageValue = asset.getSalvageValue() != null ? asset.getSalvageValue() : 0.0;
        int    usefulLife   = asset.getLife();

        double monthlyDepreciation = asset.getDepreciationAmount() != null
                ? asset.getDepreciationAmount()
                : (initialCost - salvageValue) / usefulLife;

        LocalDate serviceDate = asset.getDatePlacedInService().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate startDate   = serviceDate.withDayOfMonth(1).plusMonths(1);
        LocalDate currentDate = LocalDate.now();
        LocalDate endOfMonth  = currentDate.withDayOfMonth(currentDate.lengthOfMonth());

        long monthsUtilized = ChronoUnit.MONTHS.between(startDate, endOfMonth);

        // FIX (Gate 2): Not yet in service — record with zero depreciation
        if (monthsUtilized < 0) {
            return new DepreciationResult(
                    0.0,
                    0.0,
                    initialCost,
                    endOfMonth.format(PERIOD_FORMAT),
                    LocalDateTime.now()
            );
        }

        monthsUtilized = Math.min(monthsUtilized, usefulLife);

        double accumulatedDepreciation = monthlyDepreciation * monthsUtilized;
        double netCost = Math.max(initialCost - accumulatedDepreciation, 0.0);

        // FIX (Gate 3): Fully depreciated — record with zero monthly, netCost clamped to 0
        double effectiveMonthly = (netCost <= 0) ? 0.0 : monthlyDepreciation;

        return new DepreciationResult(
                effectiveMonthly,
                accumulatedDepreciation,
                netCost,
                endOfMonth.format(PERIOD_FORMAT),
                LocalDateTime.now()
        );
    }

    /** Immutable value object — decouples compute logic from entity mutation. */
    private record DepreciationResult(
            double        monthlyDepreciation,
            double        accumulatedDepreciation,
            double        netCost,
            String        period,
            LocalDateTime computedAt
    ) {}

    // ── Entity population ────────────────────────────────────────────────────

    private void populateRecord(DepreciationHistory record, FarReport asset,
                                 DepreciationResult result) {
        record.setAssetId(asset.getAssetId());
        record.setDepreciationPeriod(result.period);
        record.setDepreciationDate(result.computedAt);
        record.setRecordDatetime(result.computedAt);
        record.setMonthlyDepreciationAmt(result.monthlyDepreciation);
        record.setAccumulatedDepreciationAmt(result.accumulatedDepreciation);
        record.setNetCost(result.netCost);
        record.setNbv(result.netCost);

        record.setBook(asset.getBook());
        record.setQuantity(asset.getQuantity());
        record.setDescription(asset.getDescription());
        record.setSerialNumber(asset.getSerialNumber());
        record.setAssetType(asset.getAssetType());
        record.setTagNumber(asset.getTagNumber());
        record.setPicStatus(asset.getPicStatus());
        record.setAssetStatus(asset.getAssetStatus());
        record.setValue(asset.getValue());
        record.setPartNumber(asset.getPartNumber());
        record.setVendorName(asset.getVendorName());
        record.setVendorNumber(asset.getVendorNumber());
        record.setMergedCode(asset.getMergedCode());
        record.setCostAccount(asset.getCostAccount());
        record.setAccumulatedDepreAccount(asset.getAccumulatedDepreAccount());
        record.setCipCostAccount(asset.getCipCostAccount());
        record.setExpenseCostCenter(asset.getExpenseCostCenter());
        record.setExpenseAccount(asset.getExpenseAccount());
        record.setLife(asset.getLife());
        record.setCost(asset.getCost());
        record.setDepreciationAmount(asset.getDepreciationAmount());
        record.setYtdDepreciation(asset.getYtdDepreciation());
        record.setDepreciationReserve(asset.getDepreciationReserve());
        record.setSalvageValue(asset.getSalvageValue());
        record.setCategory(asset.getCategory());
        record.setCategoryDescription(asset.getCategoryDescription());
        record.setLocationSegment1(asset.getLocationSegment1());
        record.setLocationSegment2(asset.getLocationSegment2());
        record.setLocationSegment3(asset.getLocationSegment3());
        record.setLocationSegment4(asset.getLocationSegment4());
        record.setLocations(asset.getLocations());
        record.setSequenceNumber(asset.getSequenceNumber());
        record.setStatusFlag(asset.getStatusFlag());
        record.setNodeType(asset.getNodeType());
        record.setCreatedBy(asset.getCreatedBy());
        record.setUpdatedBy(asset.getUpdatedBy());
        record.setLinkId(asset.getLinkId());
        record.setAcceptanceNumber(asset.getAcceptanceNumber());
        record.setDepreciateFlag(asset.getDepreciateFlag());
        record.setCipEu(asset.getCipEu());
        record.setInvoiceNumber(asset.getInvoiceNumber());
        record.setPoNumber(asset.getPoNumber());
        record.setPoLineNumber(asset.getPoLineNumber());
        record.setUplLine(asset.getUplLine());
        record.setTransferToNewFar(asset.getTransferToNewFar());
        record.setInsertedBy(asset.getInsertedBy());
        record.setFinancialApproval(asset.getFinancialApproval());
        record.setChangedBy(asset.getChangedBy());
        record.setMapped(null);

        record.setCreationDate(toLocalDateTime(asset.getCreationDate()));
        record.setDatePlacedInService(toLocalDateTime(asset.getDatePlacedInService()));
        record.setPicDate(toLocalDateTime(asset.getPicDate()));
        record.setCipDeliveryDate(toLocalDateTime(asset.getCipDeliveryDate()));
        record.setCreatedDate(toLocalDateTime(asset.getCreatedDate()));
        record.setUpdatedDate(toLocalDateTime(asset.getUpdatedDate()));
        record.setChangedDate(toLocalDateTime(asset.getChangedDate()));
    }

    private void applyDepreciationToFarReport(FarReport asset, DepreciationResult result) {
        asset.setMonthlyDepreciationAmt(result.monthlyDepreciation);
        asset.setAccumulatedDepreciationAmt(result.accumulatedDepreciation);
        asset.setNetCost(result.netCost);
        asset.setDepreciationDate(new Date());
    }

    // ── Pre-warm refresh ─────────────────────────────────────────────────────

    private void refreshPreWarmExport() {
        try {
            log.info("[DEPRECIATION] Refreshing pre-warmed exports (depreciation + far_report)");
            preWarmExportJob.warmSingle("depreciation", ExportFormat.EXCEL);
            preWarmExportJob.warmSingle("far_report",   ExportFormat.EXCEL);
        } catch (Exception ex) {
            log.error("[DEPRECIATION] Pre-warm refresh failed: {}", ex.getMessage(), ex);
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}