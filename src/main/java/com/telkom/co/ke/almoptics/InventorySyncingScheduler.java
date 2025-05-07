/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics;

import com.telkom.co.ke.almoptics.entities.tbNode;
import com.telkom.co.ke.almoptics.entities.tbStorage;
import com.telkom.co.ke.almoptics.entities.tbNetworkDevice;
import com.telkom.co.ke.almoptics.entities.tbSNMPManagedDevice;
import com.telkom.co.ke.almoptics.entities.tbPrinter;
import com.telkom.co.ke.almoptics.entities.tbPassiveInventory;
import com.telkom.co.ke.almoptics.entities.tbHost;
import com.telkom.co.ke.almoptics.entities.tbNodeType;
import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.services.FarReportService;
import com.telkom.co.ke.almoptics.services.PassiveInventoryService;
import com.telkom.co.ke.almoptics.services.HostService;
import com.telkom.co.ke.almoptics.services.NetworkDeviceService;
import com.telkom.co.ke.almoptics.services.NodeTypeService;
import com.telkom.co.ke.almoptics.services.PrinterService;
import com.telkom.co.ke.almoptics.services.SmtpManagedDeviceService;
import com.telkom.co.ke.almoptics.services.StorageService;
import com.telkom.co.ke.almoptics.services.tbNodeService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;

/**
 *
 * @author jgithu
 */
@Component
public class InventorySyncingScheduler {

    private static final Logger LOGGER = LogManager.getLogger(DepreciationScheduler.class.getName());

    @Autowired
    private FarReportService farReportService;

    @Autowired
    private tbNodeService nodeService;

    @Autowired
    private PassiveInventoryService passiveService;

    @Autowired
    private HostService hostService;

    @Autowired
    private PrinterService printerService;

    @Autowired
    private SmtpManagedDeviceService smtpService;

    @Autowired
    private NetworkDeviceService networkDeviceService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private NodeTypeService nodeTypeService;

    //@Scheduled(cron = "0 0 20 * * *", zone = "Africa/Nairobi")
    public void processSynching() {
        try {
            LOGGER.info("SCHEDULER STARTED FOR SYNCHING FOR DECOMMISSION  ");
            int pageNumber = 0;
            Page<tb_FarReport> page;
            int pageSize = 2500;
            do {
                Pageable pageable1 = PageRequest.of(pageNumber, pageSize);
                page = farReportService.findAll(pageable1);

                List<tb_FarReport> assetsHere = page.getContent();

                processRecords(assetsHere);

                pageNumber++;
            } while (page.hasNext());

            LOGGER.info("SCHEDULER COMPLETED FOR SYNCHING FOR DECOMMISSION ");
        } catch (Exception ex) {
            LOGGER.error("Exception occurred during scheduled task: ", ex);
        }
    }

    private void processRecords(List<tb_FarReport> assetsHere) {
        assetsHere.forEach(asset -> {
            try {
                LocalDate localDate = LocalDate.now();
                String serialNumber = asset.getSerialNumber();
                if (serialNumber == null || serialNumber.isEmpty()) {
                    return;
                }
                tbNode nodes = this.nodeService.findBySerialNumber(serialNumber);
                tbPassiveInventory passiveInv = this.passiveService.findBySerialNumber(serialNumber);
                tbHost host = this.hostService.findByHostSerialNumber(serialNumber);
                tbPrinter printer = this.printerService.findByHostSerialNumber(serialNumber);
                tbSNMPManagedDevice devices = this.smtpService.findByHostSerialNumber(serialNumber);
                tbNetworkDevice network = networkDeviceService.findBySerialNumber(serialNumber);
                tbStorage storage = storageService.findByHostSerialNumber(serialNumber);
                LocalDate twentyDaysAgo = LocalDate.now().minusDays(21);

                //START BY CHECKING ACTIVE 
                if (nodes != null && nodes.getSerialNumber() != null && !nodes.getSerialNumber().isEmpty()) {
                    Date lastchangedDate = nodes.getChangedDate();

                    tbNodeType nodetype = this.nodeTypeService.findById(nodes.getNodeTypeId());
                    if (lastchangedDate != null) {
                        LocalDate lastUpdatedLocalDate = lastchangedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        asset.setInventoryStatus("Node");
                        asset.setNodeType(nodetype.getNodeType());
                        if (lastUpdatedLocalDate.isBefore(twentyDaysAgo)) {
                            asset.setStatusFlag("Decommissioned");
                            asset.setChangedDate(new Date());
                            asset.setChangedBy("System");
                            LOGGER.info("Active Asset with serial number " + serialNumber + " has been Decommissioned.");
                        }

                        this.farReportService.save(asset);
                    }
                } else if (passiveInv != null && passiveInv.getSerialNumber() != null && !passiveInv.getSerialNumber().isEmpty()) {
                    //CHECK PASSIVE DATA HERE 
                    Date lastchangedDate = passiveInv.getChangedDate();
                    if (lastchangedDate != null) {
                        LocalDate lastUpdatedLocalDate = lastchangedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        asset.setNodeType("Passive Node");
                        asset.setInventoryStatus("Passive");
                        if (lastUpdatedLocalDate.isBefore(twentyDaysAgo)) {
                            asset.setStatusFlag("Decommissioned");
                            asset.setChangedDate(new Date());
                            asset.setChangedBy("System");

                            LOGGER.info("Passive Asset with serial number " + serialNumber + " has been Decommissioned.");
                        }
                        this.farReportService.save(asset);
                    }
                } else if (host != null && host.getHostSerialNumber() != null && !host.getHostSerialNumber().isEmpty()) {
                    Date lastchangedDate = host.getChangedDate();
                    if (lastchangedDate != null) {
                        LocalDate lastUpdatedLocalDate = lastchangedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        asset.setNodeType("Passive Node");
                        asset.setInventoryStatus("Host");
                        if (lastUpdatedLocalDate.isBefore(twentyDaysAgo)) {
                            asset.setStatusFlag("Decommissioned");
                            asset.setChangedDate(new Date());
                            asset.setChangedBy("System");

                            LOGGER.info("Host IT Asset with serial number " + serialNumber + " has been Decommissioned.");
                        }
                        this.farReportService.save(asset);
                    }
                } else if (printer != null && printer.getHostSerialNumber() != null && !printer.getHostSerialNumber().isEmpty()) {

                    Date lastchangedDate = printer.getChangedDate();
                    if (lastchangedDate != null) {
                        LocalDate lastUpdatedLocalDate = lastchangedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        asset.setNodeType("Passive Node");
                        asset.setInventoryStatus("Printer");
                        if (lastUpdatedLocalDate.isBefore(twentyDaysAgo)) {
                            asset.setStatusFlag("Decommissioned");
                            asset.setChangedDate(new Date());
                            asset.setChangedBy("System");

                            LOGGER.info("Printer IT Asset with serial number " + serialNumber + " has been Decommissioned.");
                        }
                        this.farReportService.save(asset);
                    }
                } else if (devices != null && devices.getHostSerialNumber() != null && !devices.getHostSerialNumber().isEmpty()) {
                    Date lastchangedDate = devices.getChangedDate();
                    if (lastchangedDate != null) {
                        LocalDate lastUpdatedLocalDate = lastchangedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        asset.setNodeType("Passive Node");
                        asset.setInventoryStatus("SNMPManagedDevice");
                        if (lastUpdatedLocalDate.isBefore(twentyDaysAgo)) {
                            asset.setStatusFlag("Decommissioned");
                            asset.setChangedDate(new Date());
                            asset.setChangedBy("System");
                            LOGGER.info("Device Asset with serial number " + serialNumber + " has been Decommissioned.");
                        }
                        this.farReportService.save(asset);
                    }
                } else if (network != null && network.getSerialNumber() != null && !network.getSerialNumber().isEmpty()) {

                    Date lastchangedDate = network.getChangedDate();
                    if (lastchangedDate != null) {
                        LocalDate lastUpdatedLocalDate = lastchangedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        asset.setNodeType("Passive Node");
                        asset.setInventoryStatus("NetworkDevice");
                        if (lastUpdatedLocalDate.isBefore(twentyDaysAgo)) {
                            asset.setStatusFlag("Decommissioned");
                            asset.setChangedDate(new Date());
                            asset.setChangedBy("System");
                            LOGGER.info("Network IT Asset with serial number " + serialNumber + " has been Decommissioned.");
                        }
                        this.farReportService.save(asset);
                    }
                } else if (storage != null && storage.getHostSerialNumber() != null && !storage.getHostSerialNumber().isEmpty()) {
                    Date lastchangedDate = storage.getChangedDate();
                    if (lastchangedDate != null) {
                        LocalDate lastUpdatedLocalDate = lastchangedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        asset.setNodeType("Passive Node");
                        asset.setInventoryStatus("Storage");
                        if (lastUpdatedLocalDate.isBefore(twentyDaysAgo)) {
                            asset.setStatusFlag("Decommissioned");
                            asset.setChangedDate(new Date());
                            asset.setChangedBy("System");
                            LOGGER.info("Storage  Asset with serial number " + serialNumber + " has been Decommissioned.");
                        }
                        this.farReportService.save(asset);
                    }
                }
            } catch (Exception ex) {
                LOGGER.info("Exception: ", ex);
            }
        }
        );
    }
}
