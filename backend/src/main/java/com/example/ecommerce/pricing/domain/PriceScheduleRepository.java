package com.example.ecommerce.pricing.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PriceScheduleRepository extends JpaRepository<PriceScheduleEntity, Long> {

    List<PriceScheduleEntity> findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
        String status,
        LocalDateTime effectiveAt,
        Pageable pageable
    );
}
