package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.util.stream.Stream;

@Repository
public interface FarReportRepository extends JpaRepository<tb_FarReport, Long>, JpaSpecificationExecutor<tb_FarReport> {

    @QueryHints(@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE, value = "1000"))
    @Query("SELECT r FROM tb_FarReport r")
    Stream<tb_FarReport> streamAll();
}
