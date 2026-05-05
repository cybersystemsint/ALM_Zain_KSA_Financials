package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.SnmpManagedDevice;

@Service
public interface SmtpManagedDeviceService {

	SnmpManagedDevice findByHostSerialNumber(String serialNumber);

	Page<SnmpManagedDevice> findAll(Pageable pageable);

	void saveAll(List<SnmpManagedDevice> hosts);
}
