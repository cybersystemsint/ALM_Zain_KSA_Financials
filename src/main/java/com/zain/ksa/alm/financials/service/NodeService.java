
package com.zain.ksa.alm.financials.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.entity.Node;

@Service
public interface NodeService {

	Node findBySerialNumber(String serialNumber);

	List<Node> findAll();

	void saveAll(List<Node> nodes);

	List<Node> findByNode(String paramString);

	Node save(Node param);

	Page<Node> findAll(Pageable pageable);

}
