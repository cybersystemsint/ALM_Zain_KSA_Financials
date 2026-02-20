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

    @JsonProperty("columnName")
    private String columnName;

    @JsonProperty("searchQuery")
    private String searchQuery;

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