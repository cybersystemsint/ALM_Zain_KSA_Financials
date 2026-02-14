package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.AssetAllocation;
import com.zain.ksa.alm.financials.repository.AssetAllocationRepository;
import com.zain.ksa.alm.financials.service.AssetAllocationService;

@Service
@Transactional
public class AssetAllocationServiceImpl implements AssetAllocationService {

	private final AssetAllocationRepository assetAllocationRepository;

	@Autowired
	public AssetAllocationServiceImpl(AssetAllocationRepository assetAllocationRepository) {
		this.assetAllocationRepository = assetAllocationRepository;
	}

	@Override
	public List<AssetAllocation> findAll() {
		return assetAllocationRepository.findAll();
	}

	@Override
	public AssetAllocation save(AssetAllocation allocation) {
		return assetAllocationRepository.save(allocation);
	}

	@Override
	public List<AssetAllocation> findBystatus(String status) {
		return assetAllocationRepository.findBystatus(status);
	}

	@Override
	public AssetAllocation findByLocationId(String locationId) {
		return assetAllocationRepository.findByLocationId(locationId);
	}

	@Override
	public AssetAllocation findByPersonId(String personId) {
		return assetAllocationRepository.findByPersonId(personId);
	}

	@Override
	public AssetAllocation findByAssetCode(String assetCode) {
		return assetAllocationRepository.findByAssetCode(assetCode);
	}
}
