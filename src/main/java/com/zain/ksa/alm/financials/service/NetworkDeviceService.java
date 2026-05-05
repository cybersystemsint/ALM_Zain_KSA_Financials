package com.zain.ksa.alm.financials.service;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.NetworkDevice;

@Service
public interface NetworkDeviceService {

	NetworkDevice findBySerialNumber(String serialNumber);

}
