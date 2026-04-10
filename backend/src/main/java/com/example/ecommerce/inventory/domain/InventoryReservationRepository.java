package com.example.ecommerce.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, String> {
}
