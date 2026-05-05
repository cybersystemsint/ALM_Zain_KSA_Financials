package com.zain.ksa.alm.financials.repository.custom;

import com.zain.ksa.alm.financials.entity.FarReport;

import javax.persistence.EntityManager;

public class FarReportRepositoryImpl
        extends FilteredStreamSupport<FarReport>
        implements FilteredStreamRepository<FarReport> {

    public FarReportRepositoryImpl(EntityManager entityManager) {
        super(FarReport.class, entityManager);
    }
}