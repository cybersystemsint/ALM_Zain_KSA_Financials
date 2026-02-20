package com.telkom.co.ke.almoptics.repository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UnmappedActiveInventoryRepository extends JpaRepository<UnmappedActiveInventory, Integer>, JpaSpecificationExecutor<UnmappedActiveInventory> {

      List<UnmappedActiveInventory> findBySerialNumberIn(Collection<String> serialNumbers);
      


    @Query(
        value = """
            SELECT * FROM tb_unmapped_active_inventory
            ORDER BY recordNo
            LIMIT 1 OFFSET :offset
        """,
        nativeQuery = true
    )
    Optional<UnmappedActiveInventory> findNthRecord(@Param("offset") int offset);
    
}
