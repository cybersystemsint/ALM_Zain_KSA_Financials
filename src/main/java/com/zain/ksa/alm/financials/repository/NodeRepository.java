package com.zain.ksa.alm.financials.repository;

import java.util.List;

import javax.persistence.QueryHint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.Node;
import javax.persistence.QueryHint;
import java.util.List;
import java.util.stream.Stream;

@Repository
public interface NodeRepository
        extends JpaRepository<Node, Integer>,
                JpaSpecificationExecutor<Node> {

		Node findBySerialNumber(String serialNumber);

	List<Node> findByNodeName(String nodeName);

	@Override
	Page<Node> findAll(Pageable pageable);

    Page<Node> findByIsMapped(Boolean isMapped, Pageable pageable);

    Page<Node> findBySiteId(Integer siteId, Pageable pageable);

    boolean existsBySerialNumber(String serialNumber);

  @QueryHints(value = {
    @QueryHint(name = "org.hibernate.fetchSize", value = "500"),
    @QueryHint(name = "org.hibernate.readOnly",  value = "true")
    })
    @Query("SELECT n FROM Node n WHERE "
         + "(:siteId IS NULL OR n.siteId = :siteId) AND "
         + "(:isMapped IS NULL OR n.isMapped = :isMapped)")
    Stream<Node> streamByFilters(
            @Param("siteId")   Integer siteId,
            @Param("isMapped") Boolean isMapped
    );

    /**
     * Lightweight projection: returns only serial numbers — used by the
     * scheduler to avoid loading full entity graph.
     */
    @Query("SELECT n.serialNumber FROM Node n WHERE n.serialNumber IS NOT NULL")
    Stream<String> streamAllSerialNumbers();

    @Query("SELECT n.serialNumber FROM Node n "
         + "WHERE n.serialNumber IN :serials")
    List<String> findMatchingSerialNumbers(@Param("serials") List<String> serials);
}