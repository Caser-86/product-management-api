package com.example.ecommerce.product.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_spu")
public class ProductSpuEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "spu_code", nullable = false)
    private String spuCode;

    @Column(nullable = false)
    private String title;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String status;

    @Column(name = "publish_status", nullable = false)
    private String publishStatus;

    @Column(name = "audit_status", nullable = false)
    private String auditStatus;

    @OneToMany(mappedBy = "spu", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSkuEntity> skus = new ArrayList<>();

    protected ProductSpuEntity() {
    }

    public static ProductSpuEntity draft(Long merchantId, String spuCode, String title, Long categoryId) {
        ProductSpuEntity entity = new ProductSpuEntity();
        entity.merchantId = merchantId;
        entity.spuCode = spuCode;
        entity.title = title;
        entity.categoryId = categoryId;
        entity.status = "draft";
        entity.publishStatus = "unpublished";
        entity.auditStatus = "pending";
        return entity;
    }

    public void addSku(ProductSkuEntity sku) {
        sku.attachTo(this);
        skus.add(sku);
    }

    public Long getId() {
        return id;
    }

    public List<ProductSkuEntity> getSkus() {
        return skus;
    }
}
