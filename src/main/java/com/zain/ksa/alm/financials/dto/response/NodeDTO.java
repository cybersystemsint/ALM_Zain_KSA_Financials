package com.zain.ksa.alm.financials.dto.response;

import lombok.Builder;
import lombok.Value;
import java.util.Date;
import java.time.LocalDateTime;


@Value
@Builder
public class NodeDTO {
    Integer id;
    String nodeName;         
    Integer siteId;
    String technologySupported;
    Integer nodeTypeId;
    Integer manufacturerId;
    String networkElement;
    String partNumber;
    String model;
    String inventoryFlag;
    String serialNumber;
    String description;
    Date manufacturingDate; 
    String issueNumber;
    LocalDateTime insertDate;
    LocalDateTime updateDate;
    String changedBy;
    LocalDateTime changedDate;
    Boolean isMapped;
}