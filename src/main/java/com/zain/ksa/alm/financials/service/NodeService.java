
package com.zain.ksa.alm.financials.service;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.financials.dto.request.DynamicFilterRequest;
import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import com.zain.ksa.alm.financials.dto.response.PagedResponse;
import com.zain.ksa.alm.financials.entity.Node;
import com.zain.ksa.alm.financials.dto.response.NodeDTO;


@Service
public interface NodeService {

	Node findBySerialNumber(String serialNumber);

	List<Node> findAll();

	void saveAll(List<Node> nodes);

	List<Node> findByNodeName(String nodeName);

	Node save(Node param);

	Page<Node> findAll(Pageable pageable);

    PagedResponse<NodeDTO> findAll(DynamicFilterRequest filter, Pageable pageable);

    NodeDTO findById(Integer id);



}
