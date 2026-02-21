package com.telkom.co.ke.almoptics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class FarReportExportRequest {

    @JsonProperty("page")
    private Integer page;

    @JsonProperty("size")
    private Integer size;

    @JsonProperty("format")
    private String format = "excel";

    // ========================================================================
    // LEGACY/SIMPLE FORMAT FIELDS (for backward compatibility)
    // ========================================================================
    @JsonProperty("assetId")
    private String assetId;

    @JsonProperty("columnName")
    private String columnName;

    @JsonProperty("searchQuery")
    private String searchQuery;

    @JsonProperty("dateFrom")
    private String dateFrom;

    @JsonProperty("dateTo")
    private String dateTo;

    // ========================================================================
    // ADVANCED FORMAT FIELD
    // ========================================================================
    @JsonProperty("filterBy")
    private Map<String, FilterCriteria> filterBy;

    // Default constructor
    public FarReportExportRequest() {
    }

    // Getters and Setters
    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public String getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }

    public String getDateTo() {
        return dateTo;
    }

    public void setDateTo(String dateTo) {
        this.dateTo = dateTo;
    }

    public Map<String, FilterCriteria> getFilterBy() {
        return filterBy;
    }

    public void setFilterBy(Map<String, FilterCriteria> filterBy) {
        this.filterBy = filterBy;
    }

    @Override
    public String toString() {
        return "FarReportExportRequest{" +
                "page=" + page +
                ", size=" + size +
                ", format='" + format + '\'' +
                ", assetId='" + assetId + '\'' +
                ", dateFrom='" + dateFrom + '\'' +
                ", dateTo='" + dateTo + '\'' +
                ", columnName='" + columnName + '\'' +
                ", searchQuery='" + searchQuery + '\'' +
                ", filterBy=" + filterBy +
                '}';
    }

    // Inner class for filter criteria
    public static class FilterCriteria {

        @JsonProperty("operator")
        private String operator;

        @JsonProperty("value")
        private String value;

        // Default constructor
        public FilterCriteria() {
        }

        // Constructor with parameters
        public FilterCriteria(String operator, String value) {
            this.operator = operator;
            this.value = value;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "FilterCriteria{" +
                    "operator='" + operator + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }
}