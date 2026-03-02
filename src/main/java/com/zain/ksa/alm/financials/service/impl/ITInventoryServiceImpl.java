package com.zain.ksa.alm.financials.service.impl;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

// ─── CRITICAL FIX: Spring's Transactional has readOnly; javax's does NOT ─────
import org.springframework.transaction.annotation.Transactional;
// ─────────────────────────────────────────────────────────────────────────────

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.response.ITInventoryDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.ITInventory;
import com.zain.ksa.alm.financials.exception.ResourceNotFoundException;
import com.zain.ksa.alm.financials.mapper.InventoryMapper;
import com.zain.ksa.alm.financials.repository.ITInventoryRepository;
import com.zain.ksa.alm.financials.service.ITInventoryService;

/**
 * IT Inventory service implementation.
 * No export — removed per requirements.
 *
 * FIX: @RequiredArgsConstructor + explicit constructor caused
 * "duplicate constructor" errors. Replaced with single explicit constructor.
 */
@Service
public class ITInventoryServiceImpl implements ITInventoryService {

    private static final Logger log = LoggerFactory.getLogger(ITInventoryServiceImpl.class);

    private final ITInventoryRepository repository;
    private final InventoryMapper        mapper;

    @PersistenceContext
    private EntityManager entityManager;

    private static final GenericSpecificationBuilder<ITInventory> SPEC_BUILDER =
            new GenericSpecificationBuilder<>("recordDateTime");

    public ITInventoryServiceImpl(ITInventoryRepository repository, InventoryMapper mapper) {
        this.repository = repository;
        this.mapper     = mapper;
    }

    @Override
    @Cacheable(
        value  = "it-inventory:list",
        key    = "T(String).valueOf(#filter.siteId) + ':' + "
               + "T(String).valueOf(#filter.columnName) + ':' + "
               + "T(String).valueOf(#filter.searchQuery) + ':' + "
               + "#pageable.pageNumber + ':' + #pageable.pageSize"
    )
    @Transactional(readOnly = true)
    public PagedResponse<ITInventoryDTO> findAll(DynamicFilterRequest filter, Pageable pageable) {
        log.debug("findAll IT inventory with filter: columnName={}, searchQuery={}",
                filter.getColumnName(), filter.getSearchQuery());

        Page<ITInventory> page = repository.findAll(SPEC_BUILDER.build(filter), pageable);
        return PagedResponse.of(page.map(mapper::toDto));
    }

    @Override
    @Cacheable(value = "it-inventory:single", key = "#id")
    @Transactional(readOnly = true)
    public ITInventoryDTO findById(Long id) {
        log.debug("findById IT inventory: {}", id);
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("ITInventory", "id", id));
    }
}