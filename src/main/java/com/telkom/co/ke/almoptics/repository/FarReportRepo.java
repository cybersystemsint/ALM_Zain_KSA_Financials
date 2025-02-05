/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author jgithu
 */
@Repository
public interface FarReportRepo extends JpaRepository<tb_FarReport, Long> {

    @Override
    List<tb_FarReport> findAll();

    // tb_FinancialReport findBySerialNumber(String paramString);
    List<tb_FarReport> findByAssetId(String paramString);

}
