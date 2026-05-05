package com.zain.ksa.alm.financials.repository.custom;

import com.zain.ksa.alm.financials.entity.DepreciationHistory;

import javax.persistence.EntityManager;

public class DepreciationHistoryRepositoryImpl
        extends FilteredStreamSupport<DepreciationHistory>
        implements FilteredStreamRepository<DepreciationHistory> {

    public DepreciationHistoryRepositoryImpl(EntityManager entityManager) {
        super(DepreciationHistory.class, entityManager);
    }
}