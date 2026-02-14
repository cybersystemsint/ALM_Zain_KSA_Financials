package com.zain.ksa.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.PassiveInventory;

@Repository
public interface PassiveInventoryRepository extends JpaRepository<PassiveInventory, Long> {

	PassiveInventory findBySerialNumber(String serialNumber);

	@Override
	Page<PassiveInventory> findAll(Pageable pageable);
}
