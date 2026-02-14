package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.AssetDepreciation;

@Service
public interface AssetDepreciationService {
	List<AssetDepreciation> findAll();

	AssetDepreciation save(AssetDepreciation paramtb_Asset_Depreciation);

	AssetDepreciation findByAssetCode(String paramString);

	AssetDepreciation findByAssetCodeAndDepreciationDate(String paramString, String depreciationDate);
}
