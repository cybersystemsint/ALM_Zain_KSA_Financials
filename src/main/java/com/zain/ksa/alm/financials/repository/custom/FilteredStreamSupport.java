package com.zain.ksa.alm.financials.repository.custom;

import org.springframework.data.jpa.domain.Specification;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_CACHEABLE;
import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

/**
 * Reusable base class for implementing {@link FilteredStreamRepository}.
 *
 * <p><b>Spring Data custom fragment naming convention:</b> For a repository
 * interface named {@code FooRepository} that extends {@code FilteredStreamRepository},
 * Spring looks for a class named {@code FooRepositoryImpl}. That class should
 * extend this base and pass the entity class to the constructor.</p>
 *
 * <p>Example:</p>
 * <pre>
 * public class DepreciationHistoryRepositoryImpl
 *         extends FilteredStreamSupport&lt;DepreciationHistory&gt;
 *         implements FilteredStreamRepository&lt;DepreciationHistory&gt; {
 *
 *     public DepreciationHistoryRepositoryImpl(EntityManager em) {
 *         super(DepreciationHistory.class, em);
 *     }
 * }
 * </pre>
 */
public abstract class FilteredStreamSupport<T> implements FilteredStreamRepository<T> {

    private final Class<T> entityClass;
    private final EntityManager entityManager;

    protected FilteredStreamSupport(Class<T> entityClass, EntityManager entityManager) {
        this.entityClass = entityClass;
        this.entityManager = entityManager;
    }

    @Override
    public Stream<T> streamAll(Specification<T> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);

        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, cq, cb);
            if (predicate != null) {
                cq.where(predicate);
            }
        }

        TypedQuery<T> query = entityManager.createQuery(cq);
        query.setHint(HINT_FETCH_SIZE, "500");
        query.setHint(HINT_READONLY, "true");
        query.setHint(HINT_CACHEABLE, "false");

        return query.getResultStream();
    }
}