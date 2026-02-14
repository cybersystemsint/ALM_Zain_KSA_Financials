package com.zain.ksa.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.Printer;

@Repository
public interface PrinterRepository extends JpaRepository<Printer, Long> {

	Printer findByHostSerialNumber(String hostSerialNumber);

	@Override
	Page<Printer> findAll(Pageable pageable);
}
