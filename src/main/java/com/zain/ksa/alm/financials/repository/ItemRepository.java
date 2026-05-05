package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.Item;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
	List<Item> findAll();

	Item findByItemCode(String paramString);
}
