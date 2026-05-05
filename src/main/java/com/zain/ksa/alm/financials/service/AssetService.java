package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Asset;

@Service
public interface AssetService {

	List<Asset> findAll();

	Asset save(Asset paramtb_Asset);

	Asset findBySerialNumber(String paramString);

	Asset findBySupplierId(String paramString);

	Asset findByPoId(String paramString);

	List<Asset> findByStatus(String paramString);

	Asset findByAssetCode(String paramString);
}
