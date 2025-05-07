/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbNode;
import com.telkom.co.ke.almoptics.repository.tbNodeRepository;
import com.telkom.co.ke.almoptics.services.tbNodeService;
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
public class tbNodeServiceImpl implements tbNodeService {

    @Autowired
    private tbNodeRepository nodeRepository;

    @Override
    public tbNode findBySerialNumber(String serialNumber) {
        return this.nodeRepository.findBySerialNumber(serialNumber);
    }

    @Override
    public List<tbNode> findAll() {
        return this.nodeRepository.findAll();

    }

    @Override
    public void saveAll(List<tbNode> nodes) {
        this.nodeRepository.saveAll(nodes);
    }

    @Override
    public List<tbNode> findByNode(String paramString) {
        return this.nodeRepository.findByNode(paramString);

    }

    @Override
    public tbNode save(tbNode paramString) {
        return (tbNode) this.nodeRepository.save(paramString);
    }

    @Override
    public Page<tbNode> findAll(Pageable pageable) {
        return this.nodeRepository.findAll(pageable);
    }

}
