package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.PassiveInventory;
import com.zain.ksa.alm.financials.repository.PassiveInventoryRepository;
import com.zain.ksa.alm.financials.service.PassiveInventoryService;

@Service
@Transactional
public class PassiveInventoryServiceImpl implements PassiveInventoryService {

	private final PassiveInventoryRepository passiveInventoryRepository;

	@Autowired
	public PassiveInventoryServiceImpl(PassiveInventoryRepository passiveInventoryRepository) {
		this.passiveInventoryRepository = passiveInventoryRepository;
	}

	@Override
	public PassiveInventory findBySerialNumber(String serialNumber) {
		return passiveInventoryRepository.findBySerialNumber(serialNumber);
	}

	@Override
	public List<PassiveInventory> findAll() {
		return passiveInventoryRepository.findAll();
	}

	@Override
	public void saveAll(List<PassiveInventory> passiveInventories) {
		passiveInventoryRepository.saveAll(passiveInventories);
	}

	@Override
	public Page<PassiveInventory> findAll(Pageable pageable) {
		return passiveInventoryRepository.findAll(pageable);
	}
}
