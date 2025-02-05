/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.repository.FarReportRepo;
import com.telkom.co.ke.almoptics.services.FarReportService;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
@Transactional
public class FarReportServiceImpl implements FarReportService {

    @Autowired
    private FarReportRepo farRepo;

    public List<tb_FarReport> findAll() {
        return this.farRepo.findAll();

    }

    public tb_FarReport save(tb_FarReport boards) {
        return (tb_FarReport) this.farRepo.save(boards);
    }

    public List<tb_FarReport> findByAssetId(String paramString) {
        return this.farRepo.findByAssetId(paramString);

    }
}
