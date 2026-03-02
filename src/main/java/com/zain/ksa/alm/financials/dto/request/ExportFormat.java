package com.zain.ksa.alm.financials.dto.request;


/**
 * Supported export formats.
 * Adding a new format requires only adding an enum constant
 * and implementing a corresponding ExportStrategy.
 */
public enum ExportFormat {
    CSV,
    EXCEL
}