package com.example.ecommerce.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_history")
public class PriceHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "change_type", nullable = false)
    private String changeType;

    @Column(name = "old_price_json", nullable = false, columnDefinition = "json")
    private String oldPriceJson;

    @Column(name = "new_price_json", nullable = false, columnDefinition = "json")
    private String newPriceJson;

    @Column
    private String reason;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PriceHistoryEntity() {
    }

    public static PriceHistoryEntity manual(
        Long skuId,
        Long merchantId,
        String oldPriceJson,
        BigDecimal listPrice,
        BigDecimal salePrice,
        String reason,
        Long operatorId
    ) {
        PriceHistoryEntity entity = new PriceHistoryEntity();
        entity.skuId = skuId;
        entity.merchantId = merchantId;
        entity.changeType = "manual";
        entity.oldPriceJson = oldPriceJson;
        entity.newPriceJson = priceJson(listPrice, salePrice);
        entity.reason = reason;
        entity.operatorId = operatorId;
        return entity;
    }

    public static PriceHistoryEntity scheduled(
        Long skuId,
        Long merchantId,
        String oldPriceJson,
        BigDecimal listPrice,
        BigDecimal salePrice,
        String reason,
        Long operatorId
    ) {
        PriceHistoryEntity entity = manual(skuId, merchantId, oldPriceJson, listPrice, salePrice, reason, operatorId);
        entity.changeType = "scheduled";
        return entity;
    }

    public static String priceJson(BigDecimal listPrice, BigDecimal salePrice) {
        return "{\"listPrice\":" + listPrice.toPlainString() + ",\"salePrice\":" + salePrice.toPlainString() + "}";
    }

    public String getChangeType() {
        return changeType;
    }

    public String getOldPriceJson() {
        return oldPriceJson;
    }

    public String getNewPriceJson() {
        return newPriceJson;
    }

    public String getReason() {
        return reason;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
