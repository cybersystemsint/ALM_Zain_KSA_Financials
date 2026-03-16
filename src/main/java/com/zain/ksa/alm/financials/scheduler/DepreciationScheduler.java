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
 * │  AD   = MD × NoMU + ADJ                                              │
 * │  NC   = IC − AD                                                      │
 * │                                                                      │
 * │  Constraints:                                                        │
 * │  • NC  ≥ SalvageValue  (never negative or below salvage)            │
 * │  • AD  ≤ (IC − SalvageValue)                                        │
 * │  • ADJ < IC at all times                                             │
 * │  • IC, MD, AD rounded to 3 decimal places                           │
 * │  • Stop computing when NC == SalvageValue                           │
 * └──────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>Performance design:</b>
 * <ul>
 *   <li>Assets are fetched in pages of {@value #PAGE_SIZE} via JPA (read-only).</li>
 *   <li>Each page is split into chunks of {@value #BATCH_SIZE}.</li>
 *   <li>Per chunk: compute in-memory → single bulk upsert (DepreciationHistory)
 *       → single bulk update (FarReport). No per-row SELECT before write.</li>
 *   <li>The {@code existingMap} fetch from the old design is eliminated;
 *       upsert correctness is enforced by the DB unique constraint
 *       {@code uk_dh_asset_period (assetId, depreciationPeriod)}.</li>
 * </ul>
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

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final FarReportService                farReportService;
    private final PreWarmExportJob                preWarmExportJob;
    private final DepreciationHistoryBulkRepository depHistoryBulkRepo;
    private final FarReportBulkRepository           farReportBulkRepo;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public DepreciationScheduler(FarReportService                farReportService,
                                  PreWarmExportJob                preWarmExportJob,
                                  DepreciationHistoryBulkRepository depHistoryBulkRepo,
                                  FarReportBulkRepository           farReportBulkRepo) {
        this.farReportService    = farReportService;
        this.preWarmExportJob    = preWarmExportJob;
        this.depHistoryBulkRepo  = depHistoryBulkRepo;
        this.farReportBulkRepo   = farReportBulkRepo;
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
        log.info("[DEPRECIATION] Run started  page_size={}  batch_size={}", PAGE_SIZE, BATCH_SIZE);

        int  pageNumber     = 0;
        long totalProcessed = 0;
        long totalSkipped   = 0;
        long totalBatches   = 0;
        long runStartMs     = System.currentTimeMillis();

        // Compute period + timestamp ONCE per run — consistent across all batches
        LocalDate     today      = LocalDate.now(RIYADH);
        LocalDate     endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        String        period     = endOfMonth.format(PERIOD_FORMAT);
        LocalDateTime computedAt = LocalDateTime.now(RIYADH);

        // Single Date instance reused for all FarReport.depreciationDate writes
        Date runDate = Date.from(computedAt.atZone(RIYADH).toInstant());

        log.info("[DEPRECIATION] Period={}, EndOfMonth={}", period, endOfMonth);

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
                    List<FarReport>          validAssets  = new ArrayList<>(chunk.size());
                    List<DepreciationResult> validResults = new ArrayList<>(chunk.size());
                    int chunkSkipped   = 0;
                    int chunkProcessed = 0;

                    for (FarReport asset : chunk) {
                        try {
                            DepreciationResult result =
                                    computeDepreciation(asset, period, endOfMonth, computedAt);
                            if (result == null) {
                                chunkSkipped++;
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
                                // Build DepreciationHistory record (new — no pre-fetch needed)
                                DepreciationHistory record = new DepreciationHistory();
                                populateRecord(record, asset, result);
                                depBatch.add(record);

                                // Mutate FarReport in memory
                                applyDepreciationToFarReport(asset, result, runDate);
                                chunkProcessed++;
                            } catch (Exception ex) {
                                chunkSkipped++;
                                log.error("[DEPRECIATION] Asset {} populate error: {}",
                                          asset.getAssetId(), ex.getMessage(), ex);
                            }
                        }

                        // ── Phase 3: single-round-trip bulk writes ────────────
                        // 1 DB call: INSERT ... ON DUPLICATE KEY UPDATE
                        depHistoryBulkRepo.upsertAll(depBatch);
                        // 1 DB call: batch UPDATE (depreciation columns only)
                        farReportBulkRepo.bulkUpdateDepreciation(validAssets, runDate);
                    }

                    chunkCount++;
                    totalBatches++;
                    totalProcessed += chunkProcessed;
                    totalSkipped   += chunkSkipped;

                    log.info("[DEPRECIATION] Batch {} | size={} ok={} skip={} | {}ms",
                             totalBatches, chunk.size(), chunkProcessed, chunkSkipped,
                             System.currentTimeMillis() - chunkStartMs);
                }

                log.info("[DEPRECIATION] ── Page {} complete  batches={}  ok={}  skip={} ──",
                         pageNumber + 1, chunkCount, totalProcessed, totalSkipped);
                pageNumber++;

            } while (page.hasNext());

            long elapsed = System.currentTimeMillis() - runStartMs;
            log.info("[DEPRECIATION] Run COMPLETE  pages={}  batches={}  processed={}  skipped={}  elapsed={}ms",
                     pageNumber, totalBatches, totalProcessed, totalSkipped, elapsed);
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
     * Straight-line depreciation per spec:
     *
     * <pre>
     *  Mandatory fields  : cost (IC), life (L), datePlacedInService
     *
     *  Step 1  IC   = asset.cost                     (rounded to 3dp)
     *  Step 2  SV   = asset.salvageValue ?? 0         (rounded to 3dp)
     *  Step 3  ADJ  = asset.depreciationReserve ?? 0  (ADJ < IC enforced)
     *  Step 4  MD   = (IC − SV) / L                  (rounded to 3dp)
     *  Step 5  D    = 1st of month AFTER datePlacedInService
     *  Step 6  NoMU = months(D → endOfCurrentMonth), capped at L; skip if < 0
     *  Step 7  AD   = MD × NoMU + ADJ                (capped at IC − SV)
     *  Step 8  NC   = IC − AD                        (floored at SV, never < 0)
     *  Step 9  Skip if NC already == SV (fully depreciated)
     * </pre>
     *
     * @param asset      the FAR record
     * @param period     pre-formatted "yyyy-MM" string for this run
     * @param endOfMonth last day of the current month
     * @param computedAt timestamp for this run
     * @return {@code null} when the asset must be skipped (missing data / fully depreciated)
     */
    private DepreciationResult computeDepreciation(FarReport     asset,
                                                    String        period,
                                                    LocalDate     endOfMonth,
                                                    LocalDateTime computedAt) {

        // ── Guard: mandatory fields ──────────────────────────────────────────
        if (asset.getCost() == null
                || asset.getLife() == null || asset.getLife() <= 0
                || asset.getDatePlacedInService() == null) {
            log.debug("[DEPRECIATION] Skip {} — missing mandatory field(s)", asset.getAssetId());
            return null;
        }

        // ── Step 1-3: core values (3dp precision) ───────────────────────────
        BigDecimal ic  = bd(asset.getCost());
        BigDecimal sv  = asset.getSalvageValue() != null ? bd(asset.getSalvageValue()) : BigDecimal.ZERO;
        BigDecimal adj = BigDecimal.ZERO;

        if (asset.getDepreciationReserve() != null) {
            BigDecimal rawAdj = bd(asset.getDepreciationReserve());
            if (rawAdj.compareTo(ic) < 0) {
                adj = rawAdj;
            } else {
                log.warn("[DEPRECIATION] Asset {} ADJ ({}) >= IC ({}), treating ADJ=0",
                         asset.getAssetId(), rawAdj, ic);
            }
        }

        int L = asset.getLife();

        // ── Step 4: MD = (IC − SV) / L (3dp) ───────────────────────────────
        BigDecimal depreciableAmount = ic.subtract(sv);
        if (depreciableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("[DEPRECIATION] Skip {} — IC ({}) <= SV ({})", asset.getAssetId(), ic, sv);
            return null;
        }
        BigDecimal md = depreciableAmount.divide(BigDecimal.valueOf(L), SCALE, ROUNDING);

        // ── Step 5: D = 1st of next calendar month after Date of Service ────
        // FIX: use explicit RIYADH zone instead of ZoneId.systemDefault()
        LocalDate serviceDate = asset.getDatePlacedInService()
                .toInstant().atZone(RIYADH).toLocalDate();
        LocalDate D = serviceDate.withDayOfMonth(1).plusMonths(1);

        // ── Step 6: NoMU = months(D → endOfCurrentMonth), capped at L ───────
        long nomu = ChronoUnit.MONTHS.between(D, endOfMonth);
        if (nomu < 0) {
            log.debug("[DEPRECIATION] Skip {} — service date {} is in the future (D={})",
                      asset.getAssetId(), serviceDate, D);
            return null;
        }
        nomu = Math.min(nomu, L);

        // ── Step 7: AD = MD × NoMU + ADJ  (capped at IC − SV) ───────────────
        BigDecimal ad    = md.multiply(BigDecimal.valueOf(nomu)).add(adj).setScale(SCALE, ROUNDING);
        BigDecimal maxAD = depreciableAmount;
        if (ad.compareTo(maxAD) > 0) {
            ad = maxAD;
        }

        // ── Step 8: NC = IC − AD  (floored at SV, never negative) ───────────
        BigDecimal nc = ic.subtract(ad).setScale(SCALE, ROUNDING);
        if (nc.compareTo(sv) < 0) {
            nc = sv;
            ad = ic.subtract(sv).setScale(SCALE, ROUNDING);
        }
        if (nc.compareTo(BigDecimal.ZERO) < 0) {
            nc = BigDecimal.ZERO;
        }

        // ── Step 9: Stop when fully depreciated (NC == SV) ───────────────────
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
     * Immutable value object carrying all computed fields for one asset.
     * IC and SV are included so {@link #applyDepreciationToFarReport} can
     * write back the 3dp-rounded values without re-reading the asset.
     */
    private record DepreciationResult(
            double        monthlyDepreciation,      // MD  (3dp)
            double        accumulatedDepreciation,  // AD  (3dp)
            double        netCost,                  // NC  (3dp)
            double        initialCost,              // IC  (3dp) — rounded
            double        salvageValue,             // SV  (3dp) — rounded
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
     *
     * <p>Uses the single {@code runDate} computed at the start of the run
     * (not {@code new Date()}) so all assets in a run share the same timestamp.
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
        asset.setDepreciationDate(runDate);  // consistent timestamp — not new Date() per asset
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