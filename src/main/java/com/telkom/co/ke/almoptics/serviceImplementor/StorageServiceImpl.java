/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbStorage;
import com.telkom.co.ke.almoptics.repository.StorageRepository;
import com.telkom.co.ke.almoptics.services.StorageService;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
@Transactional
public class StorageServiceImpl implements StorageService {

    @Autowired
    private StorageRepository storageRepo;

    @Override
    public tbStorage findByHostSerialNumber(String serialNumber) {
        return this.storageRepo.findByHostSerialNumber(serialNumber);
    }
}
