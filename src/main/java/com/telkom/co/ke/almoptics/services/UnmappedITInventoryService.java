package com.telkom.co.ke.almoptics.services;


import com.telkom.co.ke.almoptics.common.BatchResult;
import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.entities.ITInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedITInventory;
import com.telkom.co.ke.almoptics.repository.ITInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedITInventoryRepository;
import com.telkom.co.ke.almoptics.specs.InventorySpecs;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Date;

@Service
public class UnmappedITInventoryService {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedITInventoryService.class);

    @Autowired
    private ITInventoryRepository itInventoryRepository;

    @Autowired
    private UnmappedITInventoryRepository unmappedITInventoryRepository;
   @Autowired
    private UnmappedITInventoryBatchService unmappedITInventoryBatchService;

  // Fetch all unmapped IT Inventory
   public Page<UnmappedITInventory> fetchPaged(InventoryRequest req) {
    logger.info(
        "Fetching unmapped IT inventory: page={}, size={}, searchColumn={}, searchQuery={}, filterBy={}",
        req.getPage(), req.getSize(), req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy()
    );

    Pageable pageable = PageRequest.of(req.getPage(), req.getSize());

    try {
        // SPECIAL CASE: rowNumber (now handled with LIMIT/OFFSET)
        if ("rowNumber".equalsIgnoreCase(req.getSearchColumn())) {
            int rowNumber = Integer.parseInt(req.getSearchQuery()); // 1-based index
            int offset = Math.max(0, rowNumber - 1);

            Optional<UnmappedITInventory> recordOpt =
                    unmappedITInventoryRepository.findNthRecord(offset);

            List<UnmappedITInventory> list =
                    recordOpt.map(List::of).orElse(List.of());
            // Set rowNumber for display
            if (!list.isEmpty()) list.get(0).setRowNumber(rowNumber);

            // Make a Page from the single record
            return new PageImpl<>(list, PageRequest.of(0, 1), list.isEmpty() ? 0 : 1);
        }

        // NORMAL PATH
        Page<UnmappedITInventory> page = unmappedITInventoryRepository.findAll(
                InventorySpecs.filterBy(
                        req.getSearchColumn(),
                        req.getSearchQuery(),
                        req.getFilterBy()
                ),
                pageable
        );

        int startRow = req.getPage() * req.getSize() + 1;
        for (int i = 0; i < page.getContent().size(); i++) {
            page.getContent().get(i).setRowNumber(startRow + i);
        }

        return page;

    } catch (Exception e) {
        logger.error("Error fetching unmapped IT inventory", e);
        throw e;
    }
}

    // Scheduled task to process unmapped IT Inventory daily at 5:10 PM
  @Scheduled(cron = "0 00 20 * * ?")
    public void processUnmappedITInventory() {
        logger.info("UnmappedITInventoryService::processUnmappedITInventory - Scheduler started at {}", new Date());
        int page = 0;
        int pageSize = 500;
        Page<ITInventory> itPage;
        BatchResult totalResult = new BatchResult();
        do {
            Pageable pageable = PageRequest.of(page, pageSize);
            itPage = itInventoryRepository.findAll(pageable);

            BatchResult batchResult = unmappedITInventoryBatchService.processBatch(itPage.getContent());
            totalResult.add(batchResult);
            page++;
        } while (itPage.hasNext());
        logger.info("Scheduler finished. Processed: {}, Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
                totalResult.getProcessed(),
                totalResult.getInserted(),
                totalResult.getUpdated(),
                totalResult.getDeleted(),
                totalResult.getFailed());
    }

}
