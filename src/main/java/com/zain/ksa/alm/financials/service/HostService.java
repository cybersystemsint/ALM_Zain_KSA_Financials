package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Host;

@Service
public interface HostService {

	Host findByHostSerialNumber(String serialNumber);

	List<Host> findAll();

	void saveAll(List<Host> hosts);

	Page<Host> findAll(Pageable pageable);
}
