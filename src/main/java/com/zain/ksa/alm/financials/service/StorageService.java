package com.zain.ksa.alm.financials.service;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Storage;

@Service
public interface StorageService {

	Storage findByHostSerialNumber(String serialNumber);
}
