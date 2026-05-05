package com.zain.ksa.alm.financials.repository.custom;

import com.zain.ksa.alm.financials.entity.UnmappedPassiveInventory;

import javax.persistence.EntityManager;

public class UnmappedPassiveInventoryRepositoryImpl
        extends FilteredStreamSupport<UnmappedPassiveInventory>
        implements FilteredStreamRepository<UnmappedPassiveInventory> {

    public UnmappedPassiveInventoryRepositoryImpl(EntityManager entityManager) {
        super(UnmappedPassiveInventory.class, entityManager);
    }
}