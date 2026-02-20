package com.telkom.co.ke.almoptics.services;


import com.telkom.co.ke.almoptics.entities.ITInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedITInventory;
import com.telkom.co.ke.almoptics.repository.ITInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedITInventoryRepository;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;
import com.telkom.co.ke.almoptics.common.BatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.Transactional;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UnmappedITInventoryBatchService {
    private static final Logger logger = LoggerFactory.getLogger(UnmappedITInventoryBatchService.class);

    @Autowired
    private ITInventoryRepository itInventoryRepository;
    @Autowired
    private UnmappedITInventoryRepository unmappedITInventoryRepository;
    @Autowired
    private FarReportRepository farReportRepository;
    @PersistenceContext
    private EntityManager entityManager;

       @Transactional
    public BatchResult processBatch(List<ITInventory> items) {
        BatchResult result = new BatchResult();

        List<UnmappedITInventory> toInsert = new ArrayList<>(items.size());
        List<UnmappedITInventory> toUpdate = new ArrayList<>(items.size());
        List<UnmappedITInventory> toDelete = new ArrayList<>(items.size());

        List<String> serialNumbers = items.stream()
                .map(ITInventory::getHostSerialNumber)
                .filter(s -> s != null && !s.isBlank())
                .toList();

        Set<String> farSerials = farReportRepository.findAllSerialNumbersBySerialNumberIn(serialNumbers);
        List<UnmappedITInventory> unmappedList = unmappedITInventoryRepository.findByHostSerialNumberIn(serialNumbers);

        Map<String, UnmappedITInventory> unmappedMap = unmappedList.stream()
        .collect(Collectors.toMap(
            UnmappedITInventory::getHostSerialNumber,
            x -> x,
            (existing, replacement) -> replacement // keep the last one
        ));


        for (ITInventory itInv : items) {
            result.incProcessed();

            try {
                String serialNumber = itInv.getHostSerialNumber();
                if (serialNumber == null || serialNumber.isBlank()) {
                    logger.warn("ITInventory recordNo {} has empty hostSerialNumber, skipping.", itInv.getRecordNo());
                    result.incFailed();
                    continue;
                }

                boolean existsInFAR = farSerials.contains(serialNumber);
                UnmappedITInventory unmappedOpt = unmappedMap.get(serialNumber);

                if (!existsInFAR) {
                    UnmappedITInventory incoming = assembleUnmappedITRecord(itInv);

                    if (unmappedOpt == null) {
                        toInsert.add(incoming);
                        result.incInserted();
                    } else if (needsUpdate(unmappedOpt, incoming)) {
                        copyUnmappedITDetails(unmappedOpt, incoming);
                        toUpdate.add(unmappedOpt);
                        result.incUpdated();
                    }
                } else {
                    if (unmappedOpt != null) {
                        toDelete.add(unmappedOpt);
                        result.incDeleted();
                    }
                }

            } catch (Exception e) {
                result.incFailed();
                logger.error("Error processing IT inventory {}: {}", itInv.getRecordNo(), e.getMessage(), e);
            }
        }

        if (!toInsert.isEmpty()) unmappedITInventoryRepository.saveAll(toInsert);
        if (!toUpdate.isEmpty()) unmappedITInventoryRepository.saveAll(toUpdate);
        if (!toDelete.isEmpty()) unmappedITInventoryRepository.deleteAll(toDelete);

        entityManager.flush();
        entityManager.clear();

        logger.info("Batch summary: {} UnmappedITInventory inserted, {} updated, {} deleted.", 
                     toInsert.size(), toUpdate.size(), toDelete.size());

        return result;
    }

    // Map ITInventory -> UnmappedITInventory
    private UnmappedITInventory assembleUnmappedITRecord(ITInventory itInv) {
        UnmappedITInventory unmapped = new UnmappedITInventory();
        unmapped.setObjectId(itInv.getObjectId());
        unmapped.setSiteId(itInv.getSiteId());
        unmapped.setHostSerialNumber(itInv.getHostSerialNumber());
        unmapped.setInventoryTypeId(itInv.getInventoryTypeId());
        unmapped.setInventoryType(itInv.getInventoryType());
        unmapped.setHostTypeName(itInv.getHostTypeName());
        unmapped.setFirstScan(itInv.getFirstScan());
        unmapped.setIpAddress(itInv.getIpAddress());
        unmapped.setOsId(itInv.getOsId());
        unmapped.setOsName(itInv.getOsName());
        unmapped.setHardwareVendorId(itInv.getHardwareVendorId());
        unmapped.setHardwareVendorName(itInv.getHardwareVendorName());
        unmapped.setModel(itInv.getModel());
        Boolean virtual = itInv.getVirtual();
        unmapped.setVirtual(virtual == null ? null : (virtual ? 1 : 0));
        unmapped.setHostTypeId(itInv.getHostTypeId());
        unmapped.setCategory(itInv.getCategory());
        return unmapped;
    }

    // Compare fields
    private boolean needsUpdate(UnmappedITInventory a, UnmappedITInventory b) {
        return notEqual(a.getObjectId(), b.getObjectId())
            || notEqual(a.getSiteId(), b.getSiteId())
            || notEqual(a.getInventoryTypeId(), b.getInventoryTypeId())
            || notEqual(a.getInventoryType(), b.getInventoryType())
            || notEqual(a.getHostTypeName(), b.getHostTypeName())
            || notEqual(a.getFirstScan(), b.getFirstScan())
            || notEqual(a.getIpAddress(), b.getIpAddress())
            || notEqual(a.getOsId(), b.getOsId())
            || notEqual(a.getOsName(), b.getOsName())
            || notEqual(a.getHardwareVendorId(), b.getHardwareVendorId())
            || notEqual(a.getHardwareVendorName(), b.getHardwareVendorName())
            || notEqual(a.getModel(), b.getModel())
            || notEqual(a.getVirtual(), b.getVirtual())
            || notEqual(a.getHostTypeId(), b.getHostTypeId())
            || notEqual(a.getCategory(), b.getCategory())
            ;}

    private void copyUnmappedITDetails(UnmappedITInventory target, UnmappedITInventory source) {
        target.setObjectId(source.getObjectId());
        target.setSiteId(source.getSiteId());
        target.setHostSerialNumber(source.getHostSerialNumber());
        target.setInventoryTypeId(source.getInventoryTypeId());
        target.setInventoryType(source.getInventoryType());
        target.setHostTypeName(source.getHostTypeName());
        target.setFirstScan(source.getFirstScan());
        target.setIpAddress(source.getIpAddress());
        target.setOsId(source.getOsId());
        target.setOsName(source.getOsName());
        target.setHardwareVendorId(source.getHardwareVendorId());
        target.setHardwareVendorName(source.getHardwareVendorName());
        target.setModel(source.getModel());
        target.setVirtual(source.getVirtual());
        target.setHostTypeId(source.getHostTypeId());
        target.setCategory(source.getCategory());
    }
    private boolean notEqual(Object a, Object b) {
        return (a == null && b != null) || (a != null && b == null) || (a != null && !a.equals(b));
    }
}
