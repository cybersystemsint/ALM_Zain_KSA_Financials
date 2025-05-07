/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbPassiveInventory;
import com.telkom.co.ke.almoptics.repository.PassiveInventoryRepository;
import com.telkom.co.ke.almoptics.services.PassiveInventoryService;
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
public class PassiveInventoryServiceImpl implements PassiveInventoryService {

    @Autowired
    private PassiveInventoryRepository passiveInventory;

    @Override
    public tbPassiveInventory findBySerialNumber(String serialNumber) {
        return this.passiveInventory.findBySerialNumber(serialNumber);
    }

    @Override
    public List<tbPassiveInventory> findAll() {
        return this.passiveInventory.findAll();

    }

    @Override
    public void saveAll(List<tbPassiveInventory> nodes) {
        this.passiveInventory.saveAll(nodes);
    }

    @Override
    public Page<tbPassiveInventory> findAll(Pageable pageable) {
        return this.passiveInventory.findAll(pageable);
    }

}
