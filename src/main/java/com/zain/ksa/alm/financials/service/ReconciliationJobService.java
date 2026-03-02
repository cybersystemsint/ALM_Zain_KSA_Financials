package com.zain.ksa.alm.financials.service;

import com.zain.ksa.alm.financials.scheduler.UnmappedInventoryScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs reconciliation jobs asynchronously on the "reconciliationExecutor" thread pool.
 *
 * The controller returns 202 Accepted immediately with a jobId.
 * The client polls GET /unmapped-inventory/reconcile/status/{jobId} to check progress.
 *
 * Job lifecycle: RUNNING → COMPLETED | FAILED
 */
@Service
public class ReconciliationJobService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobService.class);

    // In-memory job status store — sufficient for single-node deployment
    private final Map<String, JobStatus> jobs = new ConcurrentHashMap<>();

    private final UnmappedInventoryScheduler scheduler;

    public ReconciliationJobService(UnmappedInventoryScheduler scheduler) {
        this.scheduler = scheduler;
    }

    // ── Public start methods — return jobId immediately ───────────────────────

    public String startActive() {
        String jobId = newJob("active");
        runActive(jobId);
        return jobId;
    }

    public String startPassive() {
        String jobId = newJob("passive");
        runPassive(jobId);
        return jobId;
    }

    public String startIT() {
        String jobId = newJob("it");
        runIT(jobId);
        return jobId;
    }

    public String startFull() {
        String jobId = newJob("full");
        runFull(jobId);
        return jobId;
    }

    // ── Async runners on reconciliationExecutor ───────────────────────────────

    @Async("reconciliationExecutor")
    protected void runActive(String jobId) {
        try {
            log.info("[ReconcileJob:{}] Active reconciliation starting on thread: {}",
                    jobId, Thread.currentThread().getName());
            scheduler.reconcileActiveInventoryPublic();
            complete(jobId);
        } catch (Exception e) {
            fail(jobId, e);
        }
    }

    @Async("reconciliationExecutor")
    protected void runPassive(String jobId) {
        try {
            log.info("[ReconcileJob:{}] Passive reconciliation starting on thread: {}",
                    jobId, Thread.currentThread().getName());
            scheduler.reconcilePassiveInventoryPublic();
            complete(jobId);
        } catch (Exception e) {
            fail(jobId, e);
        }
    }

    @Async("reconciliationExecutor")
    protected void runIT(String jobId) {
        try {
            log.info("[ReconcileJob:{}] IT reconciliation starting on thread: {}",
                    jobId, Thread.currentThread().getName());
            scheduler.reconcileITInventoryPublic();
            complete(jobId);
        } catch (Exception e) {
            fail(jobId, e);
        }
    }

    @Async("reconciliationExecutor")
    protected void runFull(String jobId) {
        try {
            log.info("[ReconcileJob:{}] Full reconciliation starting on thread: {}",
                    jobId, Thread.currentThread().getName());
            scheduler.runUnmappedReconciliation();
            complete(jobId);
        } catch (Exception e) {
            fail(jobId, e);
        }
    }

    // ── Status query ──────────────────────────────────────────────────────────

    public JobStatus getStatus(String jobId) {
        return jobs.get(jobId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String newJob(String type) {
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        jobs.put(jobId, new JobStatus(jobId, type, "RUNNING", LocalDateTime.now(), null, null));
        log.info("[ReconcileJob:{}] Job created for type '{}'", jobId, type);
        return jobId;
    }

    private void complete(String jobId) {
        jobs.computeIfPresent(jobId, (id, s) ->
                new JobStatus(id, s.type(), "COMPLETED", s.startedAt(), LocalDateTime.now(), null));
        log.info("[ReconcileJob:{}] COMPLETED", jobId);
    }

    private void fail(String jobId, Exception e) {
        log.error("[ReconcileJob:{}] FAILED: {}", jobId, e.getMessage(), e);
        jobs.computeIfPresent(jobId, (id, s) ->
                new JobStatus(id, s.type(), "FAILED", s.startedAt(), LocalDateTime.now(), e.getMessage()));
    }

    // ── Status record ─────────────────────────────────────────────────────────

    public record JobStatus(
            String jobId,
            String type,
            String status,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String errorMessage
    ) {}
}