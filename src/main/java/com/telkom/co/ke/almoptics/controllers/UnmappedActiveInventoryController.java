package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;
import com.telkom.co.ke.almoptics.services.UnmappedActiveInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
public class UnmappedActiveInventoryController {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedActiveInventoryController.class);

    @Autowired
    private UnmappedActiveInventoryService unmappedActiveInventoryService;

    @PostMapping("/active-inventories/unmapped")
    public Page<UnmappedActiveInventory> getPaged(@RequestBody InventoryRequest req) {
        logger.info("FETCH /active-inventory/unmapped called, params: page={}, size={}, searchColumn={}, searchQuery={}, filterBy={}",
                req.getPage(), req.getSize(), req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy());
        try {
            Page<UnmappedActiveInventory> result = unmappedActiveInventoryService.fetchPaged(req);
            logger.info("Returning {} records for page {}.", result.getNumberOfElements(), req.getPage());
            return result;
        } catch (Exception e) {
            logger.error("Error in /active-inventory/unmapped endpoint: {}", e.getMessage(), e);
            throw e;
        }
    }


    //Manual trigger for Unmapped Active Inventory processing
    @PostMapping("/active-inventories/unmapped/process")
    public ResponseEntity<String> triggerActiveUnmapped() {
        unmappedActiveInventoryService.processUnmappedNodes();
        return ResponseEntity.ok("Active unmapped scheduler triggered.");
    }

}