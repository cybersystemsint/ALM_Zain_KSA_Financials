package com.zain.ksa.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.Storage;

@Repository
public interface StorageRepository extends JpaRepository<Storage, Long> {

	Storage findByHostSerialNumber(String hostSerialNumber);

}
