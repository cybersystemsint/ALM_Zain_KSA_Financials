package com.zain.ksa.alm.financials.service.impl;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.NodeType;
import com.zain.ksa.alm.financials.repository.NodeTypeRepository;
import com.zain.ksa.alm.financials.service.NodeTypeService;

@Service
@Transactional
public class NodeTypeServiceImpl implements NodeTypeService {

	private final NodeTypeRepository nodeTypeRepository;

	@Autowired
	public NodeTypeServiceImpl(NodeTypeRepository nodeTypeRepository) {
		this.nodeTypeRepository = nodeTypeRepository;
	}

	@Override
	public NodeType findById(Integer id) {
		return nodeTypeRepository.findById(id);
	}

	@Override
	public NodeType findByNodeType(String nodeType) {
		return nodeTypeRepository.findByNodeType(nodeType);
	}
}
