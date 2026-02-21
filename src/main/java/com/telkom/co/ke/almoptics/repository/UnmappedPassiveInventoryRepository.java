package com.telkom.co.ke.almoptics.repository;


import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.telkom.co.ke.almoptics.entities.UnmappedPassiveInventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UnmappedPassiveInventoryRepository extends JpaRepository<UnmappedPassiveInventory, Long>, JpaSpecificationExecutor<UnmappedPassiveInventory> {
       List<UnmappedPassiveInventory> findBySerialNumberIn(Collection<String> serialNumbers);

     @Query(
        value = """
            SELECT * FROM tb_unmapped_passive_inventory
            ORDER BY id
            LIMIT 1 OFFSET :offset
        """,
        nativeQuery = true
    )
    Optional<UnmappedPassiveInventory> findNthRecord(@Param("offset") int offset);
}