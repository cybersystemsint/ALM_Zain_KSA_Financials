package com.telkom.co.ke.almoptics.services;


import com.telkom.co.ke.almoptics.common.BatchResult;
import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.entities.UnmappedPassiveInventory;
import com.telkom.co.ke.almoptics.entities.tbPassiveInventory;
import com.telkom.co.ke.almoptics.repository.*;
import com.telkom.co.ke.almoptics.specs.InventorySpecs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class UnmappedPassiveInventoryService {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedPassiveInventoryService.class);

    @Autowired
    private PassiveInventoryRepository passiveInventoryRepo;
    @Autowired
    private FarReportRepository farReportRepo;
    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepo;
    @Autowired
    private UnmappedPassiveInventoryBatchService unmappedPassiveInventoryBatchService;


    //Fetch all unmapped Passive Inventory
    public Page<UnmappedPassiveInventory> fetchPaged(InventoryRequest req) {
    logger.info("Fetching paged unmapped PASSIVE inventory: page={}, size={}, searchColumn={}, searchQuery={}, filterBy={}",
            req.getPage(), req.getSize(), req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy());

    Pageable pageable = PageRequest.of(req.getPage(), req.getSize());

    try {
        // SPECIAL CASE: rowNumber
        if ("rowNumber".equalsIgnoreCase(req.getSearchColumn())) {
            int rowNumber = Integer.parseInt(req.getSearchQuery()); // 1-based index
            int offset = Math.max(0, rowNumber - 1);

            Optional<UnmappedPassiveInventory> recordOpt =
                    unmappedPassiveInventoryRepo.findNthRecord(offset);

            List<UnmappedPassiveInventory> list =
                    recordOpt.map(List::of).orElse(List.of());
            // Set rowNumber for display
            if (!list.isEmpty()) list.get(0).setRowNumber(rowNumber);

            // Make a Page from the single record
            return new PageImpl<>(list, PageRequest.of(0, 1), list.isEmpty() ? 0 : 1);
        }

        // NORMAL PATH
        Page<UnmappedPassiveInventory> page = unmappedPassiveInventoryRepo.findAll(
                InventorySpecs.filterBy(req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy()),
                pageable
        );

        int startRow = req.getPage() * req.getSize() + 1;
        for (int i = 0; i < page.getContent().size(); i++) {
            page.getContent().get(i).setRowNumber(startRow + i);
        }

        logger.info("Fetched {} records for unmapped PASSIVE inventory, page {}.", page.getNumberOfElements(), req.getPage());
        return page;
    } catch (Exception e) {
        logger.error("Error while fetching unmapped PASSIVE inventory: {}", e.getMessage(), e);
        throw e;
    }
}
   
    //Unmapped Passive Inventory Scheduler - runs at 00:00pm every day
    @Scheduled(cron = "0 00 00 * * ?")
    public void processUnmappedPassiveInventory() {
        logger.info("Starting scheduler for unmapped passive inventory at {}", new Date());

        int page = 0;
        int pageSize = 500;
        List<tbPassiveInventory> list;
        BatchResult totalResult = new BatchResult();

        do {
            Pageable pageable = PageRequest.of(page, pageSize);
            list = passiveInventoryRepo.findByInventoryType(4, pageable);
            BatchResult batchResult = unmappedPassiveInventoryBatchService.processBatch(list);
            totalResult.add(batchResult);
            page++;
        } while (!list.isEmpty());

        logger.info("Passive scheduler finished. Processed: {}, Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
            totalResult.getProcessed(), totalResult.getInserted(), totalResult.getUpdated(),
            totalResult.getDeleted(), totalResult.getFailed());
    }

}