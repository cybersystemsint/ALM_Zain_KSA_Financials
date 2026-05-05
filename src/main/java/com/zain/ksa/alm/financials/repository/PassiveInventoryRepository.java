package com.zain.ksa.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.PassiveInventory;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.util.List;
import java.util.stream.Stream;

@Repository
public interface PassiveInventoryRepository
        extends JpaRepository<PassiveInventory, Integer>,
                JpaSpecificationExecutor<PassiveInventory> {
	
    PassiveInventory findBySerialNumber(String serialNumber);

	@Override
	Page<PassiveInventory> findAll(Pageable pageable);

    Page<PassiveInventory> findBySiteId(String siteId, Pageable pageable);

    Page<PassiveInventory> findByIsMapped(Boolean isMapped, Pageable pageable);

    boolean existsBySerialNumber(String serialNumber);

  @QueryHints(value = {
    @QueryHint(name = "org.hibernate.fetchSize", value = "500"),
    @QueryHint(name = "org.hibernate.readOnly",  value = "true")
    })
    @Query("SELECT p FROM PassiveInventory p WHERE "
         + "(:siteId IS NULL OR p.siteId = :siteId) AND "
         + "(:isMapped IS NULL OR p.isMapped = :isMapped)")
    Stream<PassiveInventory> streamByFilters(
            @Param("siteId")   String siteId,
            @Param("isMapped") Boolean isMapped
    );

    @Query("SELECT p.serialNumber FROM PassiveInventory p WHERE p.serialNumber IS NOT NULL")
    Stream<String> streamAllSerialNumbers();

    @Query("SELECT p.serialNumber FROM PassiveInventory p "
         + "WHERE p.serialNumber IN :serials")
    List<String> findMatchingSerialNumbers(@Param("serials") List<String> serials);
}