# ALM Financials Service - API Documentation

**Base URL:** `/depreciation-history`, `/far-report`, `/unmapped-inventory`, `/exports`

**API Version:** 2.0  
**Last Updated:** 2025-03-04

---

## Table of Contents
1. [Overview](#overview)
2. [Authentication & Headers](#authentication--headers)
3. [Depreciation History Endpoints](#depreciation-history-endpoints)
4. [FAR Report Endpoints](#far-report-endpoints)
5. [Unmapped Inventory Endpoints](#unmapped-inventory-endpoints)
6. [Export Management Endpoints](#export-management-endpoints)
7. [Response Formats](#response-formats)
8. [Error Handling](#error-handling)

---

## Overview

The ALM Financials Service provides REST APIs for managing asset depreciation, FAR (Fixed Asset Register) reports, and inventory reconciliation. All endpoints support async export with job polling and file streaming.

**Key Features:**
- ✓ Dynamic filtering with pagination
- ✓ Async export (CSV/Excel) with pre-warming
- ✓ Manual scheduler triggers
- ✓ Real-time status monitoring
- ✓ Streaming file downloads for large exports

---

## Authentication & Headers

All requests should include standard HTTP headers:

```
Content-Type: application/json
Accept: application/json
```

**CORS:** Enabled for all origins (`*`), max age 3600s

---

## Depreciation History Endpoints

### 1. GET /depreciation-history/list
**Fetch paginated depreciation history with optional filters**

**Method:** `POST`  
**Response:** `200 OK`

#### Request
```json
{
  "pageNumber": 0,
  "pageSize": 100,
  "filter": {
    "columnName": "assetId",
    "searchQuery": "AST-001",
    "dateFrom": "2025-01-01",
    "dateTo": "2025-03-04"
  }
}
```

**Query Parameters:**
| Parameter | Type | Default | Max | Description |
|-----------|------|---------|-----|-------------|
| pageNumber | int | 0 | - | 0-indexed page number |
| pageSize | int | 100 | 200 | Records per page |

**Filter Properties (optional):**
- `columnName`: Field to filter (e.g., "assetId", "depreciationPeriod", "netCost")
- `searchQuery`: Value to match
- `dateFrom` / `dateTo`: Date range filters (YYYY-MM-DD format)

#### Response
```json
{
  "status": "success",
  "message": "Found 100 records on page 1 of 5",
  "data": {
    "content": [
      {
        "recordNo": 1,
        "assetId": "AST-001",
        "depreciationPeriod": "2025-02",
        "monthlyDepreciationAmt": 1500.00,
        "accumulatedDepreciationAmt": 45000.00,
        "netCost": 155000.00,
        "depreciationDate": "2025-02-28T23:59:59",
        "createdBy": "SYSTEM",
        "changedBy": "DEPRECIATION_BATCH"
      },
      {
        "recordNo": 2,
        "assetId": "AST-002",
        "depreciationPeriod": "2025-02",
        "monthlyDepreciationAmt": 2000.00,
        "accumulatedDepreciationAmt": 60000.00,
        "netCost": 240000.00,
        "depreciationDate": "2025-02-28T23:59:59",
        "createdBy": "SYSTEM",
        "changedBy": "DEPRECIATION_BATCH"
      }
    ],
    "pageNumber": 0,
    "pageSize": 100,
    "totalPages": 5,
    "totalElements": 450
  }
}
```

---

### 2. POST /depreciation-history/export
**Asynchronously export depreciation history to CSV or Excel**

**Method:** `POST`  
**Response:** `202 Accepted`

#### Request
```json
{
  "columnName": "depreciationPeriod",
  "searchQuery": "2025-02"
}
```

**Query Parameters:**
| Parameter | Type | Default | Options |
|-----------|------|---------|---------|
| format | string | EXCEL | EXCEL, CSV |

#### Response
```json
{
  "status": "success",
  "message": "Export started. Use pollUrl to check status, downloadUrl to retrieve file.",
  "data": {
    "jobId": "JOB-DEP-1740909600000-12345",
    "status": "RUNNING",
    "pollUrl": "/exports/status/JOB-DEP-1740909600000-12345",
    "downloadUrl": "/exports/download/JOB-DEP-1740909600000-12345"
  }
}
```

**Export Strategies:**
- **No filters:** Returns pre-warmed export job ID (instant download available)
- **With filters:** Starts fresh on-demand export, retained for configured hours

---

### 3. POST /depreciation-history/run-depreciation
**Manually trigger depreciation processing**

**Method:** `POST`  
**Response:** `202 Accepted` or `409 Conflict`

#### Request
```json
{}
```

#### Success Response (202 Accepted)
```json
{
  "status": "success",
  "message": "Depreciation run triggered — check server logs for progress",
  "data": null
}
```

**Monitoring:** Check server logs for `[DEPRECIATION]` entries to follow progress.

#### Error Response (409 Conflict)
```json
{
  "status": "error",
  "message": "Depreciation run is already in progress",
  "data": null
}
```

---

### 4. GET /depreciation-history/run-depreciation/status
**Check if depreciation scheduler is running**

**Method:** `GET`  
**Response:** `200 OK`

#### Response
```json
{
  "status": "success",
  "message": "RUNNING",
  "data": "RUNNING"
}
```

**Possible Values:** `RUNNING` | `IDLE`

---

### 5. GET /depreciation-history/health
**Health check endpoint**

**Method:** `GET`  
**Response:** `200 OK`

#### Response
```json
{
  "status": "success",
  "message": "Depreciation history service is healthy",
  "data": null
}
```

---

## FAR Report Endpoints

### 1. POST /far-report/list
**Fetch paginated FAR report with optional filters and summary**

**Method:** `POST`  
**Response:** `200 OK`

#### Request
```json
{
  "columnName": "assetId",
  "searchQuery": "AST",
  "dateFrom": "2025-01-01",
  "dateTo": "2025-03-04"
}
```

**Query Parameters:**
| Parameter | Type | Default | Max |
|-----------|------|---------|-----|
| page | int | 0 | - |
| size | int | 100 | 500 |

#### Response
```json
{
  "status": "success",
  "message": "FAR report data with summary",
  "data": {
    "summary": {
      "totalAssets": 5000,
      "totalCost": 50000000.00,
      "totalAccumulatedDepreciation": 15000000.00,
      "totalNetCost": 35000000.00,
      "averageNetCost": 7000.00
    },
    "content": [
      {
        "assetId": "AST-001",
        "description": "Server Equipment",
        "serialNumber": "SN-2024-001",
        "category": "IT",
        "cost": 200000.00,
        "salvageValue": 20000.00,
        "life": 60,
        "datePlacedInService": "2022-01-15",
        "monthlyDepreciationAmt": 1500.00,
        "accumulatedDepreciationAmt": 45000.00,
        "netCost": 155000.00,
        "depreciationDate": "2025-02-28T23:59:59",
        "createdBy": "ADMIN",
        "changedBy": "SYSTEM"
      }
    ],
    "pageNumber": 0,
    "pageSize": 100,
    "totalPages": 50,
    "totalElements": 5000
  }
}
```

---

### 2. POST /far-report/upload
**Upload FAR data from external sources (Excel, CSV)**

**Method:** `POST`  
**Response:** `200 OK` or `400 Bad Request`

#### Request
```json
[
  {
    "assetId": "AST-NEW-001",
    "description": "New Server",
    "serialNumber": "SN-2025-100",
    "category": "IT",
    "cost": 150000.00,
    "salvageValue": 15000.00,
    "life": 60,
    "datePlacedInService": "2025-01-01",
    "depreciationAmount": 2250.00
  },
  {
    "assetId": "AST-NEW-002",
    "description": "Printer",
    "serialNumber": "SN-2025-101",
    "category": "Office",
    "cost": 5000.00,
    "salvageValue": 500.00,
    "life": 60,
    "datePlacedInService": "2025-01-15",
    "depreciationAmount": 75.00
  }
]
```

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| source | string | Excel | Data source (Excel, CSV, SAP, Oracle) |

**Validation Rules:**
- Max 10,000 rows per request
- Required fields: `assetId`, `cost`, `life`, `datePlacedInService`
- Serial numbers checked for uniqueness
- Dates must be in YYYY-MM-DD format

#### Success Response
```json
{
  "status": "success",
  "message": "Upload processed successfully",
  "data": {
    "processed": 2,
    "inserted": 2,
    "updated": 0,
    "failed": 0,
    "errors": [],
    "source": "Excel",
    "timestamp": "2025-03-04T10:30:45"
  }
}
```

#### Error Response (Bad Request)
```json
{
  "status": "error",
  "message": "No data provided",
  "data": null
}
```

---

### 3. POST /far-report/export
**Asynchronously export FAR report to CSV or Excel**

**Method:** `POST`  
**Response:** `202 Accepted`

#### Request
```json
{
  "columnName": "category",
  "searchQuery": "IT"
}
```

**Query Parameters:**
| Parameter | Type | Default | Options |
|-----------|------|---------|---------|
| format | string | EXCEL | EXCEL, CSV |

#### Response
```json
{
  "status": "success",
  "message": "Export started. Use pollUrl to check status, downloadUrl to retrieve file.",
  "data": {
    "jobId": "JOB-FAR-1740909600000-67890",
    "status": "RUNNING",
    "pollUrl": "/exports/status/JOB-FAR-1740909600000-67890",
    "downloadUrl": "/exports/download/JOB-FAR-1740909600000-67890"
  }
}
```

---

## Unmapped Inventory Endpoints

### 1. POST /unmapped-inventory/active
**Fetch unmapped active inventory (Node assets)**

**Method:** `POST`  
**Response:** `200 OK`

#### Request
```json
{
  "columnName": "model",
  "searchQuery": "Server"
}
```

**Query Parameters:**
| Parameter | Type | Default | Max |
|-----------|------|---------|-----|
| page | int | 0 | - |
| size | int | 100 | 500 |

#### Response
```json
{
  "status": "success",
  "message": "Active unmapped inventory",
  "data": {
    "content": [
      {
        "id": "NODE-001",
        "serialNumber": "SN-NODE-001",
        "node": "server-01",
        "siteId": "SITE-KSA-01",
        "model": "Dell R750",
        "partNumber": "PN-2024-001",
        "description": "Enterprise Server",
        "manufacturingDate": "2023-01-15",
        "inventoryType": "Hardware"
      }
    ],
    "pageNumber": 0,
    "pageSize": 100,
    "totalPages": 5,
    "totalElements": 450
  }
}
```

---

### 2. POST /unmapped-inventory/active/export
**Export active inventory with optional filtering**

**Method:** `POST`  
**Response:** `202 Accepted`

#### Request
```json
{
  "columnName": "siteId",
  "searchQuery": "SITE-KSA-01"
}
```

**Query Parameters:**
| Parameter | Type | Default | Options |
|-----------|------|---------|---------|
| format | string | EXCEL | EXCEL, CSV |

#### Response
```json
{
  "status": "success",
  "message": "Export started. Use pollUrl to check status, downloadUrl to retrieve file.",
  "data": {
    "jobId": "JOB-UNMAPPED-ACTIVE-1740909600000-11111",
    "status": "RUNNING",
    "pollUrl": "/exports/status/JOB-UNMAPPED-ACTIVE-1740909600000-11111",
    "downloadUrl": "/exports/download/JOB-UNMAPPED-ACTIVE-1740909600000-11111"
  }
}
```

---

### 3. POST /unmapped-inventory/passive
**Fetch unmapped passive inventory (stored items)**

**Method:** `POST`  
**Response:** `200 OK`

#### Request
```json
{
  "columnName": "itemStatus",
  "searchQuery": "Active"
}
```

#### Response
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "inventoryId": "PASSIVE-001",
        "serialNumber": "SN-PASSIVE-001",
        "objectId": "OBJ-2024-001",
        "parentName": "Warehouse A",
        "siteId": "SITE-KSA-02",
        "itemBarCode": "BAR-001",
        "model": "HP Printer",
        "note": "Unused equipment",
        "part": "PART-002",
        "entryUser": "USER-001",
        "entryDate": "2024-12-01",
        "itemStatus": "Active",
        "categoryInNEP": "Office Equipment",
        "scrapStatus": "No",
        "inventoryType": "Hardware"
      }
    ],
    "pageNumber": 0,
    "pageSize": 100,
    "totalPages": 3,
    "totalElements": 250
  }
}
```

---

### 4. POST /unmapped-inventory/passive/export
**Export passive inventory with optional filtering**

**Method:** `POST`  
**Response:** `202 Accepted`

#### Request
```json
{
  "columnName": "scrapStatus",
  "searchQuery": "No"
}
```

**Query Parameters:**
| Parameter | Type | Default | Options |
|-----------|------|---------|---------|
| format | string | EXCEL | EXCEL, CSV |

#### Response
```json
{
  "status": "success",
  "message": "Export started.",
  "data": {
    "jobId": "JOB-UNMAPPED-PASSIVE-1740909600000-22222",
    "status": "RUNNING",
    "pollUrl": "/exports/status/JOB-UNMAPPED-PASSIVE-1740909600000-22222",
    "downloadUrl": "/exports/download/JOB-UNMAPPED-PASSIVE-1740909600000-22222"
  }
}
```

---

### 5. POST /unmapped-inventory/it
**Fetch unmapped IT inventory (hardware inventory)**

**Method:** `POST`  
**Response:** `200 OK`

#### Request
```json
{
  "columnName": "hostTypeName",
  "searchQuery": "Server"
}
```

#### Response
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "recordDateTime": "2025-02-28T15:30:00",
        "objectId": "OBJ-IT-001",
        "siteId": "SITE-KSA-03",
        "hostSerialNumber": "SN-IT-SERVER-001",
        "inventoryType": "Server",
        "hostTypeName": "Linux Server",
        "firstScan": "2024-06-01",
        "ipAddress": "192.168.1.10",
        "osName": "Ubuntu 20.04",
        "hardwareVendorName": "Dell",
        "model": "PowerEdge R750",
        "isVirtual": false,
        "category": "Infrastructure"
      }
    ],
    "pageNumber": 0,
    "pageSize": 100,
    "totalPages": 2,
    "totalElements": 180
  }
}
```

---

### 6. POST /unmapped-inventory/it/export
**Export IT inventory with optional filtering**

**Method:** `POST`  
**Response:** `202 Accepted`

#### Request
```json
{
  "columnName": "isVirtual",
  "searchQuery": "false"
}
```

**Query Parameters:**
| Parameter | Type | Default | Options |
|-----------|------|---------|---------|
| format | string | EXCEL | EXCEL, CSV |

#### Response
```json
{
  "status": "success",
  "message": "Export started.",
  "data": {
    "jobId": "JOB-UNMAPPED-IT-1740909600000-33333",
    "status": "RUNNING",
    "pollUrl": "/exports/status/JOB-UNMAPPED-IT-1740909600000-33333",
    "downloadUrl": "/exports/download/JOB-UNMAPPED-IT-1740909600000-33333"
  }
}
```

---

### 7. POST /unmapped-inventory/reconcile/active
**Trigger active inventory reconciliation**

**Method:** `POST`  
**Response:** `200 OK` or `409 Conflict`

#### Request
```json
{}
```

#### Success Response
```json
{
  "status": "success",
  "message": "Active reconciliation triggered",
  "data": null
}
```

#### Conflict Response (409)
```json
{
  "status": "error",
  "message": "Active reconciliation is already running",
  "data": null
}
```

---

### 8. POST /unmapped-inventory/reconcile/passive
**Trigger passive inventory reconciliation**

**Method:** `POST`  
**Response:** `200 OK` or `409 Conflict`

#### Request
```json
{}
```

#### Response
```json
{
  "status": "success",
  "message": "Passive reconciliation triggered",
  "data": null
}
```

---

### 9. POST /unmapped-inventory/reconcile/it
**Trigger IT inventory reconciliation**

**Method:** `POST`  
**Response:** `200 OK` or `409 Conflict`

#### Request
```json
{}
```

#### Response
```json
{
  "status": "success",
  "message": "IT reconciliation triggered",
  "data": null
}
```

---

### 10. POST /unmapped-inventory/reconcile/all
**Trigger full inventory reconciliation (all types)**

**Method:** `POST`  
**Response:** `200 OK` or `409 Conflict`

#### Request
```json
{}
```

#### Response
```json
{
  "status": "success",
  "message": "Full reconciliation triggered",
  "data": null
}
```

---

### 11. GET /unmapped-inventory/reconcile/status
**Get current status of all reconciliations**

**Method:** `GET`  
**Response:** `200 OK`

#### Response
```json
{
  "status": "success",
  "message": {
    "active": "IDLE",
    "passive": "RUNNING",
    "it": "IDLE"
  },
  "data": {
    "active": "IDLE",
    "passive": "RUNNING",
    "it": "IDLE"
  }
}
```

**Possible Values per Type:** `RUNNING` | `IDLE`

---

## Export Management Endpoints

### 1. GET /exports/status/{jobId}
**Get export job status (progress, state, etc)**

**Method:** `GET`  
**Response:** `200 OK` or `404 Not Found`

#### URL Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| jobId | string | Export job ID from export endpoint |

#### Response
```json
{
  "status": "success",
  "message": {
    "jobId": "JOB-DEP-1740909600000-12345",
    "status": "COMPLETED",
    "progress": 100,
    "recordsProcessed": 5000,
    "fileName": "depreciation_export_2025-03-04.xlsx",
    "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "fileSize": 2456789,
    "createdAt": "2025-03-04T10:30:45",
    "completedAt": "2025-03-04T10:35:22"
  },
  "data": {
    "jobId": "JOB-DEP-1740909600000-12345",
    "status": "COMPLETED",
    "progress": 100,
    "recordsProcessed": 5000,
    "fileName": "depreciation_export_2025-03-04.xlsx",
    "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "fileSize": 2456789,
    "createdAt": "2025-03-04T10:30:45",
    "completedAt": "2025-03-04T10:35:22"
  }
}
```

**Job Status Values:**
- `PENDING` - Waiting to start
- `RUNNING` - In progress
- `COMPLETED` - Successfully finished
- `FAILED` - Encountered error
- `CANCELLED` - User cancelled

---

### 2. GET /exports/download/{jobId}
**Download completed export file**

**Method:** `GET`  
**Response:** `200 OK` (file stream) | `404 Not Found` | `500 Internal Server Error`

#### URL Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| jobId | string | Export job ID |

#### Success Response (200)
- **Content-Type:** `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (for Excel) or `text/csv` (for CSV)
- **Content-Length:** File size in bytes
- **Content-Disposition:** `attachment; filename="depreciation_export_2025-03-04.xlsx"`
- **Body:** Binary file stream

#### Example cURL
```bash
curl -X GET \
  "http://api.example.com/exports/download/JOB-DEP-1740909600000-12345" \
  -O -J
```

#### Error Response (404)
```json
{
  "status": "error",
  "message": "Export job not found: JOB-INVALID-ID",
  "data": null
}
```

---

## Response Formats

### Standard API Response Envelope

All endpoints return a consistent response structure:

```json
{
  "status": "success|error",
  "message": "Human-readable message",
  "data": null
}
```

### Paginated Response Format

```json
{
  "status": "success",
  "message": "Found X records on page Y of Z",
  "data": {
    "content": [],
    "pageNumber": 0,
    "pageSize": 100,
    "totalPages": 10,
    "totalElements": 1000
  }
}
```

### Export Job Response Format

```json
{
  "status": "success",
  "message": "Export started...",
  "data": {
    "jobId": "JOB-xxx",
    "status": "RUNNING",
    "pollUrl": "/exports/status/JOB-xxx",
    "downloadUrl": "/exports/download/JOB-xxx"
  }
}
```

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| 200 | OK | Successful GET/POST request |
| 202 | Accepted | Async job started |
| 400 | Bad Request | Invalid data in payload |
| 404 | Not Found | Resource/job does not exist |
| 409 | Conflict | Process already running |
| 500 | Internal Server Error | Server error during processing |

### Error Response Format

```json
{
  "status": "error",
  "message": "Depreciation run is already in progress",
  "data": null
}
```

### Common Error Scenarios

**1. Invalid Pagination**
```json
{
  "status": "error",
  "message": "Page size must be between 1 and 200",
  "data": null
}
```

**2. Export Job Not Found**
```json
{
  "status": "error",
  "message": "Export job not found: JOB-INVALID-ID",
  "data": null
}
```

**3. Process Already Running**
```json
{
  "status": "error",
  "message": "Depreciation run is already in progress",
  "data": null
}
```

**4. Upload Exceeds Limit**
```json
{
  "status": "error",
  "message": "Upload limited to 10,000 rows per request",
  "data": null
}
```

---

## Best Practices

### Export Workflow

1. **Start Export**
   ```
   POST /depreciation-history/export
   ↓ Returns: jobId, pollUrl, downloadUrl
   ```

2. **Poll Status** (every 2-5 seconds)
   ```
   GET /exports/status/{jobId}
   ↓ Returns: status (RUNNING, COMPLETED, FAILED)
   ```

3. **Download File** (when status = COMPLETED)
   ```
   GET /exports/download/{jobId}
   ↓ Returns: Binary file stream
   ```

### Filtering Best Practices

- Use `columnName` + `searchQuery` for exact or partial matching
- Use `dateFrom` + `dateTo` for date range filtering
- Combine filters by including all fields in request body
- Leave filter null/empty for unfiltered results

### Pagination

- Start with `pageNumber: 0`
- Max `pageSize: 200` (smaller pages = faster requests)
- Check `totalPages` in response to determine iteration
- Use `totalElements` to gauge data volume

### Error Recovery

- Retry failed requests with exponential backoff
- Check `/health` endpoints before major operations
- Monitor status regularly for long-running jobs
- Implement timeout handling for export polling

---

## Scheduled Jobs

### Depreciation Scheduler
- **Schedule:** Monthly (Last day of month at 00:00 Asia/Riyadh)
- **Processing:** 1M assets in 15-20 min
- **Features:** Auto pre-warm export on success
- **Log Prefix:** `[DEPRECIATION]`

### Unmapped Inventory Scheduler
- **Schedule:** Nightly (20:00 UTC)
- **Processing:** Reconciles Active/Passive/IT inventories
- **Features:** Auto pre-warm exports on completion
- **Log Prefix:** `[Scheduler]`

---

## Rate Limiting & Pagination Limits

- **Max Page Size:** 200
- **Default Page Size:** 100
- **Upload Batch Size:** Max 10,000 rows
- **Depreciation Batch Size:** 2,000 records (internal)
- **CORS Cache:** 3600 seconds

---

## Support & Monitoring

### Health Checks
```bash
GET /depreciation-history/health
GET /far-report/health (if available)
GET /unmapped-inventory/health (if available)
```

### Server Logs
- Depreciation: `[DEPRECIATION]` prefix
- Unmapped Inventory: `[Scheduler]` prefix
- Export: `[ExportController]` prefix
- Unmapped Controller: `[UnmappedInventoryController]` prefix

### Troubleshooting
- **Slow responses:** Check page size, reduce to 50-100
- **Export failures:** Verify filters and try full export
- **Already running:** Wait for current job to complete
- **File not found:** Confirm jobId and status is COMPLETED