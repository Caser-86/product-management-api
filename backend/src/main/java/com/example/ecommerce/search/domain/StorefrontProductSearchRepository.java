package com.example.ecommerce.search.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorefrontProductSearchRepository extends JpaRepository<StorefrontProductSearchEntity, Long> {

    Page<StorefrontProductSearchEntity> findByProductStatusNot(String productStatus, Pageable pageable);

    Page<StorefrontProductSearchEntity> findByProductStatusNotAndCategoryId(
        String productStatus,
        Long categoryId,
        Pageable pageable
    );

    Page<StorefrontProductSearchEntity> findByProductStatusNotAndTitleContainingIgnoreCase(
        String productStatus,
        String title,
        Pageable pageable
    );

    Page<StorefrontProductSearchEntity> findByProductStatusNotAndTitleContainingIgnoreCaseAndCategoryId(
        String productStatus,
        String title,
        Long categoryId,
        Pageable pageable
    );
}
