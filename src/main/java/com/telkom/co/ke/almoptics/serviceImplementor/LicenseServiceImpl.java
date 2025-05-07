/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tbLicense;
import com.telkom.co.ke.almoptics.repository.LicenseRepository;
import com.telkom.co.ke.almoptics.services.LicenseService;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
@Transactional
public class LicenseServiceImpl implements LicenseService {

    @Autowired
    private LicenseRepository licenseRepo;

    
    @Override
    public List<tbLicense> findAll() {
        return this.licenseRepo.findAll();

    }
    @Override
    public tbLicense save(tbLicense license) {
        return (tbLicense) this.licenseRepo.save(license);
    }
    @Override
    public List<tbLicense> findByLicenseId(String paramString) {
        return this.licenseRepo.findByLicenseId(paramString);

    }

}
