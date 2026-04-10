package com.example.ecommerce.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductWorkflowHistoryRepository extends JpaRepository<ProductWorkflowHistoryEntity, Long> {

    List<ProductWorkflowHistoryEntity> findByProductIdOrderByCreatedAtDescIdDesc(Long productId);
}
