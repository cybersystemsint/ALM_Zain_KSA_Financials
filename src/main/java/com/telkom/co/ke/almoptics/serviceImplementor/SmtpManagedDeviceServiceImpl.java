/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbPrinter;
import com.telkom.co.ke.almoptics.entities.tbSNMPManagedDevice;
import com.telkom.co.ke.almoptics.repository.SmtpManagedDeviceRepository;
import com.telkom.co.ke.almoptics.services.SmtpManagedDeviceService;
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
public class SmtpManagedDeviceServiceImpl implements SmtpManagedDeviceService {

    @Autowired
    private SmtpManagedDeviceRepository SmtpManagedDeviceRepo;

    @Override
    public tbSNMPManagedDevice findByHostSerialNumber(String serialNumber) {
        return this.SmtpManagedDeviceRepo.findByHostSerialNumber(serialNumber);
    }

    @Override
    public void saveAll(List<tbSNMPManagedDevice> nodes) {
        this.SmtpManagedDeviceRepo.saveAll(nodes);
    }

    @Override
    public Page<tbSNMPManagedDevice> findAll(Pageable pageable) {
        return this.SmtpManagedDeviceRepo.findAll(pageable);
    }
}
