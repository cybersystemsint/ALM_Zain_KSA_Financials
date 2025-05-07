/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics;

import com.telkom.co.ke.almoptics.entities.tbHost;
import com.telkom.co.ke.almoptics.entities.tbNode;
import com.telkom.co.ke.almoptics.entities.tbPassiveInventory;
import com.telkom.co.ke.almoptics.entities.tbPrinter;
import com.telkom.co.ke.almoptics.entities.tbSNMPManagedDevice;
import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.services.AssetService;
import com.telkom.co.ke.almoptics.services.FarReportService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import com.telkom.co.ke.almoptics.services.HostService;
import com.telkom.co.ke.almoptics.services.NetworkDeviceService;
import com.telkom.co.ke.almoptics.services.NodeTypeService;
import com.telkom.co.ke.almoptics.services.PassiveInventoryService;
import com.telkom.co.ke.almoptics.services.PrinterService;
import com.telkom.co.ke.almoptics.services.SmtpManagedDeviceService;
import com.telkom.co.ke.almoptics.services.StorageService;
import com.telkom.co.ke.almoptics.services.tbNodeService;
import com.telkom.co.ke.almoptics.services.tb_Asset_DepreciationService;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 *
 * @author jgithu THIS SCHEDULER DOES MAPPING OF ACTIVE INVENTORY
 */
@Component
public class UnmappedReportScheduler {

    private static final Logger log = LogManager.getLogger(UnmappedReportScheduler.class.getName());

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

    //@Scheduled(cron = "0 0 1 * * *", zone = "Africa/Nairobi")
    public void syncActiveInventory() {
        try {
            int pageNumber = 0;
            int pageSize = 2500;
            Page<tbNode> page;
            log.info("SCHEDULER STARTED FOR ACTIVE UNMAPPED SYNC");
            //LETS SYNC ACTIVE INVENTORY HERE FOR NODES 
            List<tb_FarReport> farReports = farReportService.findByInventoryStatus("Node");
            do {

                Pageable pageable1 = PageRequest.of(pageNumber++, pageSize);
                page = nodeService.findAll(pageable1);
                List<tbNode> assetsHere = page.getContent();

                assetsHere.forEach(node -> {
                    boolean existsInFarReport = farReports.stream()
                            .anyMatch(far -> far.getSerialNumber().equals(node.getSerialNumber()));
                    node.setIsMapped(existsInFarReport);
                });
                nodeService.saveAll(assetsHere);
            } while (page.hasNext());
            log.info("SCHEDULER COMPLETED FOR ACTIVE UNMAPPED SYNC");

//            assetsHere.forEach(node -> {
//                boolean existsInFarReport = farReports.stream().anyMatch(far -> far.getSerialNumber().equals(node.getSerialNumber()));
//                node.setIsMapped(existsInFarReport);
//            });
//            nodeService.saveAll(assetsHere);
            log.info("SCHEDULER STARTED FOR PASSIVE UNMAPPED SYNC");

            List<tb_FarReport> farRPT = farReportService.findByInventoryStatus("Passive");
            //PASSIVE INVENTORY 
            int passivepageNumber = 0;
            int passivepageSize = 3000;
            Page<tbPassiveInventory> pagepassive;

            do {

                Pageable pageable2 = PageRequest.of(passivepageNumber++, passivepageSize);
                pagepassive = passiveService.findAll(pageable2);
                List<tbPassiveInventory> passiveInv = pagepassive.getContent();

                passiveInv.forEach(passive -> {
                    boolean passiveexistsInFarReport = farRPT.stream().anyMatch(far -> far.getSerialNumber().equals(passive.getSerialNumber()));
                    passive.setIsMapped(passiveexistsInFarReport);
                });
                passiveService.saveAll(passiveInv);
            } while (pagepassive.hasNext());

            log.info("SCHEDULER COMPLETED FOR PASSIVE UNMAPPED SYNC");

            log.info("SCHEDULER STARTED FOR HOST UNMAPPED SYNC");

            List<tb_FarReport> farRPT1 = farReportService.findByInventoryStatus("Host");
            //HOST INVENTORY 
            int hostpageNumber = 0;
            int hostpageSize = 3000;
            Page<tbHost> pagehost;

            do {

                Pageable pageable2 = PageRequest.of(hostpageNumber++, hostpageSize);
                pagehost = hostService.findAll(pageable2);
                List<tbHost> HostInv = pagehost.getContent();

                HostInv.forEach(host -> {
                    boolean existsInFarReport = farRPT1.stream().anyMatch(far -> far.getSerialNumber().equals(host.getHostSerialNumber()));
                    host.setIsMapped(existsInFarReport);
                });
                hostService.saveAll(HostInv);
            } while (pagehost.hasNext());

            log.info("SCHEDULER COMPLETED FOR HOST UNMAPPED SYNC");

            ///UNMAPPED PRINTER CHECK  
            log.info("SCHEDULER STARTED FOR PRINTER UNMAPPED SYNC");

            List<tb_FarReport> farRPT2 = farReportService.findByInventoryStatus("Printer");
            //PRINTER INVENTORY 
            int printerpageNumber = 0;
            int printerpageSize = 3000;
            Page<tbPrinter> pageprinter;

            do {

                Pageable pageable2 = PageRequest.of(printerpageNumber++, printerpageSize);
                pageprinter = printerService.findAll(pageable2);
                List<tbPrinter> PrinterInv = pageprinter.getContent();

                PrinterInv.forEach(printer -> {
                    boolean existsInFarReport = farRPT2.stream().anyMatch(far -> far.getSerialNumber().equals(printer.getHostSerialNumber()));
                    printer.setIsMapped(existsInFarReport);
                });
                printerService.saveAll(PrinterInv);
            } while (pageprinter.hasNext());

            log.info("SCHEDULER COMPLETED FOR PRINTER UNMAPPED SYNC");

            ///UNMAPPED PRINTER CHECK  
            log.info("SCHEDULER STARTED FOR NETWORK DEVICES UNMAPPED SYNC");

            List<tb_FarReport> farRPT3 = farReportService.findByInventoryStatus("SNMPManagedDevice");
            //PRINTER INVENTORY 
            int networkpageNumber = 0;
            int networkpageSize = 3000;
            Page<tbSNMPManagedDevice> pagedevices;

            do {

                Pageable pageable2 = PageRequest.of(networkpageNumber++, networkpageSize);
                pagedevices = smtpService.findAll(pageable2);
                List<tbSNMPManagedDevice> networkInv = pagedevices.getContent();

                networkInv.forEach(network -> {
                    boolean existsInFarReport = farRPT3.stream().anyMatch(far -> far.getSerialNumber().equals(network.getHostSerialNumber()));
                    network.setIsMapped(existsInFarReport);
                });
                smtpService.saveAll(networkInv);
            } while (pagedevices.hasNext());

            log.info("SCHEDULER COMPLETED FOR NETWORK DEVICES UNMAPPED SYNC");

        } catch (Exception ex) {
            log.error("Exception occurred during scheduled task: ", ex);
        }
    }
}
