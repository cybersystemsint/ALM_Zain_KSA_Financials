package com.telkom.co.ke.almoptics.services;

import org.springframework.core.io.InputStreamResource;

import java.util.Map;

/**
 * FAR Export Service API
 */
public interface FarExportService {

    /**
     * Export FAR Report data to Excel format
     * @param requestParams Map containing filter params
     * @return InputStreamResource with Excel content
     */
    InputStreamResource exportFarReportToExcel(Map<String, Object> requestParams);

    /**
     * Export FAR Report data to CSV format
     * @param requestParams Map containing filter params
     * @return CSV data as byte array
     */
    byte[] exportFarReportToCsv(Map<String, Object> requestParams);

    /**
     * Get total count of filtered records
     * @param requestParams Map containing filter params
     * @return count of filtered records
     */
    long getTotalFilteredRecords(Map<String, Object> requestParams);
}
