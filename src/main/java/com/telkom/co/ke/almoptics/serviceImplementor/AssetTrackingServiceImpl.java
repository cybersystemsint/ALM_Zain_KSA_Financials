/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbAssetTracking;
import com.telkom.co.ke.almoptics.repository.AssetTrackingRepository;
import com.telkom.co.ke.almoptics.services.AssetTrackingService;
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
public class AssetTrackingServiceImpl implements AssetTrackingService {

    @Autowired
    private AssetTrackingRepository trackingRepository;

    @Override
    public List<tbAssetTracking> findAll() {
        return this.trackingRepository.findAll();

    }

    @Override
    public tbAssetTracking save(tbAssetTracking boards) {
        return (tbAssetTracking) this.trackingRepository.save(boards);
    }

    @Override
    public List<tbAssetTracking> findBySerialNumber(String paramString) {
        return this.trackingRepository.findBySerialNumber(paramString);
    }

    @Override
    public List<tbAssetTracking> findBySiteId(String paramString) {
        return this.trackingRepository.findBySiteId(paramString);

    }
}
