package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.AssetJournal;

@Service
public interface AssetJournalService {
	List<AssetJournal> findAll();

	AssetJournal findByAssetCode(String paramString);

	AssetJournal save(AssetJournal paramtb_Asset_Journal);
}
