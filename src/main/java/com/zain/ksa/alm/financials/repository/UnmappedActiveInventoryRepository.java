package com.zain.ksa.alm.financials.repository;

import com.zain.ksa.alm.financials.entity.UnmappedActiveInventory;
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
 * Repository for tb_unmapped_active_inventory.
 *
 * <h3>Streaming query requirements</h3>
 * <ul>
 *   <li>{@code HINT_FETCH_SIZE} controls how many rows the JDBC driver fetches per
 *       network round-trip. Without this, MySQL Connector/J fetches the <b>entire
 *       ResultSet</b> into client memory before returning the first row.</li>
 *   <li>{@code HINT_READONLY} tells Hibernate to skip dirty-checking on fetched entities,
 *       reducing CPU and memory overhead.</li>
 *   <li>{@code HINT_CACHEABLE = false} prevents the query result from being stored in
 *       Hibernate's L2 query cache (which would defeat streaming).</li>
 * </ul>
 *
 * <h3>MySQL-specific requirement</h3>
 * <p>The JDBC URL <b>must</b> include {@code useCursorFetch=true} for fetch-size hints
 * to take effect. Without it, MySQL ignores the hint and loads all rows at once.
 * Alternatively, set {@code defaultFetchSize} at the DataSource level.</p>
 *
 * <p>The caller <b>must</b> invoke {@code streamAll()} inside a read-only
 * {@code @Transactional} and close the stream via try-with-resources.</p>
 */
@Repository
public interface UnmappedActiveInventoryRepository
        extends JpaRepository<UnmappedActiveInventory, Long>,
                JpaSpecificationExecutor<UnmappedActiveInventory> {

    boolean existsBySerialNumber(String serialNumber);

    Page<UnmappedActiveInventory> findBySiteId(String siteId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM UnmappedActiveInventory u WHERE u.serialNumber IN :serials")
    int deleteBySerialNumberIn(@Param("serials") List<String> serials);

    /**
     * Streams all active unmapped inventory records using a server-side cursor.
     *
     * <p>Fetch size of 500 means the JDBC driver fetches 500 rows per network
     * round-trip. This balances memory (500 × ~2 KB ≈ 1 MB) against network
     * latency (fewer round-trips than row-at-a-time).</p>
     */
    @QueryHints(value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HINT_READONLY,   value = "true"),
        @QueryHint(name = HINT_CACHEABLE,  value = "false")
    })
    @Query("SELECT u FROM UnmappedActiveInventory u")
    Stream<UnmappedActiveInventory> streamAll();
}