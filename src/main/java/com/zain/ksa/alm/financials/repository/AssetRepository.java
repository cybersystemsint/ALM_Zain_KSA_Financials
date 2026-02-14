package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.Asset;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
	List<Asset> findAll();

	Asset findBySerialNumber(String paramString);

	Asset findBySupplierId(String paramString);

	Asset findByPoId(String paramString);

	Asset findByAssetCode(String paramString);

	List<Asset> findByStatus(String paramString);
}
