package com.zain.ksa.alm.financials.service.impl;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Storage;
import com.zain.ksa.alm.financials.repository.StorageRepository;
import com.zain.ksa.alm.financials.service.StorageService;

@Service
@Transactional
public class StorageServiceImpl implements StorageService {

	private final StorageRepository storageRepository;

	@Autowired
	public StorageServiceImpl(StorageRepository storageRepository) {
		this.storageRepository = storageRepository;
	}

	@Override
	public Storage findByHostSerialNumber(String serialNumber) {
		return storageRepository.findByHostSerialNumber(serialNumber);
	}
}
