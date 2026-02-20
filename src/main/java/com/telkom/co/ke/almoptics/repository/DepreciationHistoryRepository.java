package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.DepreciationHistory;
import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;

import javax.transaction.Transactional;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DepreciationHistoryRepository extends JpaRepository<DepreciationHistory, Integer>, JpaSpecificationExecutor<DepreciationHistory> {

@Query("""
    SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END
    FROM DepreciationHistory d
    WHERE FUNCTION('MONTH', d.recordDatetime) = :month
      AND FUNCTION('YEAR', d.recordDatetime) = :year
""")
boolean existsForMonth(@Param("month") int month, @Param("year") int year);

        @Modifying
    @Transactional
    @Query("DELETE FROM DepreciationHistory d WHERE FUNCTION('MONTH', d.recordDatetime) = :month AND FUNCTION('YEAR', d.recordDatetime) = :year")
    void deleteByMonthYear(@Param("month") int month, @Param("year") int year);
}
