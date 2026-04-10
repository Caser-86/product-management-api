package com.example.ecommerce.product.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSpuRepository extends JpaRepository<ProductSpuEntity, Long> {

    @EntityGraph(attributePaths = "skus")
    Optional<ProductSpuEntity> findWithSkusById(Long id);

    Page<ProductSpuEntity> findByMerchantId(Long merchantId, Pageable pageable);

    Page<ProductSpuEntity> findByMerchantIdAndStatusNot(Long merchantId, String status, Pageable pageable);

    Page<ProductSpuEntity> findByStatusNot(String status, Pageable pageable);

    Page<ProductSpuEntity> findByStatusNotAndCategoryId(String status, Long categoryId, Pageable pageable);

    Page<ProductSpuEntity> findByStatusNotAndTitleContainingIgnoreCase(String status, String title, Pageable pageable);

    Page<ProductSpuEntity> findByStatusNotAndTitleContainingIgnoreCaseAndCategoryId(
        String status,
        String title,
        Long categoryId,
        Pageable pageable
    );
}
