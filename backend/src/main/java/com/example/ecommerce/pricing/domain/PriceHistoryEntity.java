package com.example.ecommerce.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected PriceHistoryEntity() {
    }

    public static PriceHistoryEntity manual(Long skuId, Double listPrice, Double salePrice, String reason, Long operatorId) {
        PriceHistoryEntity entity = new PriceHistoryEntity();
        entity.skuId = skuId;
        entity.merchantId = 2001L;
        entity.changeType = "manual";
        entity.oldPriceJson = "{}";
        entity.newPriceJson = "{\"listPrice\":" + listPrice + ",\"salePrice\":" + salePrice + "}";
        entity.reason = reason;
        entity.operatorId = operatorId;
        return entity;
    }

    public String getReason() {
        return reason;
    }
}
