package com.zain.ksa.alm.financials.dto.response;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
@With 
public class UnmappedActiveInventoryDTO {
    Long sequenceNo;
    Long recordNo;
    LocalDateTime recordDateTime;
    String nodeId;
    String nodeName;
    String nodeType;
    String serialNumber;
    String model;
    String partNumber;
    String siteId;
    String manufacturer;
    String description;
    LocalDate manufacturingDate;
    LocalDate installationDate;
    LocalDate assetInsertionDate;
    String warranty;
}
