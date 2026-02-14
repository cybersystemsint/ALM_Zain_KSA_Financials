package com.zain.ksa.alm.financials.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.zain.ksa.alm.financials.entity.FarReport;

public interface FarReportService {

	FarReport save(FarReport paramtb_FinancialReport);

	List<FarReport> findByAssetId(String paramString);

	Page<FarReport> findAll(Pageable pageable);

	List<FarReport> findAll(int page, int size);

	void streamExportToCsv(PrintWriter writer, String column, String value, String operator) throws IOException;
}
