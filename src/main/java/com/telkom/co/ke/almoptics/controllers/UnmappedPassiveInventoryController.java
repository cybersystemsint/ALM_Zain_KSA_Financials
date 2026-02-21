package com.telkom.co.ke.almoptics.controllers;


import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.entities.UnmappedPassiveInventory;
import com.telkom.co.ke.almoptics.services.UnmappedPassiveInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
public class UnmappedPassiveInventoryController {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedPassiveInventoryController.class);

    @Autowired
    private UnmappedPassiveInventoryService unmappedPassiveInventoryService;

    @PostMapping("/passive-inventories/unmapped")
    public Page<UnmappedPassiveInventory> getPaged(@RequestBody InventoryRequest req) {
        logger.info("FETCH /passive-inventory/unmapped called, params: page={}, size={}, searchColumn={}, searchQuery={}, filterBy={}",
                req.getPage(), req.getSize(), req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy());
        try {
            Page<UnmappedPassiveInventory> result = unmappedPassiveInventoryService.fetchPaged(req);
            logger.info("Returning {} records for page {}.", result.getNumberOfElements(), req.getPage());
            return result;
        } catch (Exception e) {
            logger.error("Error in /passive-inventory/unmapped endpoint: {}", e.getMessage(), e);
            throw e;
        }
    }

    //Manual trigger for Unmapped Passive Inventory processing
        @PostMapping("/passive-inventories/unmapped/process")
    public ResponseEntity<String> triggerPassiveUnmapped() {
        unmappedPassiveInventoryService.processUnmappedPassiveInventory();
        return ResponseEntity.ok("Passive unmapped scheduler triggered.");
    }

}
