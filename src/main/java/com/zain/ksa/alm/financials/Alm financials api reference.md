# ALM Financials — Frontend API Reference

**Base URL:** `http://194.164.17.17:8080/alm_ksa_financials`  
**Date:** March 2026

---

## 1. Depreciation History

### 1.1 Fetch (Paginated + Filterable)

```
GET /depreciation-history
```

| Param | Type | Description |
|-------|------|-------------|
| `columnName` | string | Column to search (e.g. `assetId`, `category`, `serialNumber`) |
| `searchQuery` | string | LIKE search value for the column above |
| `filterBy[key]` | string | Exact-match filter. Can pass multiple: `filterBy[statusFlag]=ACTIVE&filterBy[nodeType]=BTS` |
| `dateFrom` | datetime | Start of date range (format: `2024-01-01T00:00:00`) |
| `dateTo` | datetime | End of date range (format: `2024-12-31T23:59:59`) |
| `page` | int | Page number (0-based, default 0) |
| `size` | int | Page size (default 50) |
| `sort` | string | Sort field (default `recordNo,DESC`) |

**Examples:**
```
GET /depreciation-history?columnName=assetId&searchQuery=ASSET-001
GET /depreciation-history?filterBy[statusFlag]=ACTIVE&filterBy[mapped]=Y
GET /depreciation-history?filterBy[depreciationPeriod]=2025-06
GET /depreciation-history?dateFrom=2024-01-01T00:00:00&dateTo=2024-12-31T23:59:59
GET /depreciation-history?columnName=assetId&searchQuery=ASSET&filterBy[statusFlag]=ACTIVE&page=0&size=50
```

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [ { "recordNo": 1, "assetId": "...", "depreciationPeriod": "2026-03", ... } ],
    "totalElements": 150000,
    "totalPages": 3000,
    "pageNumber": 0,
    "pageSize": 50
  }
}
```

---

### 1.2 Get Single Record

```
GET /depreciation-history/{id}
```

**Response:**
```json
{
  "success": true,
  "data": { "recordNo": 1, "assetId": "...", ... }
}
```

---

### 1.3 Export (Async — user can navigate away)

**Step 1: Trigger export**

```
POST /depreciation-history/export?format=CSV
POST /depreciation-history/export?format=EXCEL
```

> **Note:** This is a POST, not GET. Format is passed as query param.  
> Optional: send `DynamicFilterRequest` as JSON body to filter the export.

**Request body (optional):**
```json
{
  "columnName": "category",
  "searchQuery": "TOWER",
  "filterBy": { "statusFlag": "ACTIVE" },
  "dateFrom": "2024-01-01T00:00:00",
  "dateTo": "2024-12-31T23:59:59"
}
```

**Response (immediate — 200):**
```json
{
  "success": true,
  "data": {
    "jobId": "a1b2c3d4",
    "status": "RUNNING",
    "pollUrl": "/exports/status/a1b2c3d4",
    "downloadUrl": "/exports/download/a1b2c3d4"
  }
}
```

**Step 2: Poll status (every 3-5 seconds)**

```
GET /exports/status/{jobId}
```

**Response while running:**
```json
{
  "success": true,
  "data": {
    "jobId": "a1b2c3d4",
    "exportType": "depreciation",
    "status": "RUNNING",
    "startedAt": "2026-03-01T22:00:00",
    "completedAt": null,
    "totalRows": 3000000,
    "processedRows": 1860000,
    "progressPercent": 62
  }
}
```

| Field | Use |
|-------|-----|
| `progressPercent` | Progress bar width (0-100) |
| `processedRows` | Display text: "1,860,000 exported..." |
| `totalRows` | Display text: "... of 3,000,000" |

**Response when done:**
```json
{
  "success": true,
  "data": {
    "jobId": "a1b2c3d4",
    "exportType": "depreciation",
    "status": "COMPLETED",
    "startedAt": "2026-03-01T22:00:00",
    "completedAt": "2026-03-01T22:02:30",
    "totalRows": 3000000,
    "processedRows": 3000000,
    "progressPercent": 100,
    "downloadUrl": "/exports/download/a1b2c3d4"
  }
}
```

**Response if failed:**
```json
{
  "success": true,
  "data": {
    "jobId": "a1b2c3d4",
    "exportType": "depreciation",
    "status": "FAILED",
    "startedAt": "2026-03-01T22:00:00",
    "completedAt": "2026-03-01T22:00:45",
    "totalRows": 3000000,
    "processedRows": 540000,
    "progressPercent": 18,
    "error": "Database connection timeout"
  }
}
```

**Step 3: Download file (when status = COMPLETED)**

```
GET /exports/download/{jobId}
```

> Opens file download (CSV or XLSX). Use `window.open()` or `<a href="..." download>`.  
> Files auto-delete after 2 hours.

---

### 1.4 Run Depreciation Scheduler (Manual Trigger)

**Trigger:**
```
POST /depreciation-history/run-depreciation
```

**Response (200 — job started):**
```json
{
  "success": true,
  "data": "Depreciation scheduler triggered successfully"
}
```

**Response (409 — already running):**
```json
{
  "success": false,
  "message": "Depreciation scheduler is already running"
}
```

**Check status:**
```
GET /depreciation-history/run-depreciation/status
```

**Response:**
```json
{
  "success": true,
  "data": "RUNNING"
}
```
or
```json
{
  "success": true,
  "data": "IDLE"
}
```

---

## 2. Unmapped Inventory

### 2.1 Active Inventory — Fetch

```
GET /unmapped-inventory/active
```

| Param | Type | Description |
|-------|------|-------------|
| `columnName` | string | Column to search (e.g. `serialNumber`, `nodeName`, `model`) |
| `searchQuery` | string | LIKE search value |
| `filterBy[key]` | string | Exact-match filter (e.g. `filterBy[siteId]=SITE01&filterBy[nodeType]=BTS`) |
| `page` | int | Page number (0-based, default 0) |
| `size` | int | Page size (default 50) |

**Examples:**
```
GET /unmapped-inventory/active?columnName=serialNumber&searchQuery=SN-123
GET /unmapped-inventory/active?filterBy[siteId]=SITE01&filterBy[nodeType]=BTS
```

---

### 2.2 Passive Inventory — Fetch

```
GET /unmapped-inventory/passive
```

Same params as active. Example:
```
GET /unmapped-inventory/passive?filterBy[categoryInNEP]=SHELTER
GET /unmapped-inventory/passive?columnName=serialNumber&searchQuery=ABC
```

---

### 2.3 IT Inventory — Fetch

```
GET /unmapped-inventory/it
```

Same params as active. Example:
```
GET /unmapped-inventory/it?filterBy[inventoryType]=PHYSICAL
GET /unmapped-inventory/it?columnName=hostSerialNumber&searchQuery=HOST-001
```

---

### 2.4 Unmapped Exports (Async — same pattern as depreciation)

**Trigger:**
```
POST /unmapped-inventory/active/export?format=CSV
POST /unmapped-inventory/active/export?format=EXCEL
POST /unmapped-inventory/passive/export?format=CSV
POST /unmapped-inventory/passive/export?format=EXCEL
POST /unmapped-inventory/it/export?format=CSV
POST /unmapped-inventory/it/export?format=EXCEL
```

**Response (same as depreciation export):**
```json
{
  "success": true,
  "data": {
    "jobId": "x9y8z7w6",
    "status": "RUNNING",
    "pollUrl": "/exports/status/x9y8z7w6",
    "downloadUrl": "/exports/download/x9y8z7w6"
  }
}
```

Then poll `GET /exports/status/{jobId}` and download `GET /exports/download/{jobId}` — same flow as Section 1.3.

---

### 2.5 Reconciliation — Manual Triggers (Async)

**Trigger specific type:**
```
POST /unmapped-inventory/reconcile/active
POST /unmapped-inventory/reconcile/passive
POST /unmapped-inventory/reconcile/it
```

**Trigger all three at once:**
```
POST /unmapped-inventory/reconcile/all
```

**Response (200):**
```json
{
  "success": true,
  "data": "Active reconciliation triggered"
}
```

**Response (409 — already running):**
```json
{
  "success": false,
  "message": "Active reconciliation is already running"
}
```

**Check status of all reconciliations:**
```
GET /unmapped-inventory/reconcile/status
```

**Response:**
```json
{
  "success": true,
  "data": {
    "active": "RUNNING",
    "passive": "IDLE",
    "it": "IDLE"
  }
}
```

---

## 3. Export Download (Shared Endpoints)

These are used by ALL export types (depreciation, active, passive, IT):

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/exports/status/{jobId}` | GET | Poll export progress. Returns `status`, `progressPercent`, `totalRows`, `processedRows`, `downloadUrl` |
| `/exports/download/{jobId}` | GET | Download the file when status is `COMPLETED`. Auto-deleted after 2 hours |

**Full status response fields:**

| Field | Type | When Present | Description |
|-------|------|--------------|-------------|
| `jobId` | string | Always | The export job identifier |
| `exportType` | string | Always | `depreciation`, `unmapped_active`, `unmapped_passive`, `unmapped_it` |
| `status` | string | Always | `RUNNING`, `COMPLETED`, or `FAILED` |
| `startedAt` | string | Always | ISO datetime when export started |
| `completedAt` | string | When done | ISO datetime when export finished |
| `totalRows` | long | Always | Total rows to export |
| `processedRows` | long | Always | Rows exported so far |
| `progressPercent` | int | Always | 0-100, for progress bar |
| `downloadUrl` | string | COMPLETED only | Path to download the file |
| `error` | string | FAILED only | Error message |

---

## 4. Frontend Implementation Guide

### Export Flow — Production UX

The export happens in **two phases**:
1. **Phase 1: Server generates the file** (tracked via polling — shows progress toast)
2. **Phase 2: Browser downloads the file** (normal browser download — shows in Chrome download bar)

#### Recommended UX: Global Export Toast/Banner

Place a **global notification component** in your root layout (App.vue / App.tsx / app.component).
This toast persists across page navigation.

```
┌──────────────────────────────────────────────────┐
│  📦 Exporting Depreciation History (EXCEL)       │
│  ████████████░░░░░░░░  62%  (1,860,000 / 3M)    │
│                                          [Cancel]│
└──────────────────────────────────────────────────┘
```

When complete, the toast changes to:

```
┌──────────────────────────────────────────────────┐
│  ✅ Depreciation History export ready!            │
│  depreciation_export_a1b2c3d4.xlsx               │
│                                       [Download] │
└──────────────────────────────────────────────────┘
```

When failed:

```
┌──────────────────────────────────────────────────┐
│  ❌ Export failed: Database connection timeout     │
│                                          [Retry] │
└──────────────────────────────────────────────────┘
```

#### Step-by-Step Code Flow

```
1. User clicks "Export" button on any page
    ↓
2. POST /depreciation-history/export?format=EXCEL
    ↓
3. Response: { jobId: "a1b2c3d4", status: "RUNNING", ... }
    ↓
4. Store jobId in GLOBAL state (Redux / Vuex / Context / Service)
   — NOT component-level state, so it survives page navigation
    ↓
5. Show export toast with progress bar (in root/layout component)
    ↓
6. Start polling: GET /exports/status/{jobId} every 3 seconds
    ↓
7. On each poll response, update the toast:
   - progressPercent → progress bar width
   - processedRows / totalRows → text like "1.2M / 3M rows"
    ↓
8. When status === "COMPLETED":
   - Stop polling
   - Show "Download Ready" toast with download button
   - On click: window.open(BASE_URL + "/exports/download/" + jobId)
   - This triggers normal browser download (Chrome download bar)
    ↓
9. When status === "FAILED":
   - Stop polling
   - Show error toast with retry button
```

#### Poll Response Fields for Progress Bar

```json
{
  "success": true,
  "data": {
    "jobId": "a1b2c3d4",
    "exportType": "depreciation",
    "status": "RUNNING",
    "startedAt": "2026-03-01T23:00:35",
    "completedAt": null,
    "totalRows": 3000000,
    "processedRows": 1860000,
    "progressPercent": 62
  }
}
```

| Field | Use |
|-------|-----|
| `status` | Controls toast state: RUNNING → progress bar, COMPLETED → download button, FAILED → error |
| `progressPercent` | Progress bar width (0-100) |
| `processedRows` | Display: "1,860,000 rows exported..." |
| `totalRows` | Display: "... of 3,000,000" |
| `downloadUrl` | Only present when COMPLETED — use for download button |
| `error` | Only present when FAILED — show in error toast |

#### Important Notes for Frontend

- **Store jobId in global state** — component state is lost on navigation
- **Multiple exports can run simultaneously** — store as array of jobs
- **Poll interval: 3 seconds** — not too fast (server load), not too slow (user waits)
- **Stop polling when status is COMPLETED or FAILED**
- **Download uses window.open()** — the browser handles the file download natively
- **Files auto-delete after 2 hours** — show warning if user hasn't downloaded yet

### Scheduler Trigger Flow

```
User clicks "Run Depreciation" button
    ↓
POST /depreciation-history/run-depreciation
    ↓
If 409 → show "Already running" message
If 200 → show "Triggered" toast
    ↓
Optionally poll GET /depreciation-history/run-depreciation/status
to show RUNNING/IDLE indicator on the page
```

### Reconciliation Trigger Flow

```
User clicks "Reconcile Active" (or Passive/IT/All)
    ↓
POST /unmapped-inventory/reconcile/active
    ↓
If 409 → show "Already running" message
If 200 → show "Triggered" toast
    ↓
Poll GET /unmapped-inventory/reconcile/status
to show which reconciliations are running
```

---

## 5. Quick Reference — All Endpoints

### Depreciation History
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/depreciation-history` | Paginated list with filters |
| GET | `/depreciation-history/{id}` | Single record |
| POST | `/depreciation-history/export?format=CSV\|EXCEL` | Start async export |
| POST | `/depreciation-history/run-depreciation` | Trigger depreciation scheduler |
| GET | `/depreciation-history/run-depreciation/status` | Check scheduler status |

### Unmapped Inventory
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/unmapped-inventory/active` | Active inventory list |
| GET | `/unmapped-inventory/passive` | Passive inventory list |
| GET | `/unmapped-inventory/it` | IT inventory list |
| POST | `/unmapped-inventory/active/export?format=CSV\|EXCEL` | Export active |
| POST | `/unmapped-inventory/passive/export?format=CSV\|EXCEL` | Export passive |
| POST | `/unmapped-inventory/it/export?format=CSV\|EXCEL` | Export IT |
| POST | `/unmapped-inventory/reconcile/active` | Trigger active reconciliation |
| POST | `/unmapped-inventory/reconcile/passive` | Trigger passive reconciliation |
| POST | `/unmapped-inventory/reconcile/it` | Trigger IT reconciliation |
| POST | `/unmapped-inventory/reconcile/all` | Trigger full reconciliation |
| GET | `/unmapped-inventory/reconcile/status` | Check reconciliation status |

### Shared Export Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/exports/status/{jobId}` | Poll export job status |
| GET | `/exports/download/{jobId}` | Download completed export file |