package com.example.ecommerce.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSkuRepository extends JpaRepository<ProductSkuEntity, Long> {
}
