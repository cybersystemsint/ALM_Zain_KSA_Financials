package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FarReportRepository extends JpaRepository<tb_FarReport, Integer> {
}