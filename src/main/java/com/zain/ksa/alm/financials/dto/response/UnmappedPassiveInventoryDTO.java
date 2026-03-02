package com.zain.ksa.alm.financials.dto.response;


import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

@Value
@Builder
public class UnmappedPassiveInventoryDTO {
    Long id;
    LocalDateTime recordDateTime;
    String inventoryId;
    String objectId;
    String siteId;
    String serialNumber;
    String model;
    String itemStatus;
    String categoryInNEP;
    String scrapStatus;
    String inventoryType;
    Integer inventoryTypeId;
    String locationSubType;
    String locationClassification;
    String itemClassification;
    String itemClassification2;
    String prPoNo;
}
