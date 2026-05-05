package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.AssetAllocation;

@Repository
public interface AssetAllocationRepository extends JpaRepository<AssetAllocation, Long> {
	List<AssetAllocation> findAll();

	AssetAllocation findByLocationId(String paramString);

	AssetAllocation findByAssetCode(String paramString);

	AssetAllocation findByPersonId(String paramString);

	List<AssetAllocation> findBystatus(String paramString);
}
