package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Printer;

@Service
public interface PrinterService {

	Printer findByHostSerialNumber(String serialNumber);

	Page<Printer> findAll(Pageable pageable);

	void saveAll(List<Printer> hosts);
}
