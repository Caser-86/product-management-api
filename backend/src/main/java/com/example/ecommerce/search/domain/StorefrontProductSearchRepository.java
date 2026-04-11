package com.example.ecommerce.search.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorefrontProductSearchRepository extends JpaRepository<StorefrontProductSearchEntity, Long>, StorefrontProductSearchCustomRepository {

    Page<StorefrontProductSearchEntity> findByProductStatusNotAndPublishStatusAndAuditStatus(
        String productStatus,
        String publishStatus,
        String auditStatus,
        Pageable pageable
    );

    Page<StorefrontProductSearchEntity> findByProductStatusNotAndPublishStatusAndAuditStatusAndCategoryId(
        String productStatus,
        String publishStatus,
        String auditStatus,
        Long categoryId,
        Pageable pageable
    );

    Page<StorefrontProductSearchEntity> findByProductStatusNotAndPublishStatusAndAuditStatusAndTitleContainingIgnoreCase(
        String productStatus,
        String publishStatus,
        String auditStatus,
        String title,
        Pageable pageable
    );

    Page<StorefrontProductSearchEntity> findByProductStatusNotAndPublishStatusAndAuditStatusAndTitleContainingIgnoreCaseAndCategoryId(
        String productStatus,
        String publishStatus,
        String auditStatus,
        String title,
        Long categoryId,
        Pageable pageable
    );
}
