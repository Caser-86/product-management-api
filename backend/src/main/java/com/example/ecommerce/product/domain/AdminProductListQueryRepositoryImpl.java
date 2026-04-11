package com.example.ecommerce.product.domain;

import com.example.ecommerce.product.application.AdminProductListSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class AdminProductListQueryRepositoryImpl implements AdminProductListQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<ProductSpuEntity> searchAdminProducts(
        Long merchantId,
        String status,
        String auditStatus,
        String publishStatus,
        String keyword,
        AdminProductListSort sort,
        Pageable pageable
    ) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<ProductSpuEntity> query = criteriaBuilder.createQuery(ProductSpuEntity.class);
        Root<ProductSpuEntity> root = query.from(ProductSpuEntity.class);
        query.select(root)
            .where(predicates(criteriaBuilder, root, merchantId, status, auditStatus, publishStatus, keyword).toArray(Predicate[]::new))
            .orderBy(toOrders(sort.toSort(), criteriaBuilder, root));

        TypedQuery<ProductSpuEntity> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<ProductSpuEntity> countRoot = countQuery.from(ProductSpuEntity.class);
        countQuery.select(criteriaBuilder.count(countRoot))
            .where(predicates(criteriaBuilder, countRoot, merchantId, status, auditStatus, publishStatus, keyword).toArray(Predicate[]::new));

        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private List<Predicate> predicates(
        CriteriaBuilder criteriaBuilder,
        Root<ProductSpuEntity> root,
        Long merchantId,
        String status,
        String auditStatus,
        String publishStatus,
        String keyword
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.notEqual(root.get("status"), "deleted"));

        if (merchantId != null) {
            predicates.add(criteriaBuilder.equal(root.get("merchantId"), merchantId));
        }
        if (status != null && !status.isBlank()) {
            predicates.add(criteriaBuilder.equal(root.get("status"), status));
        }
        if (auditStatus != null && !auditStatus.isBlank()) {
            predicates.add(criteriaBuilder.equal(root.get("auditStatus"), auditStatus));
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            predicates.add(criteriaBuilder.equal(root.get("publishStatus"), publishStatus));
        }
        if (keyword != null && !keyword.isBlank()) {
            predicates.add(criteriaBuilder.like(
                criteriaBuilder.lower(root.get("title")),
                "%" + keyword.trim().toLowerCase() + "%"
            ));
        }
        return predicates;
    }

    private List<Order> toOrders(Sort sort, CriteriaBuilder criteriaBuilder, Root<ProductSpuEntity> root) {
        return sort.stream()
            .map(order -> order.isAscending()
                ? criteriaBuilder.asc(root.get(order.getProperty()))
                : criteriaBuilder.desc(root.get(order.getProperty())))
            .toList();
    }
}
