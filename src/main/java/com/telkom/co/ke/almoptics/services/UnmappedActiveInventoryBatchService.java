package com.telkom.co.ke.almoptics.services;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.telkom.co.ke.almoptics.common.BatchResult;
import com.telkom.co.ke.almoptics.entities.Manufacturer;
import com.telkom.co.ke.almoptics.entities.Site;
import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;
import com.telkom.co.ke.almoptics.entities.tbNode;
import com.telkom.co.ke.almoptics.entities.tbNodeType;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;
import com.telkom.co.ke.almoptics.repository.ManufacturerRepository;
import com.telkom.co.ke.almoptics.repository.NodeTypeRepo;
import com.telkom.co.ke.almoptics.repository.SiteRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedActiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.tbNodeRepository;


@Service
public class UnmappedActiveInventoryBatchService {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedActiveInventoryBatchService.class);


    @Autowired
    private tbNodeRepository tbNodeRepo;
    @Autowired
    private NodeTypeRepo tbNodeTypeRepo;
    @Autowired
    private ManufacturerRepository tbManufacturerRepo;
    @Autowired
    private SiteRepository tbSiteRepo;
    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveInventoryRepo;
    @Autowired
    private FarReportRepository farReportRepo;

     @PersistenceContext
    private EntityManager entityManager;

    @Transactional
public BatchResult processBatch(List<tbNode> nodes) {
    BatchResult result = new BatchResult();

    List<UnmappedActiveInventory> toInsert = new ArrayList<>(nodes.size());
    List<UnmappedActiveInventory> toUpdate = new ArrayList<>(nodes.size());
    List<UnmappedActiveInventory> toDelete = new ArrayList<>(nodes.size());
    List<tbNode> tbNodesToUpdate = new ArrayList<>();
     Set<Integer> updatedNodeIds = new HashSet<>();


    // Track tbNode changed count
    int tbNodeChangedCount = 0;

    List<String> serialNumbers = nodes.stream()
            .map(tbNode::getSerialNumber)
            .filter(s -> s != null && !s.isBlank())
            .toList();

    Set<String> farSerials = farReportRepo.findAllSerialNumbersBySerialNumberIn(serialNumbers);
    List<UnmappedActiveInventory> unmappedList = unmappedActiveInventoryRepo.findBySerialNumberIn(serialNumbers);
    Map<String, UnmappedActiveInventory> unmappedMap = unmappedList.stream()
            .collect(Collectors.toMap(UnmappedActiveInventory::getSerialNumber, x -> x));

    for (tbNode node : nodes) {
        result.incProcessed();

        try {
            String serialNumber = node.getSerialNumber();
            if (serialNumber == null || serialNumber.isBlank()) {
                logger.warn("Node id {} has empty serial number, skipping.", node.getId());
                result.incFailed();
                continue;
            }

            boolean existsInFAR = farSerials.contains(serialNumber);
            UnmappedActiveInventory unmappedOpt = unmappedMap.get(serialNumber);

            if (!existsInFAR) {
                    if (node.getIsMapped() == null || !Boolean.FALSE.equals(node.getIsMapped())) {
                    node.setIsMapped(false);
                   if (updatedNodeIds.add(node.getId())) {tbNodesToUpdate.add(node);} // immediately update mapping status
                    tbNodeChangedCount++; // increment change count
                    logger.info("Updated isMapped=false for tbNode id {} serial {}", node.getId(), node.getSerialNumber());
                }


                UnmappedActiveInventory incoming = assembleUnmappedRecord(node);

                if (unmappedOpt == null) {
                    toInsert.add(incoming);
                    result.incInserted();
                } else {
                    if (needsUpdate(unmappedOpt, incoming)) {
                        copyUnmappedDetails(unmappedOpt, incoming);
                        toUpdate.add(unmappedOpt);
                        result.incUpdated();
                    }
                }
            } else {
                    if (node.getIsMapped() == null || !Boolean.TRUE.equals(node.getIsMapped())) {
                    node.setIsMapped(true);
                   if (updatedNodeIds.add(node.getId())) {tbNodesToUpdate.add(node);} // immediately update mapping status
                    tbNodeChangedCount++; // increment change count
                    logger.info("Marked isMapped=true for tbNode id {} serial {}", node.getId(), node.getSerialNumber());
                }

                if (unmappedOpt != null) {
                    toDelete.add(unmappedOpt);
                    result.incDeleted();
                }
            }

        } catch (Exception e) {
            result.incFailed();
            logger.error("Error processing node id {}: {}", node.getId(), e.getMessage(), e);
        }
    }

    if (!toInsert.isEmpty()) unmappedActiveInventoryRepo.saveAll(toInsert);
    if (!toUpdate.isEmpty()) unmappedActiveInventoryRepo.saveAll(toUpdate);
    if (!toDelete.isEmpty()) unmappedActiveInventoryRepo.deleteAll(toDelete);
    if (!tbNodesToUpdate.isEmpty()) {tbNodeRepo.saveAll(tbNodesToUpdate);}

    entityManager.flush();
    entityManager.clear();

    // ---- BATCH SUMMARY LOGS ----
    logger.info("Batch summary: {} UnmappedActiveInventory inserted, {} updated, {} deleted.",
            toInsert.size(), toUpdate.size(), toDelete.size());
    logger.info("Batch summary: {} tbNode records had isMapped changed.", tbNodeChangedCount);

    return result;
}
    // Create a UnmappedActiveInventory from tbNode
    private UnmappedActiveInventory assembleUnmappedRecord(tbNode node) {
        UnmappedActiveInventory unmapped = new UnmappedActiveInventory();
        unmapped.setRecordDateTime(new Timestamp(System.currentTimeMillis()));
        unmapped.setNodeId(String.valueOf(node.getId()));
        unmapped.setNodeName(node.getNode());

        // Node type
        String nodeTypeName = null;
        if (node.getNodeTypeId() != null) {
            tbNodeType nodeType = tbNodeTypeRepo.findById(node.getNodeTypeId());
            nodeTypeName = (nodeType != null) ? nodeType.getNodeType() : null;
        }
        unmapped.setNodeType(nodeTypeName);

        // Manufacturer
        String manufactName = null;
        if (node.getManufacturerId() != null) {
            Optional<Manufacturer> manuOpt = tbManufacturerRepo.findById(node.getManufacturerId());
            manufactName = manuOpt.map(Manufacturer::getManufacturerName).orElse(null);
        }
        unmapped.setManufacturer(manufactName);

        unmapped.setSerialNumber(node.getSerialNumber());
        unmapped.setModel(node.getModel());
        unmapped.setPartNumber(node.getPartNumber());

        // Site
        String siteIdRef = null;
        if (node.getSiteId() != null) {
            Site site = tbSiteRepo.findByRecordNo(node.getSiteId());
            siteIdRef = (site != null) ? site.getSiteId() : null;
        }
        unmapped.setSiteId(siteIdRef);

        unmapped.setDescription(node.getDescription());
        unmapped.setManufacturingDate(node.getManufacturingDate());
        unmapped.setAssetInsertionDate(node.getInsertDate());
        // unmapped.setInstallationDate(node.getInstallationDate());
        // unmapped.setWarranty(node.getWarranty());

        return unmapped;
    }

    // Compare record fields to see if update is needed
    private boolean needsUpdate(UnmappedActiveInventory a, UnmappedActiveInventory b) {
        return notEqual(a.getNodeId(), b.getNodeId()) ||
               notEqual(a.getNodeName(), b.getNodeName()) ||
               notEqual(a.getNodeType(), b.getNodeType()) ||
               notEqual(a.getManufacturer(), b.getManufacturer()) ||
               notEqual(a.getModel(), b.getModel()) ||
               notEqual(a.getPartNumber(), b.getPartNumber()) ||
               notEqual(a.getSiteId(), b.getSiteId()) ||
               notEqual(a.getDescription(), b.getDescription()) ||
               notEqual(a.getManufacturingDate(), b.getManufacturingDate()) ||
               notEqual(a.getAssetInsertionDate(), b.getAssetInsertionDate());

               // Add further fields if desired
    }

    private void copyUnmappedDetails(UnmappedActiveInventory target, UnmappedActiveInventory source) {
        target.setRecordDateTime(source.getRecordDateTime());
        target.setNodeId(source.getNodeId());
        target.setNodeName(source.getNodeName());
        target.setNodeType(source.getNodeType());
        target.setManufacturer(source.getManufacturer());
        target.setModel(source.getModel());
        target.setPartNumber(source.getPartNumber());
        target.setSiteId(source.getSiteId());
        target.setDescription(source.getDescription());
        target.setManufacturingDate(source.getManufacturingDate());
        target.setAssetInsertionDate(source.getAssetInsertionDate());
        // Add further fields if desired
    }

    private boolean notEqual(Object a, Object b) {
        return (a == null && b != null) ||
               (a != null && b == null) ||
               (a != null && !a.equals(b));
    }
}
