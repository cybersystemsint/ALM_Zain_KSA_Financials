package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.AssetJournal;

@Repository
public interface AssetJournalRepository extends JpaRepository<AssetJournal, Long> {
	List<AssetJournal> findAll();

	AssetJournal findByAssetCode(String paramString);
}