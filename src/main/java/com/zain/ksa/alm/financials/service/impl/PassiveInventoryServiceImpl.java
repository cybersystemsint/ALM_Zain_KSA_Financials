package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

// ─── CRITICAL FIX ────────────────────────────────────────────────────────────
// Must use Spring's @Transactional, NOT javax.transaction.Transactional.
// Only Spring's version has the readOnly attribute.
import org.springframework.transaction.annotation.Transactional;
// ─────────────────────────────────────────────────────────────────────────────

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.dto.response.PassiveInventoryDTO;
import com.zain.ksa.alm.financials.entity.PassiveInventory;
import com.zain.ksa.alm.financials.exception.ResourceNotFoundException;
import com.zain.ksa.alm.financials.mapper.InventoryMapper;
import com.zain.ksa.alm.financials.repository.PassiveInventoryRepository;
import com.zain.ksa.alm.financials.service.PassiveInventoryService;

@Service
public class PassiveInventoryServiceImpl implements PassiveInventoryService {

    private static final Logger log = LoggerFactory.getLogger(PassiveInventoryServiceImpl.class);

    private final PassiveInventoryRepository repository;
    private final InventoryMapper            mapper;

    @PersistenceContext
    private EntityManager entityManager;

    private static final GenericSpecificationBuilder<PassiveInventory> SPEC_BUILDER =
            new GenericSpecificationBuilder<>("recordDateTime");

    public PassiveInventoryServiceImpl(PassiveInventoryRepository repository,
                                       InventoryMapper mapper) {
        this.repository = repository;
        this.mapper     = mapper;
    }

    // ── Legacy methods ────────────────────────────────────────────────────────

    @Override
    public PassiveInventory findBySerialNumber(String serialNumber) {
        log.debug("findBySerialNumber: {}", serialNumber);
        return repository.findBySerialNumber(serialNumber);
    }

    @Override
    public List<PassiveInventory> findAll() {
        return repository.findAll();
    }

    @Override
    public void saveAll(List<PassiveInventory> items) {
        repository.saveAll(items);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PassiveInventory> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    // ── New paginated dynamic filter ──────────────────────────────────────────

    @Override
    @Cacheable(
        value  = "passive-inventory:list",
        key    = "T(String).valueOf(#filter.siteId) + ':' + "
               + "T(String).valueOf(#filter.isMapped) + ':' + "
               + "T(String).valueOf(#filter.columnName) + ':' + "
               + "T(String).valueOf(#filter.searchQuery) + ':' + "
               + "#pageable.pageNumber + ':' + #pageable.pageSize"
    )
    @Transactional(readOnly = true)
    public PagedResponse<PassiveInventoryDTO> findAll(DynamicFilterRequest filter, Pageable pageable) {
        log.debug("findAll passive with filter: columnName={}, searchQuery={}, filterBy={}",
                filter.getColumnName(), filter.getSearchQuery(), filter.getFilterBy());

        return PagedResponse.of(
            repository.findAll(SPEC_BUILDER.build(filter), pageable)
                      .map(mapper::toDto)
        );
    }

    @Override
    @Cacheable(value = "passive-inventory:single", key = "#id")
    @Transactional(readOnly = true)
    public PassiveInventoryDTO findById(Integer id) {
        log.debug("findById passive: {}", id);
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("PassiveInventory", "id", id));
    }
}