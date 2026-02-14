package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Host;
import com.zain.ksa.alm.financials.repository.HostRepository;
import com.zain.ksa.alm.financials.service.HostService;

@Service
@Transactional
public class HostServiceImpl implements HostService {

	private final HostRepository hostRepository;

	@Autowired
	public HostServiceImpl(HostRepository hostRepository) {
		this.hostRepository = hostRepository;
	}

	@Override
	public Host findByHostSerialNumber(String serialNumber) {
		return hostRepository.findByHostSerialNumber(serialNumber);
	}

	@Override
	public List<Host> findAll() {
		return hostRepository.findAll();
	}

	@Override
	public void saveAll(List<Host> hosts) {
		hostRepository.saveAll(hosts);
	}

	@Override
	public Page<Host> findAll(Pageable pageable) {
		return hostRepository.findAll(pageable);
	}
}
