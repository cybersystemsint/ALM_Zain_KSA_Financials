package com.zain.ksa.alm.financials.repository.custom;

import com.zain.ksa.alm.financials.entity.UnmappedActiveInventory;

import javax.persistence.EntityManager;

public class UnmappedActiveInventoryRepositoryImpl
        extends FilteredStreamSupport<UnmappedActiveInventory>
        implements FilteredStreamRepository<UnmappedActiveInventory> {

    public UnmappedActiveInventoryRepositoryImpl(EntityManager entityManager) {
        super(UnmappedActiveInventory.class, entityManager);
    }
}