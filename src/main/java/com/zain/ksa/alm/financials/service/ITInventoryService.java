package com.zain.ksa.alm.financials.service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.ITInventoryDTO;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;

import javax.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Pageable;

public interface ITInventoryService {


    PagedResponse<ITInventoryDTO> findAll(DynamicFilterRequest filter, Pageable pageable);

    ITInventoryDTO findById(Long id);
}