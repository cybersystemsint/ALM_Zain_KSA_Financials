/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tbLicense;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface LicenseService {

    tbLicense save(tbLicense paramt);

    List<tbLicense> findByLicenseId(String licenseId);
    
      List<tbLicense> findAll();

}
