package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.AssetTracking;

@Repository
public interface AssetTrackingRepository extends JpaRepository<AssetTracking, Long> {

	List<AssetTracking> findBySerialNumber(String serialNumber);

	List<AssetTracking> findBySiteId(String serialNumber);

}
