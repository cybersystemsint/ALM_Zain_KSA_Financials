package com.telkom.co.ke.almoptics.repository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedITInventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UnmappedITInventoryRepository extends JpaRepository<UnmappedITInventory, Long>, JpaSpecificationExecutor<UnmappedITInventory> {
      List<UnmappedITInventory> findByHostSerialNumberIn(Collection<String> serialNumbers);
 



    @Query(
        value = """
            SELECT * FROM tb_unmapped_IT_Inventory
            ORDER BY id
            LIMIT 1 OFFSET :offset
        """,
        nativeQuery = true
    )
    Optional<UnmappedITInventory> findNthRecord(@Param("offset") int offset);
    
}