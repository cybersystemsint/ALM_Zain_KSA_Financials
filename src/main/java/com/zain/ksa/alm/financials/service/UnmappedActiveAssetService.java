package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.UnmappedActiveAsset;

@Service
public interface UnmappedActiveAssetService {

	List<UnmappedActiveAsset> findAll();

	UnmappedActiveAsset findBySerialNumber(String paramString);

}
