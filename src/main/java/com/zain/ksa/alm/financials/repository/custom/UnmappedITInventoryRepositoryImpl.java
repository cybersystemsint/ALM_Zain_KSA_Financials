package com.zain.ksa.alm.financials.repository.custom;

import com.zain.ksa.alm.financials.entity.UnmappedITInventory;

import javax.persistence.EntityManager;

public class UnmappedITInventoryRepositoryImpl
        extends FilteredStreamSupport<UnmappedITInventory>
        implements FilteredStreamRepository<UnmappedITInventory> {

    public UnmappedITInventoryRepositoryImpl(EntityManager entityManager) {
        super(UnmappedITInventory.class, entityManager);
    }
}