package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.entities.*;
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

import com.telkom.co.ke.almoptics.common.BatchResult;

@Service
public class UnmappedActiveInventoryService {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedActiveInventoryService.class);


    @Autowired
    private tbNodeRepository tbNodeRepo;

    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveInventoryRepo;
    @Autowired
    private UnmappedActiveInventoryBatchService batchService;




   // Fetch all unmapped Active Inventory

public Page<UnmappedActiveInventory> fetchPaged(InventoryRequest req) {
    logger.info(
        "Fetching unmapped ACTIVE inventory: page={}, size={}, searchColumn={}, searchQuery={}, filterBy={}",
        req.getPage(), req.getSize(), req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy()
    );

    Pageable pageable = PageRequest.of(req.getPage(), req.getSize());

    try {
        // SPECIAL CASE: rowNumber (now handled with LIMIT/OFFSET)
        if ("rowNumber".equalsIgnoreCase(req.getSearchColumn())) {
            int rowNumber = Integer.parseInt(req.getSearchQuery()); // 1-based index
            // Convert to 0-based offset
            int offset = Math.max(0, rowNumber - 1);

            Optional<UnmappedActiveInventory> recordOpt =
                    unmappedActiveInventoryRepo.findNthRecord(offset);

            List<UnmappedActiveInventory> list =
                    recordOpt.map(List::of).orElse(List.of());
            // Set rowNumber for display
            if (!list.isEmpty()) list.get(0).setRowNumber(rowNumber);

            // Make a Page from the single record
            return new PageImpl<>(list, PageRequest.of(0, 1), list.isEmpty() ? 0 : 1);
        }

        // NORMAL PATH: all real DB columns
        Page<UnmappedActiveInventory> page = unmappedActiveInventoryRepo.findAll(
                InventorySpecs.filterBy(
                        req.getSearchColumn(),
                        req.getSearchQuery(),
                        req.getFilterBy()
                ),
                pageable
        );

        // Compute display row number
        int startRow = req.getPage() * req.getSize() + 1;
        for (int i = 0; i < page.getContent().size(); i++) {
            page.getContent().get(i).setRowNumber(startRow + i);
        }

        return page;

    } catch (Exception e) {
        logger.error("Error fetching unmapped ACTIVE inventory", e);
        throw e;
    }
}


   //Unmapped Active Inventory Scheduler - runs at 20:00pm every day
    @Scheduled(cron = "0 00 22 * * ?")
    public void processUnmappedNodes() {

    logger.info("UnmappedActiveInventoryService::processUnmappedNodes - Scheduler started at {}", new Date());

    int page = 0;
    int pageSize = 500;
    Page<tbNode> nodePage;
    BatchResult totalResult = new BatchResult();
    do {
        PageRequest pageable = PageRequest.of(page, pageSize);
        nodePage = tbNodeRepo.findAll(pageable);

        BatchResult batchResult = batchService.processBatch(nodePage.getContent());
        totalResult.add(batchResult);
        page++;
    } while (nodePage.hasNext());
    logger.info(
        "Scheduler finished. Processed: {}, Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
        totalResult.getProcessed(),
        totalResult.getInserted(),
        totalResult.getUpdated(),
        totalResult.getDeleted(),
        totalResult.getFailed()
    );
}



}