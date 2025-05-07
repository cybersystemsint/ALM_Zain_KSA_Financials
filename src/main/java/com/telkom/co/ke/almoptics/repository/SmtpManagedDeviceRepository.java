/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tbPrinter;
import com.telkom.co.ke.almoptics.entities.tbSNMPManagedDevice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author jgithu
 */
@Repository
public interface SmtpManagedDeviceRepository extends JpaRepository<tbSNMPManagedDevice, Long> {

    tbSNMPManagedDevice findByHostSerialNumber(String hostSerialNumber);

    @Override
    Page<tbSNMPManagedDevice> findAll(Pageable pageable);
}
