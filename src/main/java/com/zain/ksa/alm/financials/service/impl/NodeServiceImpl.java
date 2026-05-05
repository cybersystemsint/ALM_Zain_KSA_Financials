package com.zain.ksa.alm.financials.service.impl;

import java.util.List;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletResponse;

// ─── CRITICAL FIX ────────────────────────────────────────────────────────────
// Use org.springframework.transaction.annotation.Transactional (NOT javax.transaction.Transactional)
// javax.transaction.Transactional does NOT have the readOnly attribute → causes
// "The attribute readOnly is undefined for the annotation type Transactional"
import org.springframework.transaction.annotation.Transactional;
// ─────────────────────────────────────────────────────────────────────────────

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.response.NodeDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.Node;
import com.zain.ksa.alm.financials.exception.ResourceNotFoundException;
import com.zain.ksa.alm.financials.mapper.InventoryMapper;
import com.zain.ksa.alm.financials.repository.NodeRepository;
import com.zain.ksa.alm.financials.service.NodeService;

@Service
public class NodeServiceImpl implements NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeServiceImpl.class);

    private final NodeRepository  repository;
    private final InventoryMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    // GenericSpecificationBuilder uses "recordDateTime" for date-range filters
    private static final GenericSpecificationBuilder<Node> SPEC_BUILDER =
            new GenericSpecificationBuilder<>("recordDateTime");

    public NodeServiceImpl(NodeRepository repository, InventoryMapper mapper) {
        this.repository = repository;
        this.mapper     = mapper;
    }

    // ── Legacy methods ────────────────────────────────────────────────────────

    @Override
    public Node findBySerialNumber(String serialNumber) {
        log.debug("findBySerialNumber: {}", serialNumber);
        return repository.findBySerialNumber(serialNumber);
    }

    @Override
    public List<Node> findAll() {
        return repository.findAll();
    }

    @Override
    public void saveAll(List<Node> nodes) {
        repository.saveAll(nodes);
    }

    @Override
    public List<Node> findByNodeName(String nodeName) {
        return repository.findByNodeName(nodeName);
    }

    @Override
    public Node save(Node node) {
        return repository.save(node);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Node> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    // ── New paginated dynamic filter ──────────────────────────────────────────

    @Override
    @Cacheable(
        value  = "active-inventory:list",
        key    = "T(String).valueOf(#filter.siteId) + ':' + "
               + "T(String).valueOf(#filter.isMapped) + ':' + "
               + "T(String).valueOf(#filter.columnName) + ':' + "
               + "T(String).valueOf(#filter.searchQuery) + ':' + "
               + "#pageable.pageNumber + ':' + #pageable.pageSize"
    )
    @Transactional(readOnly = true)
    public PagedResponse<NodeDTO> findAll(DynamicFilterRequest filter, Pageable pageable) {
        log.debug("findAll nodes with filter: columnName={}, searchQuery={}, filterBy={}",
                filter.getColumnName(), filter.getSearchQuery(), filter.getFilterBy());

        return PagedResponse.of(
            repository.findAll(SPEC_BUILDER.build(filter), pageable)
                      .map(mapper::toDto)
        );
    }

    @Override
    @Cacheable(value = "active-inventory:single", key = "#id")
    @Transactional(readOnly = true)
    public NodeDTO findById(Integer id) {
        log.debug("findById node: {}", id);
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Node", "id", id));
    }
}