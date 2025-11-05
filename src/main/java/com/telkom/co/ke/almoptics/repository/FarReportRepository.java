package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.stream.Stream;

public interface FarReportRepository extends JpaRepository<tb_FarReport, Integer> {

    @Query("SELECT r FROM tb_FarReport r WHERE " +
            "(:search IS NULL OR LOWER(r.category) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(r.assetId) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%'))) ")
    Stream<tb_FarReport> streamAll(@Param("search") String search);

    @Query("SELECT f FROM tb_FarReport f WHERE " +
            "(:searchQuery IS NULL OR LOWER(f.book) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
            "OR LOWER(f.description) LIKE LOWER(CONCAT('%', :searchQuery, '%')))")
    List<tb_FarReport> findBySearchQuery(String searchQuery, Pageable pageable);



}
