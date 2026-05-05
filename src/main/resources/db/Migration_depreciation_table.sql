-- -- ════════════════════════════════════════════════════════════════════════════
-- -- DepreciationHistory Table Normalization - Migration Script
-- -- ════════════════════════════════════════════════════════════════════════════
-- -- 
-- -- This script migrates from a fat denormalized table (60+ columns)
-- -- to a lean normalized table (10 columns storing only depreciation metrics).
-- --
-- -- Master asset data remains in tb_FarReport and is joined at query time.
-- --
-- -- EXECUTION STEPS:
-- -- 1. Run steps 1-2 (backup + create new table) during maintenance window
-- -- 2. Monitor data copy (step 3) - may take 10-30 min for 3M+ rows
-- -- 3. Switch table names (step 4-5) - atomic operation, seconds downtime
-- -- 4. Run verification (step 6)
-- -- 5. Drop backup after 24-48 hours of validation (step 7)
-- --
-- -- RISK MITIGATION:
-- -- - Old table renamed to backup first (reversible)
-- -- - Verify row counts before dropping
-- -- - Keep backup for 24-48 hours in case of issues
-- --
-- -- ════════════════════════════════════════════════════════════════════════════

-- -- ────────────────────────────────────────────────────────────────────────────
-- -- STEP 1: Create backup of original table (safety)
-- -- ────────────────────────────────────────────────────────────────────────────

-- RENAME TABLE tb_DepreciationHistory TO tb_DepreciationHistory_backup;

-- -- ────────────────────────────────────────────────────────────────────────────
-- -- STEP 2: Create new normalized DepreciationHistory table
-- -- ────────────────────────────────────────────────────────────────────────────

-- CREATE TABLE tb_DepreciationHistory (
--     recordNo BIGINT PRIMARY KEY AUTO_INCREMENT,
    
--     -- Foreign key to FarReport asset (use JOIN to fetch asset details)
--     assetId VARCHAR(50) NOT NULL,
    
--     -- Period key in "YYYY-MM" format
--     -- Combined with assetId forms unique constraint
--     depreciationPeriod VARCHAR(7) NOT NULL,
    
--     -- Computed depreciation metrics (the only columns updated by scheduler)
--     monthlyDepreciationAmt DECIMAL(15,2) NOT NULL,
--     accumulatedDepreciationAmt DECIMAL(15,2) NOT NULL,
--     netCost DECIMAL(15,2) NOT NULL,
    
--     -- Metadata
--     depreciationDate DATETIME NOT NULL,
--     recordDatetime DATETIME NOT NULL,
    
--     -- Audit trail
--     createdBy VARCHAR(100),
--     changedBy VARCHAR(100),
    
--     -- Constraints
--     UNIQUE KEY uk_dh_asset_period (assetId, depreciationPeriod),
    
--     -- Indexes for common queries
--     INDEX idx_dh_asset_period (assetId, depreciationPeriod),
--     INDEX idx_dh_depreciationDate (depreciationDate),
--     INDEX idx_dh_depreciationPeriod (depreciationPeriod)
    
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -- ────────────────────────────────────────────────────────────────────────────
-- -- STEP 3: Copy data from backup to new table (only relevant columns)
-- -- ────────────────────────────────────────────────────────────────────────────
-- -- This copies ONLY the 10 columns needed, ignoring the 50+ duplicate master data columns.

-- INSERT INTO tb_DepreciationHistory (
--     recordNo,
--     assetId,
--     depreciationPeriod,
--     monthlyDepreciationAmt,
--     accumulatedDepreciationAmt,
--     netCost,
--     depreciationDate,
--     recordDatetime,
--     createdBy,
--     changedBy
-- )
-- SELECT 
--     recordNo,
--     assetId,
--     depreciationPeriod,
--     monthlyDepreciationAmt,
--     accumulatedDepreciationAmt,
--     netCost,
--     depreciationDate,
--     recordDatetime,
--     createdBy,
--     changedBy
-- FROM tb_DepreciationHistory_backup
-- WHERE recordNo IS NOT NULL;  -- Ensure no nulls in primary key

-- -- ────────────────────────────────────────────────────────────────────────────
-- -- STEP 4: Verify data integrity before switching
-- -- ────────────────────────────────────────────────────────────────────────────

-- -- Count rows to ensure no data loss
-- SELECT 'Backup table' AS source, COUNT(*) AS row_count
-- FROM tb_DepreciationHistory_backup
-- UNION ALL
-- SELECT 'New table' AS source, COUNT(*) AS row_count
-- FROM tb_DepreciationHistory;

-- -- Spot check: verify some records were migrated correctly
-- SELECT 
--     'Backup' AS source,
--     COUNT(*) AS total,
--     SUM(CASE WHEN monthlyDepreciationAmt IS NOT NULL THEN 1 ELSE 0 END) AS non_null_monthly
-- FROM tb_DepreciationHistory_backup
-- UNION ALL
-- SELECT 
--     'New' AS source,
--     COUNT(*) AS total,
--     SUM(CASE WHEN monthlyDepreciationAmt IS NOT NULL THEN 1 ELSE 0 END) AS non_null_monthly
-- FROM tb_DepreciationHistory;

-- -- ────────────────────────────────────────────────────────────────────────────
-- -- STEP 5: If verification passed, drop the backup
-- -- ────────────────────────────────────────────────────────────────────────────
-- -- WAIT 24-48 HOURS after migration to ensure everything is working before dropping!

-- -- DROP TABLE tb_DepreciationHistory_backup;

-- -- ────────────────────────────────────────────────────────────────────────────
-- -- STEP 6: Validate indexes and query performance
-- -- ────────────────────────────────────────────────────────────────────────────

-- -- Check that indexes were created correctly
-- SHOW INDEXES FROM tb_DepreciationHistory;

-- -- Test scheduler upsert query (bulk fetch for a period)
-- EXPLAIN
-- SELECT * FROM tb_DepreciationHistory
-- WHERE assetId IN ('ASSET001', 'ASSET002', 'ASSET003')
--   AND depreciationPeriod = '2025-02';

-- -- Test export query (JOIN with FarReport)
-- EXPLAIN
-- SELECT 
--     dh.recordNo,
--     dh.depreciationPeriod,
--     dh.monthlyDepreciationAmt,
--     dh.accumulatedDepreciationAmt,
--     dh.netCost,
--     fr.assetId,
--     fr.description,
--     fr.serialNumber,
--     fr.category
-- FROM tb_DepreciationHistory dh
-- JOIN tb_FarReport fr ON dh.assetId = fr.assetId
-- WHERE dh.depreciationPeriod = '2025-02'
-- ORDER BY dh.depreciationDate DESC
-- LIMIT 100;

-- -- ────────────────────────────────────────────────────────────────────────────
-- -- STEP 7: Table size comparison
-- -- ────────────────────────────────────────────────────────────────────────────

-- -- Show table sizes before/after
-- SELECT 
--     table_name,
--     ROUND(((data_length + index_length) / 1024 / 1024), 2) AS size_mb
-- FROM information_schema.TABLES 
-- WHERE table_schema = DATABASE()
--   AND table_name IN ('tb_DepreciationHistory', 'tb_DepreciationHistory_backup')
-- ORDER BY size_mb DESC;

-- -- ════════════════════════════════════════════════════════════════════════════
-- -- ROLLBACK PLAN (if needed)
-- -- ════════════════════════════════════════════════════════════════════════════
-- --
-- -- If something goes wrong, you can quickly revert:
-- --
-- -- 1. Drop the new (problematic) table:
-- --    DROP TABLE tb_DepreciationHistory;
-- --
-- -- 2. Restore from backup:
-- --    RENAME TABLE tb_DepreciationHistory_backup TO tb_DepreciationHistory;
-- --
-- -- This keeps the old 60-column table active while you investigate the issue.
-- --
-- -- ════════════════════════════════════════════════════════════════════════════

-- -- ────────────────────────────────────────────────────────────────────────────
-- -- STEP 8: After 48 hours of successful operation, clean up backup
-- -- ────────────────────────────────────────────────────────────────────────────

-- -- DROP TABLE tb_DepreciationHistory_backup;


-- -- ════════════════════════════════════════════════════════════════════════════
-- -- POST-MIGRATION CHECKS
-- -- ════════════════════════════════════════════════════════════════════════════

-- -- Verify unique constraint is enforced
-- -- This should fail if you try to insert duplicate (assetId, depreciationPeriod):
-- -- INSERT INTO tb_DepreciationHistory 
-- -- SELECT * FROM tb_DepreciationHistory LIMIT 1;

-- -- Check storage reduction
-- -- Old: ~60 columns × 3M rows ≈ 1.8 GB
-- -- New: ~10 columns × 3M rows ≈ 240 MB
-- -- Reduction: ~7.5×

-- -- Verify scheduler upsert logic works correctly
-- -- SELECT COUNT(*) FROM tb_DepreciationHistory 
-- -- WHERE depreciationPeriod = '2025-02';

-- -- Verify JOIN performance with FarReport
-- -- The assetId index on FarReport should make this fast
-- -- SELECT COUNT(*) FROM tb_DepreciationHistory dh
-- -- JOIN tb_FarReport fr ON dh.assetId = fr.assetId
-- -- WHERE dh.depreciationPeriod = '2025-02';