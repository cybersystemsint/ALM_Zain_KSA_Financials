package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.entities.UnmappedITInventory;
import com.telkom.co.ke.almoptics.services.UnmappedITInventoryService;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
public class UnmappedITInventoryController {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedITInventoryController.class);

    @Autowired
    private UnmappedITInventoryService unmappedITInventoryService;

     @PostMapping("/it-inventories/unmapped")
    public Page<UnmappedITInventory> getPaged(@RequestBody InventoryRequest req) {
        logger.info("FETCH /it-inventories/unmapped called, params: page={}, size={}, searchColumn={}, searchQuery={}, filterBy={}",
                req.getPage(), req.getSize(), req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy());
        try {
            Page<UnmappedITInventory> result = unmappedITInventoryService.fetchPaged(req);
            logger.info("Returning {} records for page {}.", result.getNumberOfElements(), req.getPage());
            return result;
        } catch (Exception e) {
            logger.error("Error in /it-inventories/unmapped endpoint: {}", e.getMessage(), e);
            throw e;
        }
    }

    //Manual trigger for Unmapped IT Inventory processing
        @PostMapping("/it-inventories/unmapped/process")
    public ResponseEntity<String> triggerITUnmapped() {
        unmappedITInventoryService.processUnmappedITInventory();
        return ResponseEntity.ok("IT unmapped scheduler triggered.");
    }
}
