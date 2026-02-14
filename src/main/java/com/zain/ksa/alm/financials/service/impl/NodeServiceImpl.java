package com.zain.ksa.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Node;
import com.zain.ksa.alm.financials.repository.NodeRepository;
import com.zain.ksa.alm.financials.service.NodeService;

@Service
@Transactional
public class NodeServiceImpl implements NodeService {

	private final NodeRepository nodeRepository;

	@Autowired
	public NodeServiceImpl(NodeRepository nodeRepository) {
		this.nodeRepository = nodeRepository;
	}

	@Override
	public Node findBySerialNumber(String serialNumber) {
		return nodeRepository.findBySerialNumber(serialNumber);
	}

	@Override
	public List<Node> findAll() {
		return nodeRepository.findAll();
	}

	@Override
	public void saveAll(List<Node> nodes) {
		nodeRepository.saveAll(nodes);
	}

	@Override
	public List<Node> findByNode(String nodeName) {
		return nodeRepository.findByNode(nodeName);
	}

	@Override
	public Node save(Node node) {
		return nodeRepository.save(node);
	}

	@Override
	public Page<Node> findAll(Pageable pageable) {
		return nodeRepository.findAll(pageable);
	}
}
