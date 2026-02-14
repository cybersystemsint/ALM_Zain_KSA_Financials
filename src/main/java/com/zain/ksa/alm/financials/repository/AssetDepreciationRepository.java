package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.AssetDepreciation;

@Repository
public interface AssetDepreciationRepository extends JpaRepository<AssetDepreciation, Long> {

	List<AssetDepreciation> findAll();

	AssetDepreciation findByAssetCode(String paramString);

	AssetDepreciation findByAssetCodeAndDepreciationDate(String paramString, String depreciationDate);

}