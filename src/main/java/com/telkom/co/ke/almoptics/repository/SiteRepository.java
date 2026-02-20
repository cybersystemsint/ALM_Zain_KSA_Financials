package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.Site;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Integer> {
    Site findByRecordNo(Integer recordNo);
}

