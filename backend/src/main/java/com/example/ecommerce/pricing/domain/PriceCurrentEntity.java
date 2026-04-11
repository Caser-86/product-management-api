package com.example.ecommerce.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "price_current")
public class PriceCurrentEntity {

    @Id
    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false)
    private String currency;

    @Column(name = "list_price", nullable = false)
    private BigDecimal listPrice;

    @Column(name = "sale_price", nullable = false)
    private BigDecimal salePrice;

    @Column(name = "cost_price")
    private BigDecimal costPrice;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PriceCurrentEntity() {
    }

    public static PriceCurrentEntity of(Long skuId, Long merchantId, BigDecimal listPrice, BigDecimal salePrice) {
        PriceCurrentEntity entity = new PriceCurrentEntity();
        entity.skuId = skuId;
        entity.merchantId = merchantId;
        entity.currency = "CNY";
        entity.listPrice = listPrice;
        entity.salePrice = salePrice;
        return entity;
    }

    public void updatePrices(BigDecimal listPrice, BigDecimal salePrice) {
        this.listPrice = listPrice;
        this.salePrice = salePrice;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public BigDecimal getListPrice() {
        return listPrice;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }
}
