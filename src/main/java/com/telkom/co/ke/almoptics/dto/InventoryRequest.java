package com.telkom.co.ke.almoptics.dto;

import java.util.Map;

import java.util.Map;

public class InventoryRequest {
    private int page = 0;
    private int size = 100;
    private String searchColumn;
    private String searchQuery;
    private Map<String, Object> filterBy;

    // Standard Getters and Setters
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getSearchColumn() { return searchColumn; }
    public void setSearchColumn(String searchColumn) { this.searchColumn = searchColumn; }
    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }
    public Map<String, Object> getFilterBy() { return filterBy; }
    public void setFilterBy(Map<String, Object> filterBy) { this.filterBy = filterBy; }
}
