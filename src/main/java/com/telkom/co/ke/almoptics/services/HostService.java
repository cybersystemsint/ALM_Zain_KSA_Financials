/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tbHost;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface HostService {

    tbHost findByHostSerialNumber(String serialNumber);

    
    List<tbHost> findAll();

    void saveAll(List<tbHost> hosts);
    
        Page<tbHost> findAll(Pageable pageable);
}
