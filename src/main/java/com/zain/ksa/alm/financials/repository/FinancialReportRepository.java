package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.FinancialReport;

@Repository
public interface FinancialReportRepository extends JpaRepository<FinancialReport, Long> {

	FinancialReport findBySerialNumber(String serialNumber);

	List<FinancialReport> findByAssetId(String assetId);
}
