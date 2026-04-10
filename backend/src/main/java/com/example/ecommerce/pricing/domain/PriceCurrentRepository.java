package com.example.ecommerce.pricing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceCurrentRepository extends JpaRepository<PriceCurrentEntity, Long> {
}
