package com.zain.ksa.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.SnmpManagedDevice;

@Repository
public interface SmtpManagedDeviceRepository extends JpaRepository<SnmpManagedDevice, Long> {

	SnmpManagedDevice findByHostSerialNumber(String hostSerialNumber);

	@Override
	Page<SnmpManagedDevice> findAll(Pageable pageable);
}
