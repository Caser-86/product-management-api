package com.example.ecommerce.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "price_schedule")
public class PriceScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "target_price_json", nullable = false, columnDefinition = "json")
    private String targetPriceJson;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PriceScheduleEntity() {
    }

    public static PriceScheduleEntity pending(
        Long skuId,
        Long merchantId,
        String targetPriceJson,
        LocalDateTime effectiveAt,
        LocalDateTime expireAt
    ) {
        PriceScheduleEntity entity = new PriceScheduleEntity();
        entity.skuId = skuId;
        entity.merchantId = merchantId;
        entity.targetPriceJson = targetPriceJson;
        entity.effectiveAt = effectiveAt;
        entity.expireAt = expireAt;
        entity.status = "pending";
        return entity;
    }

    public Long getId() {
        return id;
    }

    public Long getSkuId() {
        return skuId;
    }

    public String getTargetPriceJson() {
        return targetPriceJson;
    }

    public LocalDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public String getStatus() {
        return status;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    public void markApplied() {
        this.status = "applied";
    }
}
