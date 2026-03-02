package com.zain.ksa.alm.financials.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class DynamicFilterRequest {

    private String columnName = "";
    private String searchQuery = "";
    private Map<String, String> filterBy = new HashMap<>();

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime dateFrom;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime dateTo;

    private Boolean isMapped;
    private String siteId = "";

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

    public Map<String, String> getFilterBy() {
        return filterBy;
    }

    public void setFilterBy(Map<String, String> filterBy) {
        this.filterBy = filterBy;
    }

    public LocalDateTime getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDateTime dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDateTime getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDateTime dateTo) {
        this.dateTo = dateTo;
    }

    public Boolean getIsMapped() {
        return isMapped;
    }

    public void setIsMapped(Boolean isMapped) {
        this.isMapped = isMapped;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }
}