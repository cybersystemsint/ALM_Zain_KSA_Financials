package com.zain.ksa.alm.financials.repository;

import com.zain.ksa.alm.financials.entity.UnmappedITInventory;
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
 * Repository for tb_unmapped_IT_Inventory.
 *
 * <p>See {@link UnmappedActiveInventoryRepository} for streaming design notes
 * and MySQL cursor requirements.</p>
 */
@Repository
public interface UnmappedITInventoryRepository
        extends JpaRepository<UnmappedITInventory, Long>,
                JpaSpecificationExecutor<UnmappedITInventory> {

    boolean existsByHostSerialNumber(String hostSerialNumber);

    Page<UnmappedITInventory> findBySiteId(String siteId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM UnmappedITInventory u WHERE u.hostSerialNumber IN :serials")
    int deleteByHostSerialNumberIn(@Param("serials") List<String> serials);

    /**
     * Streams all IT unmapped inventory records using a server-side cursor.
     */
    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT u FROM UnmappedITInventory u")
    Stream<UnmappedITInventory> streamAll();
}