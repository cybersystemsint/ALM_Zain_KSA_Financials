package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.License;

@Repository
public interface LicenseRepository extends JpaRepository<License, Long> {

	List<License> findByLicenseId(String licenseId);

	@Override
	List<License> findAll();
}
