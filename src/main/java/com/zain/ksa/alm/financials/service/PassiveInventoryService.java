package com.zain.ksa.alm.financials.service;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.request.InventoryFilterRequest;
import com.zain.ksa.alm.financials.dto.response.NodeDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.PassiveInventory;
import com.zain.ksa.alm.financials.dto.response.PassiveInventoryDTO;


@Service
public interface PassiveInventoryService {

	PassiveInventory findBySerialNumber(String serialNumber);

	List<PassiveInventory> findAll();

	void saveAll(List<PassiveInventory> nodes);

	Page<PassiveInventory> findAll(Pageable pageable);


    PagedResponse<PassiveInventoryDTO> findAll(DynamicFilterRequest filter, Pageable pageable);
	
    PassiveInventoryDTO findById(Integer id);

}
