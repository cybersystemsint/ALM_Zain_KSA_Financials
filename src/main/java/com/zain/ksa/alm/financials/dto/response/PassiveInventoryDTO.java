package com.zain.ksa.alm.financials.dto.response;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

@Value
@Builder
public class PassiveInventoryDTO {
    Integer recordNo;
    LocalDateTime recordDateTime;
    String siteId;
    String objectId;
    String serialNumber;
    String model;
    String itemCode;
    String itemStatus;
    String categoryInNEP;
    String locationClassification;
    String locationSubType;
    String itemClassification;
    String itemClassification2;
    String scrapStatus;
    String ownership;
    String PRPONo;
    Boolean isMapped;
    LocalDateTime changedDate;
}