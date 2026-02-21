package com.telkom.co.ke.almoptics.services;


import java.util.*;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.telkom.co.ke.almoptics.entities.tbPassiveInventory;
import com.telkom.co.ke.almoptics.entities.InventoryType;
import com.telkom.co.ke.almoptics.entities.UnmappedPassiveInventory;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;
import com.telkom.co.ke.almoptics.repository.PassiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedPassiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.InventoryTypeRepository;
import com.telkom.co.ke.almoptics.common.BatchResult;

@Service
public class UnmappedPassiveInventoryBatchService {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedPassiveInventoryBatchService.class);

    @Autowired
    private PassiveInventoryRepository passiveInventoryRepo;
    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepo;
    @Autowired
    private FarReportRepository farReportRepo;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
   private InventoryTypeRepository inventoryTypeRepository;

    @Transactional
    public BatchResult processBatch(List<tbPassiveInventory> nodes) {
        BatchResult result = new BatchResult();

        List<UnmappedPassiveInventory> toInsert = new ArrayList<>(nodes.size());
        List<UnmappedPassiveInventory> toUpdate = new ArrayList<>(nodes.size());
        List<UnmappedPassiveInventory> toDelete = new ArrayList<>(nodes.size());
        List<tbPassiveInventory> tbNodesToUpdate = new ArrayList<>();
        Set<Integer> updatedNodeIds = new HashSet<>();
        int tbNodeChangedCount = 0;

        List<String> serialNumbers = nodes.stream()
            .map(tbPassiveInventory::getSerialNumber)
            .filter(s -> s != null && !s.isBlank())
            .toList();

        Set<String> farSerials = farReportRepo.findAllSerialNumbersBySerialNumberIn(serialNumbers);
        List<UnmappedPassiveInventory> unmappedList = unmappedPassiveInventoryRepo.findBySerialNumberIn(serialNumbers);

          Map<String, UnmappedPassiveInventory> unmappedMap =
    unmappedList.stream()
        .collect(Collectors.toMap(
            UnmappedPassiveInventory::getSerialNumber,
            x -> x,
            (existing, replacement) -> {
                logger.warn(
                    "Duplicate UnmappedPassiveInventory found for serialNumber={}. Keeping latest.",
                    existing.getSerialNumber()
                );
                return existing.getRecordDateTime().after(replacement.getRecordDateTime())
                        ? existing
                        : replacement;
            }
        ));


        for (tbPassiveInventory node : nodes) {
            result.incProcessed();
            try {
                String serialNumber = node.getSerialNumber();
                if (serialNumber == null || serialNumber.isBlank()) {
                    logger.warn("PassiveNode id {} has empty serial number, skipping.", node.getRecordNo());
                    result.incFailed();
                    continue;
                }
                boolean existsInFAR = farSerials.contains(serialNumber);
                UnmappedPassiveInventory unmappedOpt = unmappedMap.get(serialNumber);

                if (!existsInFAR) {
                    if (node.getIsMapped() == null || !Boolean.FALSE.equals(node.getIsMapped())) {
                        node.setIsMapped(false);
                        if (updatedNodeIds.add(node.getRecordNo())) tbNodesToUpdate.add(node);
                        tbNodeChangedCount++;
                        logger.info("Set isMapped=false for tbPassiveInventory id {} serial {}", node.getRecordNo(), node.getSerialNumber());
                    }
                    UnmappedPassiveInventory incoming = assembleUnmappedPassiveRecord(node);
                    if (unmappedOpt == null) {
                        toInsert.add(incoming);
                        result.incInserted();
                    } else if (needsUpdate(unmappedOpt, incoming)) {
                        copyUnmappedPassiveDetails(unmappedOpt, incoming);
                        toUpdate.add(unmappedOpt);
                        result.incUpdated();
                    }
                } else {
                    if (node.getIsMapped() == null || !Boolean.TRUE.equals(node.getIsMapped())) {
                        node.setIsMapped(true);
                        if (updatedNodeIds.add(node.getRecordNo())) tbNodesToUpdate.add(node);
                        tbNodeChangedCount++;
                        logger.info("Set isMapped=true for tbPassiveInventory id {} serial {}", node.getRecordNo(), node.getSerialNumber());
                    }
                    if (unmappedOpt != null) {
                        toDelete.add(unmappedOpt);
                        result.incDeleted();
                    }
                }
            } catch (Exception e) {
                result.incFailed();
                logger.error("Error processing passive inventory {}: {}", node.getRecordNo(), e.getMessage(), e);
            }
        }

        if (!toInsert.isEmpty()) unmappedPassiveInventoryRepo.saveAll(toInsert);
        if (!toUpdate.isEmpty()) unmappedPassiveInventoryRepo.saveAll(toUpdate);
        if (!toDelete.isEmpty()) unmappedPassiveInventoryRepo.deleteAll(toDelete);
        if (!tbNodesToUpdate.isEmpty()) passiveInventoryRepo.saveAll(tbNodesToUpdate);

        entityManager.flush();
        entityManager.clear();

        logger.info("Batch summary: {} UnmappedPassiveInventory inserted, {} updated, {} deleted.", toInsert.size(), toUpdate.size(), toDelete.size());
        logger.info("Batch summary: {} tbPassiveInventory records had isMapped changed.", tbNodeChangedCount);

        return result;
    }

    private UnmappedPassiveInventory assembleUnmappedPassiveRecord(tbPassiveInventory passive) {
        UnmappedPassiveInventory unmapped = new UnmappedPassiveInventory();
        unmapped.setRecordDateTime(new Date());
        unmapped.setInventoryId(String.valueOf(passive.getRecordNo()));
        unmapped.setInventoryTypeId(passive.getInventoryType());
        InventoryType type = inventoryTypeRepository.findByRecordNo(passive.getInventoryType());
        unmapped.setInventoryType(type != null ? type.getInventoryTypeName() : null);
        unmapped.setSerialNumber(passive.getSerialNumber());
        unmapped.setSiteId(passive.getSiteId());
        unmapped.setObjectId(passive.getObjectId());
        unmapped.setParentName(passive.getParentName());
        unmapped.setEntryDate(passive.getEntryDate());
        unmapped.setEntryUser(passive.getEntryUser());
        unmapped.setModel(passive.getModel());
        unmapped.setNote(passive.getNote());
        unmapped.setPart(passive.getPart());
        unmapped.setItemBarCode(passive.getItemBarCode());
        unmapped.setItemStatus(passive.getItemStatus());
        unmapped.setCategoryInNEP(passive.getCategoryInNEP());
        unmapped.setScrapStatus(passive.getScrapStatus());
        unmapped.setLocationSubType(passive.getLocationSubType());
        unmapped.setLocationClassification(passive.getLocationClassification());
        unmapped.setItemClassification(passive.getItemClassification());
        unmapped.setItemClassification2(passive.getItemClassification2());
        unmapped.setPrPoNo(passive.getPRPONo());
        unmapped.setNotes(passive.getNote());
        return unmapped;
    }

    private boolean needsUpdate(UnmappedPassiveInventory a, UnmappedPassiveInventory b) {
        return notEqual(a.getSiteId(), b.getSiteId()) ||
                notEqual(a.getObjectId(), b.getObjectId()) ||
                notEqual(a.getParentName(), b.getParentName()) ||
                notEqual(a.getEntryDate(), b.getEntryDate()) ||
                notEqual(a.getEntryUser(), b.getEntryUser()) ||
                notEqual(a.getModel(), b.getModel()) ||
                notEqual(a.getNote(), b.getNote()) ||
                notEqual(a.getPart(), b.getPart()) ||
                notEqual(a.getItemBarCode(), b.getItemBarCode()) ||
                notEqual(a.getItemStatus(), b.getItemStatus()) ||
                notEqual(a.getCategoryInNEP(), b.getCategoryInNEP()) ||
                notEqual(a.getScrapStatus(), b.getScrapStatus()) ||
                !Objects.equals(a.getInventoryTypeId(), b.getInventoryTypeId()) ||
               !Objects.equals(a.getInventoryType(), b.getInventoryType()) ||
                notEqual(a.getLocationSubType(), b.getLocationSubType()) ||
                notEqual(a.getLocationClassification(), b.getLocationClassification()) ||
                notEqual(a.getItemClassification(), b.getItemClassification()) ||
                notEqual(a.getItemClassification2(), b.getItemClassification2()) ||
                notEqual(a.getPrPoNo(), b.getPrPoNo()) ||
                notEqual(a.getNotes(), b.getNotes());
    }

    private void copyUnmappedPassiveDetails(UnmappedPassiveInventory target, UnmappedPassiveInventory source) {
        target.setRecordDateTime(source.getRecordDateTime());
        target.setSiteId(source.getSiteId());
        target.setObjectId(source.getObjectId());
        target.setParentName(source.getParentName());
        target.setEntryDate(source.getEntryDate());
        target.setEntryUser(source.getEntryUser());
        target.setModel(source.getModel());
        target.setNote(source.getNote());
        target.setPart(source.getPart());
        target.setItemBarCode(source.getItemBarCode());
        target.setItemStatus(source.getItemStatus());
        target.setCategoryInNEP(source.getCategoryInNEP());
        target.setScrapStatus(source.getScrapStatus());
        target.setInventoryType(source.getInventoryType());
        target.setLocationSubType(source.getLocationSubType());
        target.setLocationClassification(source.getLocationClassification());
        target.setItemClassification(source.getItemClassification());
        target.setItemClassification2(source.getItemClassification2());
        target.setPrPoNo(source.getPrPoNo());
        target.setNotes(source.getNotes());
    }

    private boolean notEqual(Object a, Object b) {
        return (a == null && b != null) ||
            (a != null && b == null) ||
            (a != null && !a.equals(b));
    }
}