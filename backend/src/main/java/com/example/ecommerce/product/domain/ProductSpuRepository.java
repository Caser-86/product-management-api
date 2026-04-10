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
}
