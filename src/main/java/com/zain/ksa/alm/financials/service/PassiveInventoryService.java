package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.PassiveInventory;

@Service
public interface PassiveInventoryService {

	PassiveInventory findBySerialNumber(String serialNumber);

	List<PassiveInventory> findAll();

	void saveAll(List<PassiveInventory> nodes);

	Page<PassiveInventory> findAll(Pageable pageable);

}
