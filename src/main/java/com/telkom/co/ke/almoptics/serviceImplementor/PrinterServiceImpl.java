/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbNode;
import com.telkom.co.ke.almoptics.entities.tbPrinter;
import com.telkom.co.ke.almoptics.repository.PrinterRepository;
import com.telkom.co.ke.almoptics.services.PrinterService;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
@Transactional
public class PrinterServiceImpl implements PrinterService {

    @Autowired
    private PrinterRepository printerRepo;

    @Override
    public tbPrinter findByHostSerialNumber(String serialNumber) {
        return this.printerRepo.findByHostSerialNumber(serialNumber);
    }
    

    @Override
    public void saveAll(List<tbPrinter> nodes) {
        this.printerRepo.saveAll(nodes);
    }

    @Override
    public Page<tbPrinter> findAll(Pageable pageable) {
        return this.printerRepo.findAll(pageable);
    }
}
