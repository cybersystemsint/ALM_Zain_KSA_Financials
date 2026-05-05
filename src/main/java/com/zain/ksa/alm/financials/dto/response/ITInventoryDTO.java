package com.zain.ksa.alm.financials.dto.response;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Value
@Builder
public class ITInventoryDTO {
    Long recordNo;
    LocalDateTime recordDateTime;
    String siteId;
    String firstScan;
    String ipAddress;
    String objectId;
    String osId;            
    String osName;
    Integer hardwareVendorId;
    String hardwareVendorName;
    String model;
    BigDecimal virtual;      
    Integer hostTypeId;
    String hostTypeName;
    String hostSerialNumber;
    Integer inventoryTypeId;
    String inventoryType;
    String category;
    Long isMapped;
}