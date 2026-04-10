package com.example.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_ledger")
public class InventoryLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "biz_type", nullable = false)
    private String bizType;

    @Column(name = "biz_id", nullable = false)
    private String bizId;

    @Column(name = "delta_available", nullable = false)
    private int deltaAvailable;

    @Column(name = "delta_reserved", nullable = false)
    private int deltaReserved;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected InventoryLedgerEntity() {
    }

    public static InventoryLedgerEntity of(
        Long skuId,
        Long merchantId,
        String bizType,
        String bizId,
        int deltaAvailable,
        int deltaReserved
    ) {
        InventoryLedgerEntity entity = new InventoryLedgerEntity();
        entity.skuId = skuId;
        entity.merchantId = merchantId;
        entity.bizType = bizType;
        entity.bizId = bizId;
        entity.deltaAvailable = deltaAvailable;
        entity.deltaReserved = deltaReserved;
        return entity;
    }

    public String getBizType() {
        return bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public int getDeltaAvailable() {
        return deltaAvailable;
    }

    public int getDeltaReserved() {
        return deltaReserved;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
