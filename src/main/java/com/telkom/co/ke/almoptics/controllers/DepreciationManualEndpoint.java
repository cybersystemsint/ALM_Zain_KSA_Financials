package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.DepreciationScheduler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/depreciation")
public class DepreciationManualEndpoint {

    private static final Logger LOGGER = LogManager.getLogger(DepreciationManualEndpoint.class);

    @Autowired
    private DepreciationScheduler depreciationScheduler;

    @PostMapping("/run")
    public ResponseEntity<String> runDepreciationManually() {
        try {
            LOGGER.info("Manual depreciation process triggered via API.");
            depreciationScheduler.processDepreciation();
            return ResponseEntity.ok("Depreciation process completed successfully.");
        } catch (Exception e) {
            LOGGER.error("Error during manual depreciation run: ", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Depreciation process failed: " + e.getMessage());
        }
    }
}