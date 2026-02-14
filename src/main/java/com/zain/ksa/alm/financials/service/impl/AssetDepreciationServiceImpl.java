package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.AssetDepreciation;
import com.zain.ksa.alm.financials.repository.AssetDepreciationRepository;
import com.zain.ksa.alm.financials.service.AssetDepreciationService;

@Service
@Transactional
public class AssetDepreciationServiceImpl implements AssetDepreciationService {

	private final AssetDepreciationRepository assetDepreciationRepository;

	@Autowired
	public AssetDepreciationServiceImpl(AssetDepreciationRepository assetDepreciationRepository) {
		this.assetDepreciationRepository = assetDepreciationRepository;
	}

	@Override
	public List<AssetDepreciation> findAll() {
		return assetDepreciationRepository.findAll();
	}

	@Override
	public AssetDepreciation save(AssetDepreciation assetDepreciation) {
		return assetDepreciationRepository.save(assetDepreciation);
	}

	@Override
	public AssetDepreciation findByAssetCode(String assetCode) {
		return assetDepreciationRepository.findByAssetCode(assetCode);
	}

	@Override
	public AssetDepreciation findByAssetCodeAndDepreciationDate(String assetCode, String depreciationDate) {
		return assetDepreciationRepository.findByAssetCodeAndDepreciationDate(assetCode, depreciationDate);
	}
}
