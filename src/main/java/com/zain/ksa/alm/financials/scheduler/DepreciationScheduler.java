package com.zain.ksa.alm.financials.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.zain.ksa.alm.financials.repository.DepreciationHistoryBulkRepository;
import com.zain.ksa.alm.financials.repository.FarReportBulkRepository;
import com.zain.ksa.alm.financials.service.FarReportService;
import com.zain.ksa.alm.financials.service.impl.PreWarmExportJob;

/**
 * Monthly depreciation scheduler — Straight Line Depreciation model.
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  FORMULA (from Financial Management: Capitalisation / Depreciation)  │
 * │                                                                      │
 * │  MD   = (IC − SalvageValue) / L          [L = useful life in months] │
 * │  D    = 1st of the NEXT calendar month from Date of Service          │
 * │  NoMU = months between D and end-of-current-month (capped at L)      │
 * │  AD   = MD × NoMU                                                    │
 * │  NC   = IC − AD                                                      │
 * │                                                                      │
 * │  Constraints:                                                        │
 * │  • NC  ≥ SalvageValue  (never negative or below salvage)            │
 * │  • AD  ≤ (IC − SalvageValue)                                        │
 * │  • IC, MD, AD rounded to 3 decimal places                           │
 * │  • Stop computing when NC == SalvageValue                           │
 * │                                                                      │
 * │  NOTE: depreciationReserve (DEPRN_RESERVE) is intentionally excluded │
 * │  from AD calculation. It represents the full historical accumulated  │
 * │  depreciation imported from ERP — not a spec ADJ correction value.  │
 * │  Including it in AD = MD × NoMU + ADJ caused massive double-counting │
 * │  (~4.7bn excess depreciation on 3.1M assets).                       │
 * └──────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * ── Mid-month cutoff rule ────────────────────────────────────────────────────
 *
 *  Assets whose creationDate falls on day 1–15 of the current month are
 *  included in the current month's depreciation run.
 *
 *  Assets whose creationDate falls on day 16–end of the current month are
 *  deferred: they are skipped entirely this run and will be picked up
 *  automatically in the following month's run (their creationDate will then
 *  be in a prior month, so day-of-month check no longer applies).
 *
 *  Assets created in any prior month are always included regardless of day.
 *
 *  Logic in computeDepreciation():
 *    LocalDate creation = toLocalDate(asset.getCreationDate())
 *    if (creation.getYear()  == today.getYear()  &&
 *        creation.getMonth() == today.getMonth() &&
 *        creation.getDayOfMonth() > MID_MONTH_CUTOFF)   → return null (skip)
 *
 *    uk_dh_asset_period (assetId, depreciationPeriod).
 */
@Component
public class DepreciationScheduler {

    private static final Logger log = LoggerFactory.getLogger(DepreciationScheduler.class);

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    /** Precision scale as per spec: 3 decimal places for IC, MD, AD. */
    private static final int          SCALE    = 3;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final int PAGE_SIZE  = 10_000;
    private static final int BATCH_SIZE =  2_000;

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Assets created on day 1–15 are included in the current month's run.
     * Assets created on day 16 or later are deferred to the following month.
     */
    private static final int MID_MONTH_CUTOFF = 15;

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final FarReportService                  farReportService;
    private final PreWarmExportJob                  preWarmExportJob;
    private final DepreciationHistoryBulkRepository depHistoryBulkRepo;
    private final FarReportBulkRepository           farReportBulkRepo;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public DepreciationScheduler(FarReportService                  farReportService,
                                  PreWarmExportJob                  preWarmExportJob,
                                  DepreciationHistoryBulkRepository depHistoryBulkRepo,
                                  FarReportBulkRepository           farReportBulkRepo) {
        this.farReportService   = farReportService;
        this.preWarmExportJob   = preWarmExportJob;
        this.depHistoryBulkRepo = depHistoryBulkRepo;
        this.farReportBulkRepo  = farReportBulkRepo;
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── Scheduled entry point ────────────────────────────────────────────────

    @Scheduled(cron = "${app.scheduler.monthly-job-cron:0 0 0 L * ?}", zone = "Asia/Riyadh")
    public void processDepreciation() {
        processDepreciation(true);
    }

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
        log.info("[DEPRECIATION] ══════════════════════════════════════════════════");
        log.info("[DEPRECIATION] Run started  page_size={}  batch_size={}  mid_month_cutoff=day{}",
                 PAGE_SIZE, BATCH_SIZE, MID_MONTH_CUTOFF);

        int  pageNumber     = 0;
        long totalProcessed = 0;
        long totalSkipped   = 0;
        long totalDeferred  = 0;   // assets skipped specifically due to mid-month cutoff
        long totalBatches   = 0;
        long runStartMs     = System.currentTimeMillis();

        // Compute period + timestamp ONCE per run — consistent across all batches
        LocalDate     today      = LocalDate.now(RIYADH);
        LocalDate     endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        String        period     = endOfMonth.format(PERIOD_FORMAT);
        LocalDateTime computedAt = LocalDateTime.now(RIYADH);

        // Single Date instance reused for all FarReport.depreciationDate writes
        Date runDate = Date.from(computedAt.atZone(RIYADH).toInstant());

        log.info("[DEPRECIATION] Period={}, EndOfMonth={}, RunDate={}",
                 period, endOfMonth, computedAt);
        log.info("[DEPRECIATION] Mid-month rule: assets with creationDate in {}/{} day > {} → DEFERRED",
                 today.getYear(), today.getMonthValue(), MID_MONTH_CUTOFF);

        try {
            Page<FarReport> page;
            do {
                Pageable pageable    = PageRequest.of(pageNumber, PAGE_SIZE);
                long     pageStartMs = System.currentTimeMillis();

                page = farReportService.findAll(pageable);
                log.info("[DEPRECIATION] ── Page {}/{} fetched  assets={}  ({}ms) ──",
                         pageNumber + 1, page.getTotalPages(),
                         page.getContent().size(),
                         System.currentTimeMillis() - pageStartMs);

                List<FarReport> assets     = page.getContent();
                int             chunkCount = 0;

                for (int chunkStart = 0; chunkStart < assets.size(); chunkStart += BATCH_SIZE) {
                    int             chunkEnd     = Math.min(chunkStart + BATCH_SIZE, assets.size());
                    List<FarReport> chunk        = assets.subList(chunkStart, chunkEnd);
                    long            chunkStartMs = System.currentTimeMillis();

                    // ── Phase 1: compute in memory ────────────────────────────
                    List<FarReport>          validAssets   = new ArrayList<>(chunk.size());
                    List<DepreciationResult> validResults  = new ArrayList<>(chunk.size());
                    int chunkSkipped   = 0;
                    int chunkDeferred  = 0;
                    int chunkProcessed = 0;

                    for (FarReport asset : chunk) {
                        try {
                            DepreciationResult result =
                                    computeDepreciation(asset, period, endOfMonth, computedAt, today);

                            if (result == null) {
                                // Distinguish deferred (mid-month) from other skips for logging
                                if (isDeferredThisMonth(asset, today)) {
                                    chunkDeferred++;
                                } else {
                                    chunkSkipped++;
                                }
                                continue;
                            }
                            validAssets.add(asset);
                            validResults.add(result);
                        } catch (Exception ex) {
                            chunkSkipped++;
                            log.error("[DEPRECIATION] Asset {} compute error: {}",
                                      asset.getAssetId(), ex.getMessage(), ex);
                        }
                    }

                    if (!validAssets.isEmpty()) {
                        // ── Phase 2: apply results into entity objects (memory) ─
                        List<DepreciationHistory> depBatch = new ArrayList<>(validAssets.size());

                        for (int i = 0; i < validAssets.size(); i++) {
                            FarReport          asset  = validAssets.get(i);
                            DepreciationResult result = validResults.get(i);
                            try {
                                DepreciationHistory record = new DepreciationHistory();
                                populateRecord(record, asset, result);
                                depBatch.add(record);

                                applyDepreciationToFarReport(asset, result, runDate);
                                chunkProcessed++;
                            } catch (Exception ex) {
                                chunkSkipped++;
                                log.error("[DEPRECIATION] Asset {} populate error: {}",
                                          asset.getAssetId(), ex.getMessage(), ex);
                            }
                        }

                        // ── Phase 3: single-round-trip bulk writes ────────────
                        depHistoryBulkRepo.upsertAll(depBatch);
                        farReportBulkRepo.bulkUpdateDepreciation(validAssets, runDate);
                    }

                    chunkCount++;
                    totalBatches++;
                    totalProcessed += chunkProcessed;
                    totalSkipped   += chunkSkipped;
                    totalDeferred  += chunkDeferred;

                    log.info("[DEPRECIATION] Batch {} | size={} ok={} skip={} deferred={} | {}ms",
                             totalBatches, chunk.size(), chunkProcessed, chunkSkipped, chunkDeferred,
                             System.currentTimeMillis() - chunkStartMs);
                }

                log.info("[DEPRECIATION] ── Page {} complete  batches={}  ok={}  skip={}  deferred={} ──",
                         pageNumber + 1, chunkCount, totalProcessed, totalSkipped, totalDeferred);
                pageNumber++;

            } while (page.hasNext());

            long elapsed = System.currentTimeMillis() - runStartMs;
            log.info("[DEPRECIATION] Run COMPLETE  pages={}  batches={}  processed={}  skipped={}  deferred={}  elapsed={}ms",
                     pageNumber, totalBatches, totalProcessed, totalSkipped, totalDeferred, elapsed);
            return true;

        } catch (Exception ex) {
            log.error("[DEPRECIATION] FATAL after {}ms  processed_before_failure={}",
                      System.currentTimeMillis() - runStartMs, totalProcessed);
            log.error("[DEPRECIATION] Cause: {}", ex.getMessage(), ex);
            return false;
        }
    }

    // ── Depreciation computation ─────────────────────────────────────────────

    /**
     * Straight-line depreciation per spec — ADJ removed.
     *
     * <pre>
     *  Mandatory fields  : cost (IC), life (L), datePlacedInService
     *
     *  Step 0  Mid-month cutoff check (NEW):
     *          If asset.creationDate is in the CURRENT run month AND
     *          day-of-month > MID_MONTH_CUTOFF (15) → return null (deferred).
     *          Assets created in any prior month are always included.
     *
     *  Step 1  IC   = asset.cost                     (rounded to 3dp)
     *  Step 2  SV   = asset.salvageValue ?? 0         (rounded to 3dp)
     *  Step 3  MD   = (IC − SV) / L                  (rounded to 3dp)
     *  Step 4  D    = 1st of month AFTER datePlacedInService
     *  Step 5  NoMU = months(D → endOfCurrentMonth), capped at L; skip if &lt; 0
     *  Step 6  AD   = MD × NoMU                      (capped at IC − SV)
     *  Step 7  NC   = IC − AD                        (floored at SV, never &lt; 0)
     *  Step 8  Skip if NC == SV (fully depreciated)
     *
     *  depreciationReserve (DEPRN_RESERVE) is intentionally NOT used here.
     * </pre>
     */
    private DepreciationResult computeDepreciation(FarReport     asset,
                                                    String        period,
                                                    LocalDate     endOfMonth,
                                                    LocalDateTime computedAt,
                                                    LocalDate     today) {

        // ── Step 0: mid-month cutoff — skip assets created after day 15 ──────
        //
        //  Rule: if the asset was created THIS calendar month and the creation
        //  day is > MID_MONTH_CUTOFF, defer it to next month's run.
        //  Assets created in any prior month are unaffected by this check.
        if (isDeferredThisMonth(asset, today)) {
            log.debug("[DEPRECIATION] Defer {} — creationDate {} is in current month after day {}",
                      asset.getAssetId(),
                      asset.getCreationDate() != null ? asset.getCreationDate() : "null",
                      MID_MONTH_CUTOFF);
            return null;
        }

        // ── Guard: mandatory fields ──────────────────────────────────────────
        if (asset.getCost() == null
                || asset.getLife() == null || asset.getLife() <= 0
                || asset.getDatePlacedInService() == null) {
            log.debug("[DEPRECIATION] Skip {} — missing mandatory field(s)", asset.getAssetId());
            return null;
        }

        // ── Step 1-2: core values (3dp precision) ────────────────────────────
        BigDecimal ic = bd(asset.getCost());
        BigDecimal sv = asset.getSalvageValue() != null
                        ? bd(asset.getSalvageValue())
                        : BigDecimal.ZERO;

        int L = asset.getLife();

        // ── Step 3: MD = (IC − SV) / L (3dp) ────────────────────────────────
        BigDecimal depreciableAmount = ic.subtract(sv);
        if (depreciableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("[DEPRECIATION] Skip {} — IC ({}) <= SV ({})", asset.getAssetId(), ic, sv);
            return null;
        }
        BigDecimal md = depreciableAmount.divide(BigDecimal.valueOf(L), SCALE, ROUNDING);

        // ── Step 4: D = 1st of next calendar month after Date of Service ─────
        LocalDate serviceDate = asset.getDatePlacedInService()
                .toInstant().atZone(RIYADH).toLocalDate();
        LocalDate D = serviceDate.withDayOfMonth(1).plusMonths(1);

        // ── Step 5: NoMU = months(D → endOfCurrentMonth), capped at L ────────
        long nomu = ChronoUnit.MONTHS.between(D, endOfMonth);
        if (nomu < 0) {
            log.debug("[DEPRECIATION] Skip {} — service date {} is in the future (D={})",
                      asset.getAssetId(), serviceDate, D);
            return null;
        }
        nomu = Math.min(nomu, L);

        // ── Step 6: AD = MD × NoMU (capped at IC − SV, NO ADJ) ───────────────
        BigDecimal ad    = md.multiply(BigDecimal.valueOf(nomu)).setScale(SCALE, ROUNDING);
        BigDecimal maxAD = depreciableAmount;
        if (ad.compareTo(maxAD) > 0) {
            ad = maxAD;
        }

        // ── Step 7: NC = IC − AD (floored at SV, never negative) ─────────────
        BigDecimal nc = ic.subtract(ad).setScale(SCALE, ROUNDING);
        if (nc.compareTo(sv) < 0) {
            nc = sv;
            ad = ic.subtract(sv).setScale(SCALE, ROUNDING);
        }
        if (nc.compareTo(BigDecimal.ZERO) < 0) {
            nc = BigDecimal.ZERO;
        }

        // ── Step 8: Stop when fully depreciated (NC == SV) ───────────────────
        if (nc.compareTo(sv) == 0 && nomu >= L) {
            log.debug("[DEPRECIATION] Skip {} — fully depreciated (NC == SV == {})",
                      asset.getAssetId(), sv);
            return null;
        }

        // ── Date of Asset Retirement: DoAR = (D + L) − 1 day ────────────────
        LocalDate doar = D.plusMonths(L).minusDays(1);

        return new DepreciationResult(
                md.doubleValue(),
                ad.doubleValue(),
                nc.doubleValue(),
                ic.doubleValue(),
                sv.doubleValue(),
                doar,
                period,
                computedAt
        );
    }

    /**
     * Returns true if this asset must be deferred to the next month's run.
     *
     * Deferral condition:
     *   asset.creationDate is in the same year+month as {@code today}
     *   AND day-of-month > {@value #MID_MONTH_CUTOFF}
     *
     * Assets with a null creationDate are NOT deferred — they fall through
     * to the normal mandatory-field guard in computeDepreciation().
     */
    private boolean isDeferredThisMonth(FarReport asset, LocalDate today) {
        if (asset.getCreationDate() == null) {
            return false;
        }
        LocalDate created = asset.getCreationDate()
                .toInstant().atZone(RIYADH).toLocalDate();

        return created.getYear()        == today.getYear()
            && created.getMonth()       == today.getMonth()
            && created.getDayOfMonth()  >  MID_MONTH_CUTOFF;
    }

    /**
     * Immutable value object carrying all computed fields for one asset.
     */
    private record DepreciationResult(
            double        monthlyDepreciation,      // MD  (3dp)
            double        accumulatedDepreciation,  // AD  (3dp)
            double        netCost,                  // NC  (3dp)
            double        initialCost,              // IC  (3dp)
            double        salvageValue,             // SV  (3dp)
            LocalDate     dateOfAssetRetirement,    // DoAR
            String        period,
            LocalDateTime computedAt
    ) {}

    // ── Entity population ────────────────────────────────────────────────────

    private void populateRecord(DepreciationHistory record,
                                 FarReport           asset,
                                 DepreciationResult  result) {
        record.setAssetId(asset.getAssetId());
        record.setDepreciationPeriod(result.period());
        record.setMonthlyDepreciationAmt(result.monthlyDepreciation());
        record.setAccumulatedDepreciationAmt(result.accumulatedDepreciation());
        record.setNetCost(result.netCost());
        record.setDepreciationDate(result.computedAt());
        record.setRecordDatetime(result.computedAt());
        record.setCreatedBy(asset.getCreatedBy());
        record.setChangedBy(asset.getChangedBy());
    }

    /**
     * Mutates FarReport in memory with computed depreciation values.
     * Uses single runDate for all assets — not new Date() per asset.
     */
    private void applyDepreciationToFarReport(FarReport          asset,
                                               DepreciationResult result,
                                               Date               runDate) {
        asset.setCost(result.initialCost());
        asset.setSalvageValue(result.salvageValue());
        asset.setDepreciationAmount(result.monthlyDepreciation());
        asset.setMonthlyDepreciationAmt(result.monthlyDepreciation());
        asset.setAccumulatedDepreciationAmt(result.accumulatedDepreciation());
        asset.setNetCost(result.netCost());
        asset.setNbv(result.netCost());
        asset.setDepreciationDate(runDate);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    // ── Pre-warm refresh ─────────────────────────────────────────────────────

    private void refreshPreWarmExport() {
        try {
            log.info("[DEPRECIATION] Refreshing pre-warmed exports");
            preWarmExportJob.warmSingle("depreciation", ExportFormat.EXCEL);
            preWarmExportJob.warmSingle("far_report",   ExportFormat.EXCEL);
        } catch (Exception ex) {
            log.error("[DEPRECIATION] Pre-warm refresh failed: {}", ex.getMessage(), ex);
        }
    }
}