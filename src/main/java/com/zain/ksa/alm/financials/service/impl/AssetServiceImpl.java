package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Asset;
import com.zain.ksa.alm.financials.repository.AssetRepository;
import com.zain.ksa.alm.financials.service.AssetService;

@Service
@Transactional
public class AssetServiceImpl implements AssetService {

	private final AssetRepository assetRepository;

	@Autowired
	public AssetServiceImpl(AssetRepository assetRepository) {
		this.assetRepository = assetRepository;
	}

	@Override
	public List<Asset> findAll() {
		return assetRepository.findAll();
	}

	@Override
	public Asset save(Asset asset) {
		return assetRepository.save(asset);
	}

	@Override
	public Asset findBySerialNumber(String serialNumber) {
		return assetRepository.findBySerialNumber(serialNumber);
	}

	@Override
	public Asset findBySupplierId(String supplierId) {
		return assetRepository.findBySupplierId(supplierId);
	}

	@Override
	public Asset findByPoId(String poId) {
		return assetRepository.findByPoId(poId);
	}

	@Override
	public List<Asset> findByStatus(String status) {
		return assetRepository.findByStatus(status);
	}

	@Override
	public Asset findByAssetCode(String assetCode) {
		return assetRepository.findByAssetCode(assetCode);
	}
}
