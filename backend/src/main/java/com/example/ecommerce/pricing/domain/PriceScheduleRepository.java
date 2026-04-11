package com.example.ecommerce.pricing.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PriceScheduleRepository extends JpaRepository<PriceScheduleEntity, Long> {

    Page<PriceScheduleEntity> findBySkuId(Long skuId, Pageable pageable);

    List<PriceScheduleEntity> findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
        String status,
        LocalDateTime effectiveAt,
        Pageable pageable
    );
}
