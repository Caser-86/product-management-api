package com.example.ecommerce.pricing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, Long> {
    List<PriceHistoryEntity> findBySkuIdOrderByIdDesc(Long skuId);
}
