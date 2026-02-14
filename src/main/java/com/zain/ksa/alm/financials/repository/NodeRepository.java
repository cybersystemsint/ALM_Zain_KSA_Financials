package com.zain.ksa.alm.financials.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.Node;

@Repository
public interface NodeRepository extends JpaRepository<Node, Long> {

	Node findBySerialNumber(String serialNumber);

	List<Node> findByNode(String paramString);

	@Override
	Page<Node> findAll(Pageable pageable);

}
