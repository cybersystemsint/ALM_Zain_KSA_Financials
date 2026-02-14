package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.FinancialReport;

@Service
public interface FinancialReportService {

	List<FinancialReport> findAll();

	FinancialReport save(FinancialReport paramtb_FinancialReport);

	FinancialReport findBySerialNumber(String paramString);

	List<FinancialReport> findByAssetId(String paramString);

}
