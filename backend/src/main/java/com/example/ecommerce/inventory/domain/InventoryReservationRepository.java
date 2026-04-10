package com.example.ecommerce.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, String> {

    Optional<InventoryReservationEntity> findByBizId(String bizId);
}
