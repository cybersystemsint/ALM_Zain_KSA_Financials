/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tbPassiveInventory;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface PassiveInventoryService {

    tbPassiveInventory findBySerialNumber(String serialNumber);

    List<tbPassiveInventory> findAll();

    void saveAll(List<tbPassiveInventory> nodes);

    Page<tbPassiveInventory> findAll(Pageable pageable);

}
