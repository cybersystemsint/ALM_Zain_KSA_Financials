package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.UnmappedActiveAsset;
import com.zain.ksa.alm.financials.repository.UnmappedActiveAssetRepository;
import com.zain.ksa.alm.financials.service.UnmappedActiveAssetService;

@Service
@Transactional
public class UnmappedActiveAssetServiceImpl implements UnmappedActiveAssetService {

	private final UnmappedActiveAssetRepository unmappedActiveAssetRepository;

	@Autowired
	public UnmappedActiveAssetServiceImpl(UnmappedActiveAssetRepository unmappedActiveAssetRepository) {
		this.unmappedActiveAssetRepository = unmappedActiveAssetRepository;
	}

	@Override
	public List<UnmappedActiveAsset> findAll() {
		return unmappedActiveAssetRepository.findAll();
	}

	@Override
	public UnmappedActiveAsset findBySerialNumber(String serialNumber) {
		return unmappedActiveAssetRepository.findBySerialNumber(serialNumber);
	}
}
