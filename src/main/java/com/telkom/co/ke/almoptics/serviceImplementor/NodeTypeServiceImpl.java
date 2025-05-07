/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbNodeType;
import com.telkom.co.ke.almoptics.repository.NodeTypeRepo;
import com.telkom.co.ke.almoptics.services.NodeTypeService;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
@Transactional
public class NodeTypeServiceImpl implements NodeTypeService {

    @Autowired
    private NodeTypeRepo nodeTypeRepo;

    @Override
    public tbNodeType findById(Integer id) {
        return this.nodeTypeRepo.findById(id);
    }

    @Override
    public tbNodeType findByNodeType(String nodeType) {
        return this.nodeTypeRepo.findByNodeType(nodeType);
    }

}
