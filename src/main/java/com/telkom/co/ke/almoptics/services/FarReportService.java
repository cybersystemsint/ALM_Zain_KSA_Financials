/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
//import java.awt.print.Pageable;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface FarReportService {

    //List<tb_FarReport> findAll();
    tb_FarReport save(tb_FarReport paramtb_FinancialReport);

    List<tb_FarReport> findByAssetId(String paramString);

//    List<tb_FarReport> findByInventoryStatus(String paramString);

    Page<tb_FarReport> findAll(Pageable pageable);

    List<tb_FarReport> findAll(int page, int size);

    void streamExportToCsv(PrintWriter writer, String column, String value, String operator) throws IOException;

     void saveAll(List<tb_FarReport> assets);
}
