package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.License;

@Service
public interface LicenseService {

	License save(License paramt);

	List<License> findByLicenseId(String licenseId);

	List<License> findAll();

}
