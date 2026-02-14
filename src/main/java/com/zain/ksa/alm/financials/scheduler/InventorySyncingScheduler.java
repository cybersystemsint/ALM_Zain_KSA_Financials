package com.zain.ksa.alm.financials.scheduler;

import com.zain.ksa.alm.financials.entity.FarReport;
import com.zain.ksa.alm.financials.entity.Node;
import com.zain.ksa.alm.financials.entity.NodeType;
import com.zain.ksa.alm.financials.service.FarReportService;
import com.zain.ksa.alm.financials.service.NodeService;
import com.zain.ksa.alm.financials.service.NodeTypeService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Component
public class InventorySyncingScheduler {

    private static final Logger LOGGER = LogManager.getLogger(InventorySyncingScheduler.class);
    private static final int PAGE_SIZE = 3000;
    private static final int DECOMMISSION_THRESHOLD_DAYS = 21;

    private final FarReportService farReportService;
    private final NodeService nodeService;
    private final NodeTypeService nodeTypeService;

    public InventorySyncingScheduler(FarReportService farReportService,
                                      NodeService nodeService,
                                      NodeTypeService nodeTypeService) {
        this.farReportService = farReportService;
        this.nodeService = nodeService;
        this.nodeTypeService = nodeTypeService;
    }

    //@Scheduled(cron = "0 0 20 * * *", zone = "Africa/Nairobi")
    public void processSynching() {
        try {
            LOGGER.info("Inventory decommission sync scheduler started");
            int pageNumber = 0;
            Page<FarReport> page;
            do {
                Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE);
                page = farReportService.findAll(pageable);
                processRecords(page.getContent());
                pageNumber++;
            } while (page.hasNext());
            LOGGER.info("Inventory decommission sync scheduler completed");
        } catch (Exception ex) {
            LOGGER.error("Exception occurred during inventory sync: {}", ex.getMessage(), ex);
        }
    }

    private void processRecords(List<FarReport> assets) {
        LocalDate decommissionThreshold = LocalDate.now().minusDays(DECOMMISSION_THRESHOLD_DAYS);

        assets.forEach(asset -> {
            try {
                String serialNumber = asset.getSerialNumber();
                if (serialNumber == null || serialNumber.isEmpty()) {
                    return;
                }

                Node node = nodeService.findBySerialNumber(serialNumber);
                if (node != null && node.getSerialNumber() != null && !node.getSerialNumber().isEmpty()) {
                    processNodeDecommission(asset, node, decommissionThreshold);
                }
            } catch (Exception ex) {
                LOGGER.error("Error processing asset: {}", ex.getMessage(), ex);
            }
        });
    }

    private void processNodeDecommission(FarReport asset, Node node, LocalDate decommissionThreshold) {
        Date lastChangedDate = node.getChangedDate();
        if (lastChangedDate == null) {
            return;
        }

        LocalDate lastUpdatedDate = lastChangedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        NodeType nodeType = nodeTypeService.findById(node.getNodeTypeId());
        asset.setNodeType(nodeType.getNodeType());

        if (lastUpdatedDate.isBefore(decommissionThreshold)) {
            asset.setStatusFlag("Decommissioned");
            asset.setChangedDate(new Date());
            asset.setChangedBy("System");
            LOGGER.info("Asset decommissioned - Serial: {}", asset.getSerialNumber());
        }

        farReportService.save(asset);
    }
}
