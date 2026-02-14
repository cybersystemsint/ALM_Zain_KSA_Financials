package com.zain.ksa.alm.financials.repository;

import java.util.List;
import java.util.stream.Stream;

import javax.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.financials.entity.FarReport;

@Repository
public interface FarReportRepository extends JpaRepository<FarReport, Long>, JpaSpecificationExecutor<FarReport> {

	List<FarReport> findByAssetId(String assetId);

	@QueryHints(@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE, value = "1000"))
	@Query("SELECT r FROM FarReport r")
	Stream<FarReport> streamAll();
}
