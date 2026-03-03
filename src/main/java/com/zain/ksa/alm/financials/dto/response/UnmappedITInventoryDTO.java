package com.zain.ksa.alm.financials.dto.response;


import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;

@Value
@Builder
@With 
public class UnmappedITInventoryDTO {
    Long sequenceNo;
    Long id;
    LocalDateTime recordDatetime;
    String objectId;
    String siteId;
    String hostSerialNumber;
    String inventoryType;
    Integer inventoryTypeId;
    String hostTypeName;
    String osName;
    String hardwareVendorName;
    String model;
    Boolean isVirtual;
    String category;
}
