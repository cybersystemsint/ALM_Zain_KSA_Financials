/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tbAssetTracking;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface AssetTrackingService {

    List<tbAssetTracking> findAll();

    List<tbAssetTracking> findBySerialNumber(String serialNumber);

    List<tbAssetTracking> findBySiteId(String siteId);

    tbAssetTracking save(tbAssetTracking paramtb_FinancialReport);
}
