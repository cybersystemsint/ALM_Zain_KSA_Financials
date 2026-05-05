package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.License;
import com.zain.ksa.alm.financials.repository.LicenseRepository;
import com.zain.ksa.alm.financials.service.LicenseService;

@Service
@Transactional
public class LicenseServiceImpl implements LicenseService {

	private final LicenseRepository licenseRepository;

	@Autowired
	public LicenseServiceImpl(LicenseRepository licenseRepository) {
		this.licenseRepository = licenseRepository;
	}

	@Override
	public List<License> findAll() {
		return licenseRepository.findAll();
	}

	@Override
	public License save(License license) {
		return licenseRepository.save(license);
	}

	@Override
	public List<License> findByLicenseId(String licenseId) {
		return licenseRepository.findByLicenseId(licenseId);
	}
}
