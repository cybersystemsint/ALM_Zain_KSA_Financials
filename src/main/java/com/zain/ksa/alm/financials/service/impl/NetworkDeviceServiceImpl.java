package com.zain.ksa.alm.financials.service.impl;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.NetworkDevice;
import com.zain.ksa.alm.financials.repository.NetworkDeviceRepository;
import com.zain.ksa.alm.financials.service.NetworkDeviceService;

@Service
@Transactional
public class NetworkDeviceServiceImpl implements NetworkDeviceService {

	private final NetworkDeviceRepository networkDeviceRepository;

	@Autowired
	public NetworkDeviceServiceImpl(NetworkDeviceRepository networkDeviceRepository) {
		this.networkDeviceRepository = networkDeviceRepository;
	}

	@Override
	public NetworkDevice findBySerialNumber(String serialNumber) {
		return networkDeviceRepository.findBySerialNumber(serialNumber);
	}
}
