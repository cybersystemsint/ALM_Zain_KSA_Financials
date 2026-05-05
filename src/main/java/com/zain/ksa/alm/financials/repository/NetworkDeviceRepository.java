package com.zain.ksa.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.NetworkDevice;

@Repository
public interface NetworkDeviceRepository extends JpaRepository<NetworkDevice, Long> {

	NetworkDevice findBySerialNumber(String serialNumber);
}
