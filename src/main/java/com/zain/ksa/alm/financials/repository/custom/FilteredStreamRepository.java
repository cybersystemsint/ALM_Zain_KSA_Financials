package com.zain.ksa.alm.financials.repository.custom;

import org.springframework.data.jpa.domain.Specification;

import java.util.stream.Stream;

/**
 * Custom repository fragment that adds filtered streaming support.
 *
 * <p>Spring Data JPA's JpaSpecificationExecutor does not support Stream returns,
 * so we implement it manually via EntityManager + CriteriaQuery.</p>
 *
 * <p>Each repository that extends this fragment needs a corresponding Impl class
 * named {@code <RepositoryName>Impl} that extends {@link FilteredStreamSupport}.</p>
 */
public interface FilteredStreamRepository<T> {

    /**
     * Streams entities matching the given specification using a server-side cursor.
     * Caller MUST use try-with-resources inside a @Transactional(readOnly = true).
     */
    Stream<T> streamAll(Specification<T> spec);
}