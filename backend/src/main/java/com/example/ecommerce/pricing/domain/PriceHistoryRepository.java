package com.example.ecommerce.pricing.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, Long> {
    Page<PriceHistoryEntity> findBySkuId(Long skuId, Pageable pageable);
}
