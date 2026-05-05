# ALM Financials Service

> A comprehensive Spring Boot REST API for managing asset depreciation, fixed asset registers (FAR), and inventory reconciliation with async export capabilities.

**Version:** 2.0.0  
**Last Updated:** 2025-03-04

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Core Features](#core-features)
- [API Modules](#api-modules)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Database Schema](#database-schema)
- [Performance](#performance)
- [Schedulers](#schedulers)
- [Export System](#export-system)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

---

## Overview

The **ALM Financials Service** is an enterprise-grade financial asset management system designed for large-scale organizations. It provides centralized management of fixed assets, automated depreciation calculations, and inventory reconciliation with real-time reporting capabilities.

### Key Capabilities

✅ **Asset Depreciation Management**
- Automated monthly depreciation calculation
- Historical tracking with full audit trail
- Support for multiple depreciation methods
- Batch processing optimized for millions of assets

✅ **Fixed Asset Register (FAR)**
- Centralized asset master data
- Upload from Excel, CSV, SAP, Oracle
- Real-time depreciation synchronization
- Advanced filtering and search

✅ **Inventory Reconciliation**
- Active inventory (Node/Server assets)
- Passive inventory (Warehouse storage)
- IT inventory (Network hardware)
- Automatic unmapped item detection

✅ **Async Reporting & Export**
- CSV and Excel export formats
- Pre-warmed exports for instant downloads
- Large file streaming (no memory limits)
- Job-based polling with progress tracking

✅ **Enterprise Features**
- Multi-site support (Saudi Arabia timezone)
- Role-based audit trails
- RESTful API with CORS support
- Comprehensive error handling

---

## Architecture

### High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        REST Controllers                      │
├──────────────┬──────────────────┬──────────────┬────────────┤
│ Depreciation │   FAR Report     │  Unmapped    │  Exports   │
│ Controller   │   Controller     │  Inventory   │ Controller │
│              │                  │  Controller  │            │
└────┬─────────┴────┬─────────────┴────┬─────────┴──────┬─────┘
     │              │                  │               │
     ▼              ▼                  ▼               ▼
┌─────────────────────────────────────────────────────────────┐
│                     Service Layer                           │
├──────────────┬──────────────────┬──────────────┬────────────┤
│ Depreciation │   FAR Report     │  Unmapped    │  Export    │
│ History      │   Service        │  Inventory   │  Job       │
│ Service      │                  │  Service     │  Service   │
└────┬─────────┴────┬─────────────┴────┬─────────┴──────┬─────┘
     │              │                  │               │
     ▼              ▼                  ▼               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Repository / Data Layer                     │
├──────────────┬──────────────────┬──────────────┬────────────┤
│ Depreciation │   FarReport      │  Unmapped    │ File       │
│ History      │   Repository     │  Inventory   │ Storage    │
│ Repository   │                  │  Repository  │ Service    │
└────┬─────────┴────┬─────────────┴────┬─────────┴──────┬─────┘
     │              │                  │               │
     └──────────────┴──────────────────┴──────────────┘
               │
               ▼
        ┌──────────────────┐
        │   MySQL / DB     │
        └──────────────────┘
```

### Scheduled Tasks

```
┌─────────────────────────────────────────────────────┐
│         Scheduled Job Executors                    │
├──────────────────┬────────────────────────────────┤
│ Depreciation     │ Unmapped Inventory Scheduler   │
│ Scheduler        │                                │
│ (Monthly: EOM)   │ (Nightly: 20:00 UTC)          │
└──────┬───────────┴────────────┬──────────────────┘
       │                        │
       ▼                        ▼
   ┌────────────┐         ┌──────────────┐
   │ Computes   │         │ Reconciles   │
   │ Monthly    │         │ Active       │
   │ Deprec.    │         │ Passive      │
   │ for ALL    │         │ IT Inventory │
   │ Assets     │         │              │
   └────┬───────┘         └────┬─────────┘
        │                      │
        ▼                      ▼
   ┌────────────┐         ┌──────────────┐
   │ Updates    │         │ Flags        │
   │ FAR Report │         │ Unmapped     │
   │ & History  │         │ Items        │
   └────┬───────┘         └────┬─────────┘
        │                      │
        └──────────┬───────────┘
                   │
                   ▼
         ┌──────────────────┐
         │ Pre-Warm Exports │
         │ (Instant DL)     │
         └──────────────────┘
```

---

## Core Features

### 1. **Depreciation History Management**

Tracks monthly depreciation calculations for all fixed assets with lean database schema.

**Key Metrics Tracked:**
- `monthlyDepreciationAmt` - Monthly depreciation charge
- `accumulatedDepreciationAmt` - Cumulative depreciation
- `netCost` - Book value (Cost - Accumulated Depreciation)
- `depreciationPeriod` - YYYY-MM format

**Depreciation Formula:**
```
Monthly Depreciation = (Cost - Salvage Value) / Useful Life (months)
Accumulated Depreciation = Monthly Depreciation × Months in Service
Net Cost = Original Cost - Accumulated Depreciation
```

**Table Design (Optimized):**
```sql
CREATE TABLE tb_depreciation_history (
  recordNo BIGINT PRIMARY KEY AUTO_INCREMENT,
  assetId VARCHAR(50) NOT NULL UNIQUE,
  depreciationPeriod VARCHAR(10),         -- YYYY-MM
  monthlyDepreciationAmt DECIMAL(15,2),
  accumulatedDepreciationAmt DECIMAL(15,2),
  netCost DECIMAL(15,2),
  depreciationDate DATETIME,
  recordDatetime DATETIME,
  createdBy VARCHAR(50),
  changedBy VARCHAR(50),
  
  INDEX idx_assetId (assetId),
  INDEX idx_period (depreciationPeriod)
);
```



---

### 2. **Fixed Asset Register (FAR)**

Master repository for all organizational fixed assets.

**Master Data Fields:**
- `assetId` - Unique asset identifier
- `description` - Asset description/name
- `serialNumber` - Manufacturer serial number
- `category` - Asset category (IT, Office, Vehicle, etc.)
- `cost` - Original acquisition cost
- `salvageValue` - Residual value at end of useful life
- `life` - Useful life in months
- `datePlacedInService` - Service start date

**Depreciation Fields (Updated by Scheduler):**
- `monthlyDepreciationAmt`
- `accumulatedDepreciationAmt`
- `netCost`
- `depreciationDate`

**Data Import Sources:**
- Excel/CSV uploads (REST API)
- SAP integrations
- Oracle ERP systems
- Custom data feeds

**Capabilities:**
- ✅ Bulk import (up to 10,000 rows/request)
- ✅ Duplicate detection (serial numbers)
- ✅ Validation before save
- ✅ Automatic sync with depreciation calculations

---

### 3. **Inventory Reconciliation System**

Three-tier inventory management system to identify unmapped assets.

#### Active Inventory (Node)
**Source:** Enterprise monitoring systems (Zabbix, Nagios)
**Tracks:** Servers, network devices, compute resources
**Fields:**
```
id, serialNumber, node, siteId, model, partNumber, 
description, manufacturingDate, inventoryType
```

#### Passive Inventory
**Source:** Warehouse management systems
**Tracks:** Stored equipment, spare parts, archived assets
**Fields:**
```
inventoryId, serialNumber, objectId, parentName, siteId, 
itemBarCode, model, note, part, entryUser, entryDate, 
itemStatus, categoryInNEP, scrapStatus, inventoryType
```

#### IT Inventory
**Source:** IT asset discovery tools (CMDB, network scanning)
**Tracks:** Computers, peripherals, network hardware
**Fields:**
```
recordDateTime, objectId, siteId, hostSerialNumber, 
inventoryType, hostTypeName, firstScan, IPAddress, 
osName, hardwareVendorName, model, isVirtual, category
```

**Reconciliation Process:**

```
1. Load FAR serial numbers into memory (HashSet)
2. For each inventory source (Active/Passive/IT):
   a. Batch load inventory items (2,000 records/batch)
   b. Check serial against FAR serials
   c. If NOT in FAR → ADD to unmapped table
   d. If IN FAR → REMOVE from unmapped table (cleanup)
3. Update pre-warmed exports
```

**Performance:**
- ✅ 2,000-record batch processing
- ✅ In-memory HashSet lookup (O(1) avg)
- ✅ Hourly execution for nightly sync

---

### 4. **Export & Reporting System**

Enterprise-grade async export with streaming for large datasets.

**Export Formats:**
- 📊 Excel (.xlsx) - OpenDocument format
- 📄 CSV - Comma-separated values

**Export Strategy:**

1. **Pre-Warmed Exports** (Instant Downloads)
   - Generated after each depreciation run
   - Refreshed nightly for inventory data
   - Available for download in <1 second
   - Used when NO filters applied

2. **On-Demand Filtered Exports**
   - Generated when filters applied
   - Background job processing
   - Streamed to file storage
   - Retained for 24 hours

**Export Job Lifecycle:**

```
User Request
    │
    ▼
Is Filter Empty? ──YES─→ Return Pre-Warmed JobId
    │                    (Status: COMPLETED)
    NO
    │
    ▼
Create JobId ──────────┐
                       │
Initialize Job ────────┤
(Status: PENDING)      │
                       │
Enqueue to Executor ───┤
(Status: RUNNING)      │
                       │
Process & Write File ──┤
                       │
Complete Job ──────────┘
(Status: COMPLETED)
    │
    ▼
Return jobId + Status
(Client polls /exports/status/{jobId})
    │
    ▼
File Ready
(Client calls /exports/download/{jobId})
```

**Features:**
- ✅ InputStreamResource streaming (8KB buffer)
- ✅ No memory limit (files >1GB supported)
- ✅ Progress tracking
- ✅ Job retention (configurable hours)
- ✅ Connection interruption handling

---

## API Modules

### Module 1: Depreciation History (`/depreciation-history`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/list` | POST | Paginated depreciation list with filters |
| `/export` | POST | Start async export job |
| `/run-depreciation` | POST | Manual trigger depreciation scheduler |
| `/run-depreciation/status` | GET | Check scheduler status |
| `/health` | GET | Service health check |

**Common Responses:**
- `200 OK` - Successful retrieval
- `202 Accepted` - Async job started
- `409 Conflict` - Process already running

---

### Module 2: FAR Report (`/far-report`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/list` | POST | Paginated FAR with summary statistics |
| `/upload` | POST | Bulk import from Excel/CSV/SAP |
| `/export` | POST | Start async export job |

**Features:**
- Summary statistics (total assets, total cost, etc.)
- Validation on upload (required fields, date formats)
- Automatic serial number deduplication

---

### Module 3: Unmapped Inventory (`/unmapped-inventory`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/active` | POST | Active unmapped items |
| `/active/export` | POST | Export active items |
| `/passive` | POST | Passive unmapped items |
| `/passive/export` | POST | Export passive items |
| `/it` | POST | IT unmapped items |
| `/it/export` | POST | Export IT items |
| `/reconcile/active` | POST | Trigger active reconciliation |
| `/reconcile/passive` | POST | Trigger passive reconciliation |
| `/reconcile/it` | POST | Trigger IT reconciliation |
| `/reconcile/all` | POST | Trigger full reconciliation |
| `/reconcile/status` | GET | Get reconciliation status |

---

### Module 4: Export Management (`/exports`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/status/{jobId}` | GET | Poll export job status |
| `/download/{jobId}` | GET | Download completed file |

**Workflow:**
1. POST to `/depreciation-history/export` → Receive jobId
2. GET `/exports/status/{jobId}` → Monitor progress
3. GET `/exports/download/{jobId}` → Stream file

---

## Getting Started

### Prerequisites

```
Java 17+
Spring Boot 2.5+
MySQL 5.7+ (InnoDB)
Maven 3.6+
```

### Configuration

**application.properties**
```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/alm_financials?serverTimezone=Asia/Riyadh
spring.datasource.username=alm_user
spring.datasource.password=secure_password
spring.jpa.hibernate.ddl-auto=update

# Scheduler
app.scheduler.monthly-job-cron=0 0 0 L * ?
app.scheduler.unmapped-check-cron=0 0 20 * * *
app.scheduler.timezone=Asia/Riyadh

# Export Settings
app.export.max-file-age-hours=5
app.export.storage-path=/data/app/financials-exports
app.export.batch-size=10000

# Thread Pools
app.scheduler-executor.core-pool-size=3
app.scheduler-executor.max-pool-size=5
app.scheduler-executor.queue-capacity=10
```

### Startup

```bash
mvn clean package
java -jar alm-financials-service-1.0.0.jar
```

**Expected Log Output:**
```
[INFO] Starting ALM Financials Service...
[INFO] Depreciation History Controller initialized
[INFO] FAR Report Controller initialized
[INFO] Unmapped Inventory Controller initialized
[INFO] Export Controller initialized
[INFO] Scheduling depreciation job: 0 0 0 L * ? (Asia/Riyadh)
[INFO] Scheduling unmapped reconciliation: 0 0 20 * * *
[INFO] Service started on port 8080
```

---

## Configuration

### Database Setup

```sql
-- Create database
CREATE DATABASE alm_financials CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user
CREATE USER 'alm_user'@'localhost' IDENTIFIED BY 'secure_password';
GRANT ALL PRIVILEGES ON alm_financials.* TO 'alm_user'@'localhost';
FLUSH PRIVILEGES;
```

### Scheduler Configuration

**Cron Expressions:**
```
Monthly Depreciation:     0 0 0 L * ?         (Last day of month, 00:00)
Inventory Reconciliation: 0 0 20 * * *        (Daily at 20:00 UTC)
                          0 0 1 * * * 0       (Sunday at 01:00)
```

**Timezone:** `Asia/Riyadh` (Default)

### Export Configuration

```properties
# Storage
app.export.storage-path=/mnt/exports

# Retention
app.export.max-file-age-hours=24
app.export.cleanup-interval=PT1H

# Batch Processing
app.export.batch-size=10000
app.export.buffer-size=8192
```

### Thread Pools

**Depreciation Scheduler Executor:**
```
Core Threads:    3
Max Threads:     5
Queue Capacity:  10
Keep-Alive:      60s
Rejection Policy: AbortPolicy
```

---

## Database Schema

### Core Tables

```
┌─────────────────────────────────────────┐
│ tb_FarReport (Master Asset Data)       │
├─────────────────────────────────────────┤
│ ✓ assetId (PK)                         │
│ ✓ description, serialNumber            │
│ ✓ category, cost, salvageValue, life   │
│ ✓ datePlacedInService                  │
│ ✓ monthlyDepreciationAmt (synced)      │
│ ✓ accumulatedDepreciationAmt (synced)  │
│ ✓ netCost (synced)                     │
│ ✓ createdBy, changedBy (audit)         │
└─────────────────────────────────────────┘
         │ 1:N
         │
         ▼
┌─────────────────────────────────────────┐
│ tb_depreciation_history (Lean)         │
├─────────────────────────────────────────┤
│ ✓ recordNo (PK)                        │
│ ✓ assetId (FK, Index)                  │
│ ✓ depreciationPeriod (YYYY-MM, Index)  │
│ ✓ monthlyDepreciationAmt               │
│ ✓ accumulatedDepreciationAmt           │
│ ✓ netCost                              │
│ ✓ depreciationDate, recordDatetime     │
│ ✓ createdBy, changedBy                 │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ tb_Node (Active Inventory)             │
├─────────────────────────────────────────┤
│ ✓ id (PK)                              │
│ ✓ serialNumber (Unique, Index)         │
│ ✓ node, siteId, model, partNumber      │
│ ✓ description, manufacturingDate       │
│ ✓ inventoryType                        │
└─────────────────────────────────────────┘
         │ M:1
         │
         ▼
┌─────────────────────────────────────────┐
│ tb_unmapped_active_inventory           │
├─────────────────────────────────────────┤
│ Items in Node but NOT in FAR            │
│ (Auto-populated by scheduler)           │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ tb_PassiveInventory (Warehouse)        │
├─────────────────────────────────────────┤
│ ✓ inventoryId (PK)                     │
│ ✓ serialNumber (Unique, Index)         │
│ ✓ objectId, parentName, siteId         │
│ ✓ itemBarCode, model, note, part       │
│ ✓ entryUser, entryDate, itemStatus     │
│ ✓ categoryInNEP, scrapStatus           │
└─────────────────────────────────────────┘
         │ M:1
         │
         ▼
┌─────────────────────────────────────────┐
│ tb_unmapped_passive_inventory          │
├─────────────────────────────────────────┤
│ Items in PassiveInventory NOT in FAR    │
│ (Auto-populated by scheduler)           │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ vw_IT_Inventory (IT Asset Discovery)   │
├─────────────────────────────────────────┤
│ ✓ objectId, siteId, hostSerialNumber   │
│ ✓ inventoryType, hostTypeName          │
│ ✓ firstScan, IPAddress, osName         │
│ ✓ hardwareVendorName, model, virtual   │
│ ✓ category                             │
└─────────────────────────────────────────┘
         │ M:1
         │
         ▼
┌─────────────────────────────────────────┐
│ tb_unmapped_IT_Inventory               │
├─────────────────────────────────────────┤
│ Items in IT Inventory NOT in FAR        │
│ (Auto-populated by scheduler)           │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ tb_export_jobs (Job Status Tracking)   │
├─────────────────────────────────────────┤
│ ✓ jobId (PK)                           │
│ ✓ exportType (Index)                   │
│ ✓ status (PENDING, RUNNING, COMPLETED) │
│ ✓ filePath, fileName, contentType      │
│ ✓ recordsProcessed, progress           │
│ ✓ createdAt, completedAt               │
└─────────────────────────────────────────┘
```

## Schedulers

### 1. Depreciation Scheduler

**Schedule:** Last day of month at 00:00 (Asia/Riyadh)  
**Cron:** `0 0 0 L * ?`

**Process Flow:**
```
1. Fetch all assets from FAR (100 per page)
2. For each asset:
   a. Check if eligible (cost, life, service date defined)
   b. Calculate monthly & accumulated depreciation
   c. Compute net cost
   d. Skip if negative net cost
3. Batch flush to DepreciationHistory (2K/batch)
4. Update FarReport with new metrics
5. Refresh pre-warmed exports (Excel)
6. Log comprehensive statistics
```

**Metrics Logged:**
```
[DEPRECIATION] ════════════════════════════════════════════════
[DEPRECIATION] Run COMPLETE
[DEPRECIATION]   Pages        : 100
[DEPRECIATION]   Total batches: 500
[DEPRECIATION]   Processed    : 1000000
[DEPRECIATION]   Skipped      : 5000
[DEPRECIATION]   Grand total  : 1005000
[DEPRECIATION]   Elapsed      : 1200000ms (20 min)
[DEPRECIATION] ════════════════════════════════════════════════
```

### 2. Unmapped Inventory Scheduler

**Schedule:** Daily at 20:00 UTC  
**Cron:** `0 0 20 * * *`

**Process Flow:**
```
1. Load FAR serial numbers (HashSet)
2. Process Active Inventory (tb_Node)
   a. Batch load 2K records
   b. Mark unmapped items
   c. Remove mapped items from unmapped table
3. Process Passive Inventory (tb_PassiveInventory)
   a. Same logic as active
4. Process IT Inventory (vw_IT_Inventory)
   a. Same logic as passive
5. Refresh pre-warmed exports (all 3 types)
```

**Output Example:**
```
[Scheduler] Starting full unmapped inventory reconciliation...
[Scheduler] Active: loading FAR serial numbers from tb_FarReport...
[Scheduler] Active: FAR contains 1000000 serial numbers
[Scheduler] Active: tb_Node has 1050000 rows to process
[Scheduler] Active: batch 1/525 (offset 0, added: 0)
...
[Scheduler] Active complete: +50000 added, -100 removed out of 1050000 total
[Scheduler] Passive complete: +120000 added, -500 removed out of 1200000 total
[Scheduler] IT complete: +30000 added, -200 removed out of 320000 total
[Scheduler] Full reconciliation complete in 2400000ms
```

### Manual Scheduler Triggers

**Depreciation:**
```bash
POST /depreciation-history/run-depreciation
# Returns 202 Accepted (async execution)
```

**Unmapped Inventory:**
```bash
POST /unmapped-inventory/reconcile/active
POST /unmapped-inventory/reconcile/passive
POST /unmapped-inventory/reconcile/it
POST /unmapped-inventory/reconcile/all
```

---

## Export System

### Export Job States

```
        ┌──────────┐
        │ PENDING  │
        └────┬─────┘
             │
             ▼
        ┌──────────┐
        │ RUNNING  │
        └────┬─────┘
             │
      ┌──────┴──────┐
      ▼             ▼
  ┌────────┐  ┌──────────┐
  │COMPLETED │  │  FAILED  │
  └────────┘  └──────────┘
```

### Pre-Warmed Exports

Generated automatically after:
- ✅ Depreciation scheduler completes
- ✅ Unmapped inventory reconciliation finishes

**Cache Duration:** Until next scheduled run

**Benefits:**
- ✅ Instant downloads (no generation delay)
- ✅ No filters needed for full dataset
- ✅ Reduced server load

### On-Demand Exports

Created when filters applied:
- ✅ Custom dataset export
- ✅ Background job processing
- ✅ 24-hour retention (configurable)

**Workflow:**
1. User submits export with filters
2. System assigns jobId
3. Background thread processes
4. User polls `/exports/status/{jobId}`
5. User downloads when ready

---

## Troubleshooting

### Issue: "Depreciation run is already in progress"

**Cause:** Previous run still executing  
**Solution:**
```
1. Check server logs for [DEPRECIATION] status
2. Monitor /depreciation-history/run-depreciation/status
3. Wait for current run to complete (check elapsed time)
4. If stuck >2 hours, restart service
```

### Issue: Export file not found (404)

**Cause:** Job ID invalid or file expired  
**Solution:**
```
1. Verify jobId is correct
2. Check /exports/status/{jobId} - confirm COMPLETED
3. Confirm within 24-hour retention window
4. Check file storage path permissions
```

### Issue: Slow export processing

**Cause:** Large filtered dataset, disk I/O bottleneck  
**Solution:**
```
1. Reduce filter scope (fewer records)
2. Try CSV format instead of Excel
3. Check disk space and I/O utilization
4. Consider pre-warmed exports (no filters)
```

### Issue: Out of memory during depreciation run

**Cause:** PAGE_SIZE or BATCH_SIZE too large  
**Solution:**
```
# In application.properties, adjust:
PAGE_SIZE=5000          (reduce from 10000)
BATCH_SIZE=1000         (reduce from 2000)

# Or increase JVM memory:
java -Xmx2G -jar alm-financials-service-1.0.0.jar
```

### Issue: Missing depreciation records

**Cause:** Assets missing required fields  
**Solution:**
```
Check server logs for skipped assets:
[DEPRECIATION] Asset AST-001 skipped (missing cost)
[DEPRECIATION] Asset AST-002 skipped (negative net cost)

Verify in FAR:
- cost >= 0
- life > 0
- datePlacedInService NOT NULL
- netCost > 0
```

### Issue: Unmapped items not detected

**Cause:** Scheduler not running or serial numbers mismatched  
**Solution:**
```
1. Confirm scheduler enabled in config
2. Check /unmapped-inventory/reconcile/status
3. Verify serial number formats match across systems
4. Check tb_FarReport has serial numbers populated
5. Manually trigger: POST /unmapped-inventory/reconcile/all
```

---

## API Rate Limits

- **Per Minute:** No hard limit (use backoff)
- **Concurrent Jobs:** 10 export jobs max
- **Upload Size:** 10,000 rows max
- **Page Size:** Max 200 records
- **File Retention:** 24 hours

---

## Security Considerations

✅ **CORS:** Enabled for all origins (configure in production)  
✅ **HTTPS:** Recommended for production  
✅ **Authentication:** Implement auth layer (OAuth2, JWT)  
✅ **Database:** Use encrypted passwords, least privilege  
✅ **Audit Trail:** All changes logged (createdBy, changedBy)  
✅ **File Storage:** Restrict access to export directory  

---

## Contributing

### Development Setup

```bash
git clone <repo>
cd alm-financials-service
mvn clean install
mvn spring-boot:run
```

### Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Coverage report
mvn jacoco:report
```

### Code Standards

- ✅ Java 17+ compliance
- ✅ Spring Boot best practices
- ✅ Comprehensive error handling
- ✅ Logging via SLF4J
- ✅ Transactional consistency

---

## Support & Documentation

| Resource | Link |
|----------|------|
| API Documentation | See `API_DOCUMENTATION.md` |
| Health Check | GET `/depreciation-history/health` |
| Server Logs | `[DEPRECIATION]`, `[Scheduler]`, `[ExportController]` |
| Database Schema | See "Database Schema" section |

---

## License

Proprietary -  KSA ALM 

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-03-04 | Initial production release |
| - | - | Depreciation scheduler with batch processing |
| - | - | Unmapped inventory reconciliation |
| - | - | Async export with pre-warming |
| - | - | REST API with pagination & filtering |

---

**Last Updated:** 2025-03-04  
**Maintainer:** ALM Financials Team  
**Author:** Uge