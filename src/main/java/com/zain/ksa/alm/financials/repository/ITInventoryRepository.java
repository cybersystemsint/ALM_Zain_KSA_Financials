package com.zain.ksa.alm.financials.repository;

import com.zain.ksa.alm.financials.entity.ITInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.util.stream.Stream;

@Repository
public interface ITInventoryRepository
        extends JpaRepository<ITInventory, Long>,
                JpaSpecificationExecutor<ITInventory> {

    Page<ITInventory> findBySiteId(String siteId, Pageable pageable);

    boolean existsByHostSerialNumber(String hostSerialNumber);

    @QueryHints(value = {
        @QueryHint(name = "org.hibernate.fetchSize", value = "500"),
        @QueryHint(name = "org.hibernate.readOnly",  value = "true")
    })
    @Query("SELECT i FROM ITInventory i WHERE " +
           "(:siteId IS NULL OR i.siteId = :siteId)")
    Stream<ITInventory> streamByFilters(@Param("siteId") String siteId);

    @QueryHints(value = {
        @QueryHint(name = "org.hibernate.fetchSize", value = "500"),
        @QueryHint(name = "org.hibernate.readOnly",  value = "true")
    })
    @Query("SELECT i FROM ITInventory i")
    Stream<ITInventory> streamAll();
}