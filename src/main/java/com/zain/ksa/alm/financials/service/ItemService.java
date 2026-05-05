package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Item;

@Service
public interface ItemService {

	List<Item> findAll();

	Item findByItemCode(String paramString);
}
