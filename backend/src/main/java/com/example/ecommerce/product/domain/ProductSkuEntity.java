package com.example.ecommerce.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_sku")
public class ProductSkuEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spu_id", nullable = false)
    private ProductSpuEntity spu;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @Column(name = "spec_snapshot", nullable = false, columnDefinition = "json")
    private String specSnapshot;

    @Column(name = "spec_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String specHash;

    @Column(nullable = false)
    private String status;

    @Column(name = "sale_status", nullable = false)
    private String saleStatus;

    protected ProductSkuEntity() {
    }

    public static ProductSkuEntity of(Long merchantId, String skuCode, String specSnapshot, String specHash) {
        ProductSkuEntity entity = new ProductSkuEntity();
        entity.merchantId = merchantId;
        entity.skuCode = skuCode;
        entity.specSnapshot = specSnapshot;
        entity.specHash = specHash;
        entity.status = "active";
        entity.saleStatus = "sellable";
        return entity;
    }

    void attachTo(ProductSpuEntity spu) {
        this.spu = spu;
    }

    public Long getId() {
        return id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public String getSkuCode() {
        return skuCode;
    }
}
