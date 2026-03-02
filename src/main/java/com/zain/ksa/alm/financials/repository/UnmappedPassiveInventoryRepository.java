package com.zain.ksa.alm.financials.repository;

import com.zain.ksa.alm.financials.entity.UnmappedPassiveInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.util.List;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_CACHEABLE;
import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

/**
 * Repository for tb_unmapped_passive_inventory.
 *
 * <p>See {@link UnmappedActiveInventoryRepository} for streaming design notes
 * and MySQL cursor requirements.</p>
 */
@Repository
public interface UnmappedPassiveInventoryRepository
        extends JpaRepository<UnmappedPassiveInventory, Long>,
                JpaSpecificationExecutor<UnmappedPassiveInventory> {

    boolean existsBySerialNumber(String serialNumber);

    Page<UnmappedPassiveInventory> findBySiteId(String siteId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM UnmappedPassiveInventory u WHERE u.serialNumber IN :serials")
    int deleteBySerialNumberIn(@Param("serials") List<String> serials);

    /**
     * Streams all passive unmapped inventory records using a server-side cursor.
     */
    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT u FROM UnmappedPassiveInventory u")
    Stream<UnmappedPassiveInventory> streamAll();
}