package com.zain.ksa.alm.financials.dto.request;


import lombok.Data;

/**
 * Query parameters for filtering active and passive inventory.
 */
@Data
public class InventoryFilterRequest {
    private String siteId;
    private Boolean isMapped;
    private String serialNumber;
}