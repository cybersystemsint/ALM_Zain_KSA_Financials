package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.AssetAllocation;

@Service
public interface AssetAllocationService {
	List<AssetAllocation> findAll();

	AssetAllocation save(AssetAllocation paramtb_Asset_Allocation);

	List<AssetAllocation> findBystatus(String paramString);

	AssetAllocation findByLocationId(String paramString);

	AssetAllocation findByPersonId(String paramString);

	AssetAllocation findByAssetCode(String paramString);
}
