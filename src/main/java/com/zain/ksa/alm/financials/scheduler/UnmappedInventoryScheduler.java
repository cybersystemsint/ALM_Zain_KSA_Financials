package com.zain.ksa.alm.financials.scheduler;

import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.repository.*;
import com.zain.ksa.alm.financials.service.impl.PreWarmExportJob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class UnmappedInventoryScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnmappedInventoryScheduler.class);
    private static final int BATCH_SIZE = 2000;

    @Autowired private NodeRepository                     activeNodeRepo;
    @Autowired private PassiveInventoryRepository         passiveItemRepo;
    @Autowired private ITInventoryRepository              itInventoryRepo;
    @Autowired private UnmappedActiveInventoryRepository  unmappedActiveRepo;
    @Autowired private UnmappedPassiveInventoryRepository unmappedPassiveRepo;
    @Autowired private UnmappedITInventoryRepository      unmappedITRepo;
    @Autowired private JdbcTemplate                       jdbcTemplate;
    @Autowired private PreWarmExportJob                   preWarmExportJob;

    private volatile boolean activeRunning  = false;
    private volatile boolean passiveRunning = false;
    private volatile boolean itRunning      = false;
    private volatile boolean fullRunning    = false;

    public boolean isActiveRunning()  { return activeRunning; }
    public boolean isPassiveRunning() { return passiveRunning; }
    public boolean isItRunning()      { return itRunning; }
    public boolean isFullRunning()    { return fullRunning; }
    public boolean isAnyRunning()     { return activeRunning || passiveRunning || itRunning || fullRunning; }

    // ── Scheduled full run ────────────────────────────────────────────────────

    /**
     * Nightly full reconciliation.
     * Refreshes all three pre-warmed exports in the finally block so
     * users get instant downloads regardless of success or failure.
     */
    @Scheduled(cron = "${app.scheduler.unmapped-check-cron:0 0 2 * * *}")
    public void runUnmappedReconciliation() {
        if (fullRunning) {
            log.warn("Full reconciliation already running, skipping");
            return;
        }
        fullRunning = true;
        try {
            log.info("[Scheduler] Starting full unmapped inventory reconciliation...");
            long start = System.currentTimeMillis();
            reconcileActiveInventory();
            reconcilePassiveInventory();
            reconcileITInventory();
            log.info("[Scheduler] Full reconciliation complete in {}ms",
                     System.currentTimeMillis() - start);
        } finally {
            fullRunning = false;
            // Refresh all three pre-warmed exports — covers both success and failure.
            // Each warmSingle() dispatches to preWarmTaskExecutor so this returns quickly.
            refreshPreWarm("unmapped_active",  "active");
            refreshPreWarm("unmapped_passive", "passive");
            refreshPreWarm("unmapped_it",      "it");
        }
    }

    // ── Manual / async triggers (called from UnmappedInventoryServiceImpl) ────

    /**
     * Refresh only the active pre-warmed export after active reconciliation.
     */
    public void reconcileActiveInventoryPublic() {
        if (activeRunning) {
            log.warn("Active reconciliation already running, skipping");
            return;
        }
        activeRunning = true;
        try {
            reconcileActiveInventory();
        } finally {
            activeRunning = false;
            refreshPreWarm("unmapped_active", "active");
        }
    }

    public void reconcilePassiveInventoryPublic() {
        if (passiveRunning) {
            log.warn("Passive reconciliation already running, skipping");
            return;
        }
        passiveRunning = true;
        try {
            reconcilePassiveInventory();
        } finally {
            passiveRunning = false;
            refreshPreWarm("unmapped_passive", "passive");
        }
    }

    public void reconcileITInventoryPublic() {
        if (itRunning) {
            log.warn("IT reconciliation already running, skipping");
            return;
        }
        itRunning = true;
        try {
            reconcileITInventory();
        } finally {
            itRunning = false;
            refreshPreWarm("unmapped_it", "it");
        }
    }

    // ── Pre-warm helper ───────────────────────────────────────────────────────

    /**
     * Triggers a pre-warm refresh for a single export type.
     * Isolated in try/catch so a pre-warm failure never affects
     * the reconciliation outcome or its finally block.
     */
    private void refreshPreWarm(String exportType, String label) {
        try {
            log.info("[Scheduler] Refreshing pre-warmed export for {}", label);
            preWarmExportJob.warmSingle(exportType, ExportFormat.EXCEL);
        } catch (Exception ex) {
            log.error("[Scheduler] Failed to refresh pre-warmed export for {}: {}",
                      label, ex.getMessage(), ex);
        }
    }

    // ── 1. Active Inventory ───────────────────────────────────────────────────

    private void reconcileActiveInventory() {
        log.info("[Scheduler] Active: loading FAR serial numbers from tb_FarReport...");
        Set<String> farSerials = loadFarSerials();
        log.info("[Scheduler] Active: FAR contains {} serial numbers", farSerials.size());

        long totalRows = activeNodeRepo.count();
        log.info("[Scheduler] Active: tb_Node has {} rows to process", totalRows);
        if (totalRows == 0) { log.warn("[Scheduler] Active: tb_Node is empty"); return; }

        int added = 0, removed = 0;
        int pages = (int) Math.ceil((double) totalRows / BATCH_SIZE);

        for (int page = 0; page < pages; page++) {
            int offset = page * BATCH_SIZE;
            if (page % 50 == 0)
                log.info("[Scheduler] Active: batch {}/{} (offset {}, added: {})",
                         page + 1, pages, offset, added);

            List<Object[]> rows = jdbcTemplate.query(
                "SELECT id, serialNumber, node, siteId, model, partNumber, description, " +
                "manufacturingDate, inventoryType FROM tb_Node LIMIT ? OFFSET ?",
                (rs, i) -> new Object[]{
                    rs.getObject("id"),
                    rs.getString("serialNumber"),
                    rs.getString("node"),
                    rs.getObject("siteId"),
                    rs.getString("model"),
                    rs.getString("partNumber"),
                    rs.getString("description"),
                    rs.getDate("manufacturingDate"),
                    rs.getObject("inventoryType")
                }, BATCH_SIZE, offset);

            List<Object[]> toInsert = new ArrayList<>();
            List<String>   toRemove = new ArrayList<>();
            for (Object[] row : rows) {
                String serial = (String) row[1];
                if (serial == null || serial.isBlank()) continue;
                if (farSerials.contains(serial)) toRemove.add(serial);
                else toInsert.add(row);
            }
            if (!toRemove.isEmpty() || !toInsert.isEmpty()) {
                processBatchActiveJdbc(toInsert, toRemove);
                added += toInsert.size();
                removed += toRemove.size();
            }
        }
        log.info("[Scheduler] Active complete: +{} added, -{} removed out of {} total",
                 added, removed, totalRows);
    }

    @Transactional
    protected void processBatchActiveJdbc(List<Object[]> toInsert, List<String> toRemove) {
        LocalDateTime now = LocalDateTime.now();
        if (!toRemove.isEmpty()) {
            String ph = String.join(",", toRemove.stream().map(s -> "?").toArray(String[]::new));
            jdbcTemplate.update(
                "DELETE FROM tb_unmapped_active_inventory WHERE serialNumber IN (" + ph + ")",
                toRemove.toArray());
        }
        if (!toInsert.isEmpty()) {
            StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO tb_unmapped_active_inventory " +
                "(recordDateTime, nodeId, nodeName, nodeType, serialNumber, model, partNumber, " +
                "siteId, manufacturingDate) VALUES ");
            List<Object> p = new ArrayList<>();
            for (int i = 0; i < toInsert.size(); i++) {
                Object[] row = toInsert.get(i);
                if (i > 0) sql.append(",");
                sql.append("(?,?,?,?,?,?,?,?,?)");
                p.add(now);
                p.add(row[0] != null ? row[0].toString() : null);
                p.add(row[2]);
                p.add(row[8] != null ? row[8].toString() : null);
                p.add(row[1]); p.add(row[4]); p.add(row[5]);
                p.add(row[3] != null ? row[3].toString() : null);
                p.add(row[7]);
            }
            jdbcTemplate.update(sql.toString(), p.toArray());
        }
    }

    // ── 2. Passive Inventory ──────────────────────────────────────────────────

    private void reconcilePassiveInventory() {
        log.info("[Scheduler] Passive: loading FAR serial numbers from tb_FarReport...");
        Set<String> farSerials = loadFarSerials();

        long totalRows = passiveItemRepo.count();
        log.info("[Scheduler] Passive: tb_Passive_Inventory has {} rows to process", totalRows);
        if (totalRows == 0) { log.warn("[Scheduler] Passive: source table is empty"); return; }

        int added = 0, removed = 0;
        int pages = (int) Math.ceil((double) totalRows / BATCH_SIZE);

        for (int page = 0; page < pages; page++) {
            int offset = page * BATCH_SIZE;
            if (page % 50 == 0)
                log.info("[Scheduler] Passive: batch {}/{} (offset {}, added: {})",
                         page + 1, pages, offset, added);

            List<Object[]> rows = jdbcTemplate.query(
                "SELECT recordNo, serialNumber, objectId, parentName, siteId, itemBarCode, model, note, " +
                "part, entryUser, entryDate, itemStatus, categoryInNEP, scrapStatus, inventoryType, " +
                "locationSubType, locationClassification, itemClassification, itemClassification2, PRPONo " +
                "FROM tb_Passive_Inventory LIMIT ? OFFSET ?",
                (rs, i) -> new Object[]{
                    rs.getObject("recordNo"),
                    rs.getString("serialNumber"),
                    rs.getString("objectId"),
                    rs.getString("parentName"),
                    rs.getString("siteId"),
                    rs.getString("itemBarCode"),
                    rs.getString("model"),
                    rs.getString("note"),
                    rs.getString("part"),
                    rs.getString("entryUser"),
                    rs.getString("entryDate"),
                    rs.getString("itemStatus"),
                    rs.getString("categoryInNEP"),
                    rs.getString("scrapStatus"),
                    rs.getObject("inventoryType"),
                    rs.getString("locationSubType"),
                    rs.getString("locationClassification"),
                    rs.getString("itemClassification"),
                    rs.getString("itemClassification2"),
                    rs.getString("PRPONo")
                }, BATCH_SIZE, offset);

            List<Object[]> toInsert = new ArrayList<>();
            List<String>   toRemove = new ArrayList<>();
            for (Object[] row : rows) {
                String serial = (String) row[1];
                if (serial == null || serial.isBlank()) continue;
                if (farSerials.contains(serial)) toRemove.add(serial);
                else toInsert.add(row);
            }
            if (!toRemove.isEmpty() || !toInsert.isEmpty()) {
                processBatchPassiveJdbc(toInsert, toRemove);
                added += toInsert.size();
                removed += toRemove.size();
            }
        }
        log.info("[Scheduler] Passive complete: +{} added, -{} removed out of {} total",
                 added, removed, totalRows);
    }

    @Transactional
    protected void processBatchPassiveJdbc(List<Object[]> toInsert, List<String> toRemove) {
        LocalDateTime now = LocalDateTime.now();
        if (!toRemove.isEmpty()) {
            String ph = String.join(",", toRemove.stream().map(s -> "?").toArray(String[]::new));
            jdbcTemplate.update(
                "DELETE FROM tb_unmapped_passive_inventory WHERE serialNumber IN (" + ph + ")",
                toRemove.toArray());
        }
        if (!toInsert.isEmpty()) {
            StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO tb_unmapped_passive_inventory " +
                "(recordDateTime, inventoryId, objectId, parentName, siteId, itemBarCode, serialNumber, model, " +
                "note, part, entryUser, entryDate, itemStatus, categoryInNEP, scrapStatus, inventoryType, " +
                "inventoryTypeId, locationSubType, locationClassification, itemClassification, " +
                "itemClassification2, notes, prPoNo) VALUES ");
            List<Object> p = new ArrayList<>();
            for (int i = 0; i < toInsert.size(); i++) {
                Object[] row = toInsert.get(i);
                if (i > 0) sql.append(",");
                sql.append("(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                p.add(now);
                p.add(row[0] != null ? row[0].toString() : null);
                p.add(row[2]); p.add(row[3]); p.add(row[4]); p.add(row[5]);
                p.add(row[1]); p.add(row[6]); p.add(row[7]); p.add(row[8]);
                p.add(row[9]); p.add(row[10]); p.add(row[11]); p.add(row[12]);
                p.add(row[13]);
                p.add(row[14] != null ? row[14].toString() : null);
                p.add(null); p.add(row[15]); p.add(row[16]); p.add(row[17]);
                p.add(row[18]); p.add(null); p.add(row[19]);
            }
            jdbcTemplate.update(sql.toString(), p.toArray());
        }
    }

    // ── 3. IT Inventory ───────────────────────────────────────────────────────

    private void reconcileITInventory() {
        log.info("[Scheduler] IT: loading FAR serial numbers from tb_FarReport...");
        Set<String> farSerials = loadFarSerials();

        long totalRows = itInventoryRepo.count();
        log.info("[Scheduler] IT: vw_IT_Inventory has {} rows to process", totalRows);
        if (totalRows == 0) { log.warn("[Scheduler] IT: source view is empty"); return; }

        int added = 0, removed = 0;
        int pages = (int) Math.ceil((double) totalRows / BATCH_SIZE);

        for (int page = 0; page < pages; page++) {
            int offset = page * BATCH_SIZE;
            if (page % 50 == 0)
                log.info("[Scheduler] IT: batch {}/{} (offset {}, added: {})",
                         page + 1, pages, offset, added);

            List<Object[]> rows = jdbcTemplate.query(
                "SELECT recordDateTime, objectId, siteId, hostSerialNumber, inventoryTypeId, inventoryType, " +
                "hostTypeName, firstScan, IPAddress, OSId, osName, hardwareVendorId, hardwareVendorName, " +
                "model, `virtual`, hostTypeId, category FROM vw_IT_Inventory LIMIT ? OFFSET ?",
                (rs, i) -> new Object[]{
                    rs.getTimestamp("recordDateTime"),
                    rs.getString("objectId"),
                    rs.getString("siteId"),
                    rs.getString("hostSerialNumber"),
                    rs.getObject("inventoryTypeId"),
                    rs.getString("inventoryType"),
                    rs.getString("hostTypeName"),
                    rs.getString("firstScan"),
                    rs.getString("IPAddress"),
                    rs.getString("OSId"),
                    rs.getString("osName"),
                    rs.getObject("hardwareVendorId"),
                    rs.getString("hardwareVendorName"),
                    rs.getString("model"),
                    rs.getObject("virtual"),
                    rs.getObject("hostTypeId"),
                    rs.getString("category")
                }, BATCH_SIZE, offset);

            List<Object[]> toInsert = new ArrayList<>();
            List<String>   toRemove = new ArrayList<>();
            for (Object[] row : rows) {
                String serial = (String) row[3];
                if (serial == null || serial.isBlank()) continue;
                if (farSerials.contains(serial)) toRemove.add(serial);
                else toInsert.add(row);
            }
            if (!toRemove.isEmpty() || !toInsert.isEmpty()) {
                processBatchITJdbc(toInsert, toRemove);
                added += toInsert.size();
                removed += toRemove.size();
            }
        }
        log.info("[Scheduler] IT complete: +{} added, -{} removed out of {} total",
                 added, removed, totalRows);
    }

    @Transactional
    protected void processBatchITJdbc(List<Object[]> toInsert, List<String> toRemove) {
        LocalDateTime now = LocalDateTime.now();
        if (!toRemove.isEmpty()) {
            String ph = String.join(",", toRemove.stream().map(s -> "?").toArray(String[]::new));
            jdbcTemplate.update(
                "DELETE FROM tb_unmapped_IT_Inventory WHERE hostSerialNumber IN (" + ph + ")",
                toRemove.toArray());
        }
        if (!toInsert.isEmpty()) {
            StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO tb_unmapped_IT_Inventory " +
                "(recordDatetime, objectId, siteId, hostSerialNumber, inventoryTypeId, inventoryType, " +
                "hostTypeName, firstScan, ipAddress, osId, osName, hardwareVendorId, hardwareVendorName, " +
                "model, isVirtual, hostTypeId, category) VALUES ");
            List<Object> p = new ArrayList<>();
            for (int i = 0; i < toInsert.size(); i++) {
                Object[] row = toInsert.get(i);
                if (i > 0) sql.append(",");
                sql.append("(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                p.add(now);
                p.add(row[1]); p.add(row[2]); p.add(row[3]);
                p.add(row[4]); p.add(row[5]); p.add(row[6]);
                p.add(row[7]); p.add(row[8]); p.add(row[9]);
                p.add(row[10]); p.add(row[11]); p.add(row[12]); p.add(row[13]);
                Object v = row[14];
                p.add(v == null ? 0 : (((Number) v).intValue() != 0 ? 1 : 0));
                p.add(row[15]); p.add(row[16]);
            }
            jdbcTemplate.update(sql.toString(), p.toArray());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Set<String> loadFarSerials() {
        List<String> serials = jdbcTemplate.queryForList(
            "SELECT DISTINCT serialNumber FROM tb_FarReport WHERE serialNumber IS NOT NULL",
            String.class);
        log.info("[Scheduler] Loaded {} FAR serial numbers into memory", serials.size());
        return new HashSet<>(serials);
    }
}