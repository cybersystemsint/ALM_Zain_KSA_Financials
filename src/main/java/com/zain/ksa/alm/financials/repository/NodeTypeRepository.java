package com.zain.ksa.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.NodeType;

@Repository
public interface NodeTypeRepository extends JpaRepository<NodeType, Long> {

	NodeType findById(Integer id);

	NodeType findByNodeType(String nodeType);
}
