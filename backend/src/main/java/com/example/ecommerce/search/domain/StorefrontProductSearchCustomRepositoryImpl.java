package com.example.ecommerce.search.domain;

import com.example.ecommerce.search.application.StorefrontSearchSort;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
public class StorefrontProductSearchCustomRepositoryImpl implements StorefrontProductSearchCustomRepository {

    private static final String DELETED_STATUS = "deleted";
    private static final String PUBLISHED_STATUS = "published";
    private static final String APPROVED_STATUS = "approved";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<StorefrontProductSearchEntity> searchVisibleProducts(
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStockOnly,
        StorefrontSearchSort sort,
        Pageable pageable
    ) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<StorefrontProductSearchEntity> query = criteriaBuilder.createQuery(StorefrontProductSearchEntity.class);
        Root<StorefrontProductSearchEntity> root = query.from(StorefrontProductSearchEntity.class);
        query.select(root)
            .where(predicates(criteriaBuilder, root, keyword, categoryId, minPrice, maxPrice, inStockOnly).toArray(Predicate[]::new))
            .orderBy(toOrders(sort.toSort(), criteriaBuilder, root));

        TypedQuery<StorefrontProductSearchEntity> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<StorefrontProductSearchEntity> countRoot = countQuery.from(StorefrontProductSearchEntity.class);
        countQuery.select(criteriaBuilder.count(countRoot))
            .where(predicates(criteriaBuilder, countRoot, keyword, categoryId, minPrice, maxPrice, inStockOnly).toArray(Predicate[]::new));

        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private List<Predicate> predicates(
        CriteriaBuilder criteriaBuilder,
        Root<StorefrontProductSearchEntity> root,
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStockOnly
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.notEqual(root.get("productStatus"), DELETED_STATUS));
        predicates.add(criteriaBuilder.equal(root.get("publishStatus"), PUBLISHED_STATUS));
        predicates.add(criteriaBuilder.equal(root.get("auditStatus"), APPROVED_STATUS));

        if (keyword != null && !keyword.isBlank()) {
            predicates.add(criteriaBuilder.like(
                criteriaBuilder.lower(root.get("title")),
                "%" + keyword.trim().toLowerCase() + "%"
            ));
        }
        if (categoryId != null) {
            predicates.add(criteriaBuilder.equal(root.get("categoryId"), categoryId));
        }
        if (minPrice != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("maxPrice"), minPrice));
        }
        if (maxPrice != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("minPrice"), maxPrice));
        }
        if (Boolean.TRUE.equals(inStockOnly)) {
            predicates.add(criteriaBuilder.greaterThan(root.get("availableQty"), 0));
        }
        return predicates;
    }

    private List<Order> toOrders(Sort sort, CriteriaBuilder criteriaBuilder, Root<StorefrontProductSearchEntity> root) {
        return sort.stream()
            .map(order -> order.isAscending()
                ? criteriaBuilder.asc(root.get(order.getProperty()))
                : criteriaBuilder.desc(root.get(order.getProperty())))
            .toList();
    }
}
