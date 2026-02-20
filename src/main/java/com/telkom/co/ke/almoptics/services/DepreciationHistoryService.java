package com.telkom.co.ke.almoptics.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.telkom.co.ke.almoptics.dto.InventoryRequest;
import com.telkom.co.ke.almoptics.entities.DepreciationHistory;
import com.telkom.co.ke.almoptics.repository.DepreciationHistoryRepository;
import com.telkom.co.ke.almoptics.specs.InventorySpecs;

@Service
public class DepreciationHistoryService {
    private static final Logger logger = LoggerFactory.getLogger(DepreciationHistoryService.class);

    @Autowired
    private DepreciationHistoryRepository depreciationHistoryRepository;
    // Fetch paginated & filtered history
    public Page<DepreciationHistory> fetchPaged(InventoryRequest req) {
        logger.info("Fetching paged DepreciationHistory: page={}, size={}, searchColumn={}, searchQuery={}, filterBy={}",
                req.getPage(), req.getSize(), req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy());

        Pageable pageable = PageRequest.of(req.getPage(), req.getSize());
        try {
            Specification<DepreciationHistory> spec = InventorySpecs.filterBy(
                    req.getSearchColumn(), req.getSearchQuery(), req.getFilterBy());
            Page<DepreciationHistory> page = depreciationHistoryRepository.findAll(spec, pageable);

 

            logger.info("Fetched {} records for DepreciationHistory, page {}.", page.getNumberOfElements(), req.getPage());
            return page;
        } catch (Exception e) {
            logger.error("Error while fetching DepreciationHistory: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    public void saveAll(List<DepreciationHistory> histories) {
        depreciationHistoryRepository.saveAll(histories);
    }

    public void deleteByMonthYear(int month, int year) {
        depreciationHistoryRepository.deleteByMonthYear(month, year);
    }

     public boolean existsForMonth(int month, int year) {
        return depreciationHistoryRepository.existsForMonth(month, year);
    }
}