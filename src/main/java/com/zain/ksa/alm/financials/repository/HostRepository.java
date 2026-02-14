package com.zain.ksa.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.Host;

@Repository
public interface HostRepository extends JpaRepository<Host, Long> {

	Host findByHostSerialNumber(String hostSerialNumber);

	@Override
	Page<Host> findAll(Pageable pageable);

}
