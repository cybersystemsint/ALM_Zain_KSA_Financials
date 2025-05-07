/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbHost;
import com.telkom.co.ke.almoptics.repository.HostRepository;
import com.telkom.co.ke.almoptics.services.HostService;
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
public class HostServiceImpl implements HostService {

    @Autowired
    private HostRepository hostRepo;

    @Override
    public tbHost findByHostSerialNumber(String serialNumber) {
        return this.hostRepo.findByHostSerialNumber(serialNumber);
    }

    @Override
    public List<tbHost> findAll() {
        return this.hostRepo.findAll();

    }

    @Override
    public void saveAll(List<tbHost> nodes) {
        this.hostRepo.saveAll(nodes);
    }

    @Override
    public Page<tbHost> findAll(Pageable pageable) {
        return this.hostRepo.findAll(pageable);
    }

}
