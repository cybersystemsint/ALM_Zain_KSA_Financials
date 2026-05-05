package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.SnmpManagedDevice;
import com.zain.ksa.alm.financials.repository.SmtpManagedDeviceRepository;
import com.zain.ksa.alm.financials.service.SmtpManagedDeviceService;

@Service
@Transactional
public class SmtpManagedDeviceServiceImpl implements SmtpManagedDeviceService {

	private final SmtpManagedDeviceRepository smtpManagedDeviceRepository;

	@Autowired
	public SmtpManagedDeviceServiceImpl(SmtpManagedDeviceRepository smtpManagedDeviceRepository) {
		this.smtpManagedDeviceRepository = smtpManagedDeviceRepository;
	}

	@Override
	public SnmpManagedDevice findByHostSerialNumber(String serialNumber) {
		return smtpManagedDeviceRepository.findByHostSerialNumber(serialNumber);
	}

	@Override
	public void saveAll(List<SnmpManagedDevice> devices) {
		smtpManagedDeviceRepository.saveAll(devices);
	}

	@Override
	public Page<SnmpManagedDevice> findAll(Pageable pageable) {
		return smtpManagedDeviceRepository.findAll(pageable);
	}
}
