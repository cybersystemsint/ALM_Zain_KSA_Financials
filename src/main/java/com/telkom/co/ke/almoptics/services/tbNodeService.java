/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tbNode;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface tbNodeService {

    tbNode findBySerialNumber(String serialNumber);

    List<tbNode> findAll();

    void saveAll(List<tbNode> nodes);

    List<tbNode> findByNode(String paramString);

    tbNode save(tbNode param);

    Page<tbNode> findAll(Pageable pageable);

}
