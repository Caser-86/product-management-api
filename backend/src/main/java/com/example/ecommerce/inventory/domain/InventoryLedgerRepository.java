package com.example.ecommerce.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntity, Long> {

    List<InventoryLedgerEntity> findBySkuIdOrderByIdDesc(Long skuId);
}
