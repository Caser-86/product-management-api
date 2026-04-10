package com.example.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservationEntity {

    @Id
    private String id;

    @Column(name = "biz_id", nullable = false, unique = true)
    private String bizId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected InventoryReservationEntity() {
    }

    public static InventoryReservationEntity reserved(String id, String bizId, Long skuId, int quantity) {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.id = id;
        entity.bizId = bizId;
        entity.skuId = skuId;
        entity.quantity = quantity;
        entity.status = "reserved";
        entity.expiresAt = LocalDateTime.now().plusMinutes(30);
        return entity;
    }

    public Long getSkuId() {
        return skuId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void confirm() {
        this.status = "confirmed";
    }
}
