package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.ITInventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ITInventoryRepository extends JpaRepository<ITInventory, Integer> {
       List<ITInventory> findByHostSerialNumberIn(List<String> serialNumbers);
}