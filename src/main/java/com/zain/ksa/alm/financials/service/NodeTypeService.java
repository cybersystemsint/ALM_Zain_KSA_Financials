package com.zain.ksa.alm.financials.service;

import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.NodeType;

@Service
public interface NodeTypeService {

	NodeType findById(Integer id);

	NodeType findByNodeType(String nodeType);

}
