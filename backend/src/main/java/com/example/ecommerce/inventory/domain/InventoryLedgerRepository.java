package com.example.ecommerce.inventory.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntity, Long> {

    Page<InventoryLedgerEntity> findBySkuId(Long skuId, Pageable pageable);
}
