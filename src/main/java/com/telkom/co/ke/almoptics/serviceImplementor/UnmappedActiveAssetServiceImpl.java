/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbUnmappedActiveAsset;
import com.telkom.co.ke.almoptics.services.UnmappedActiveAssetService;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.telkom.co.ke.almoptics.repository.UnmappedActiveAssetRepo;

/**
 *
 * @author jgithu
 */
@Service
@Transactional
public class UnmappedActiveAssetServiceImpl implements UnmappedActiveAssetService {

    @Autowired
    private UnmappedActiveAssetRepo unmappedActiveRepo;

    @Override
    public List<tbUnmappedActiveAsset> findAll() {
        return this.unmappedActiveRepo.findAll();

    }


    @Override
    public tbUnmappedActiveAsset findBySerialNumber(String paramString) {
        return this.unmappedActiveRepo.findBySerialNumber(paramString);
    }


}

