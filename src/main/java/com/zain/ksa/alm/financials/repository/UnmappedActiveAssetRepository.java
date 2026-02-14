package com.zain.ksa.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.UnmappedActiveAsset;

@Repository
public interface UnmappedActiveAssetRepository extends JpaRepository<UnmappedActiveAsset, Long> {

	UnmappedActiveAsset findBySerialNumber(String serialNumber);
}
