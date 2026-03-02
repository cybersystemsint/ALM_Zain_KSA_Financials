// package com.zain.ksa.alm.financials.service.impl;

// import java.util.List;

// import javax.transaction.Transactional;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import com.zain.ksa.alm.financials.entity.FinancialReport;
// import com.zain.ksa.alm.financials.repository.FinancialReportRepository;
// import com.zain.ksa.alm.financials.service.FinancialReportService;

// @Service
// @Transactional
// public class FinancialReportServiceImpl implements FinancialReportService {

// 	private final FinancialReportRepository financialReportRepository;

// 	@Autowired
// 	public FinancialReportServiceImpl(FinancialReportRepository financialReportRepository) {
// 		this.financialReportRepository = financialReportRepository;
// 	}

// 	public List<FinancialReport> findAll() {
// 		return financialReportRepository.findAll();
// 	}

// 	public FinancialReport save(FinancialReport boards) {
// 		return financialReportRepository.save(boards);
// 	}

// 	public FinancialReport findBySerialNumber(String paramString) {
// 		return financialReportRepository.findBySerialNumber(paramString);
// 	}

// 	public List<FinancialReport> findByAssetId(String paramString) {
// 		return financialReportRepository.findByAssetId(paramString);
// 	}
// }
