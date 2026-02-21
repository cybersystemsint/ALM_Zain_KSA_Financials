package com.telkom.co.ke.almoptics.repository;


import com.telkom.co.ke.almoptics.entities.InventoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTypeRepository extends JpaRepository<InventoryType, Integer> {
    InventoryType findByRecordNo(int recordNo);
}
