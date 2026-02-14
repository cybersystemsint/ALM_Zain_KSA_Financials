package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Printer;
import com.zain.ksa.alm.financials.repository.PrinterRepository;
import com.zain.ksa.alm.financials.service.PrinterService;

@Service
@Transactional
public class PrinterServiceImpl implements PrinterService {

	private final PrinterRepository printerRepository;

	@Autowired
	public PrinterServiceImpl(PrinterRepository printerRepository) {
		this.printerRepository = printerRepository;
	}

	@Override
	public Printer findByHostSerialNumber(String serialNumber) {
		return printerRepository.findByHostSerialNumber(serialNumber);
	}

	@Override
	public void saveAll(List<Printer> printers) {
		printerRepository.saveAll(printers);
	}

	@Override
	public Page<Printer> findAll(Pageable pageable) {
		return printerRepository.findAll(pageable);
	}
}
