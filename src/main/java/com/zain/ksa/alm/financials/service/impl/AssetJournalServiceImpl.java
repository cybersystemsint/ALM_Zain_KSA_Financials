package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.AssetJournal;
import com.zain.ksa.alm.financials.repository.AssetJournalRepository;
import com.zain.ksa.alm.financials.service.AssetJournalService;

@Service
@Transactional
public class AssetJournalServiceImpl implements AssetJournalService {

	private final AssetJournalRepository assetJournalRepository;

	@Autowired
	public AssetJournalServiceImpl(AssetJournalRepository assetJournalRepository) {
		this.assetJournalRepository = assetJournalRepository;
	}

	@Override
	public List<AssetJournal> findAll() {
		return assetJournalRepository.findAll();
	}

	@Override
	public AssetJournal save(AssetJournal assetJournal) {
		return assetJournalRepository.save(assetJournal);
	}

	@Override
	public AssetJournal findByAssetCode(String assetCode) {
		return assetJournalRepository.findByAssetCode(assetCode);
	}
}
