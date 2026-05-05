package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Item;
import com.zain.ksa.alm.financials.repository.ItemRepository;
import com.zain.ksa.alm.financials.service.ItemService;

@Service
@Transactional
public class ItemServiceImpl implements ItemService {

	private final ItemRepository itemRepository;

	@Autowired
	public ItemServiceImpl(ItemRepository itemRepository) {
		this.itemRepository = itemRepository;
	}

	@Override
	public List<Item> findAll() {
		return itemRepository.findAll();
	}

	@Override
	public Item findByItemCode(String itemCode) {
		return itemRepository.findByItemCode(itemCode);
	}
}
