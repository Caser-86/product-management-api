package com.example.ecommerce.search.domain;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.pricing.domain.PriceCurrentEntity;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "storefront_product_search")
public class StorefrontProductSearchEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String title;

    @Column(name = "primary_sku_id", nullable = false)
    private Long primarySkuId;

    @Column(name = "min_price", nullable = false)
    private BigDecimal minPrice;

    @Column(name = "max_price", nullable = false)
    private BigDecimal maxPrice;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "stock_status", nullable = false)
    private String stockStatus;

    @Column(name = "product_status", nullable = false)
    private String productStatus;

    @Column(name = "publish_status", nullable = false)
    private String publishStatus;

    @Column(name = "audit_status", nullable = false)
    private String auditStatus;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected StorefrontProductSearchEntity() {
    }

    public static StorefrontProductSearchEntity of(
        Long productId,
        Long merchantId,
        Long categoryId,
        String title,
        Long primarySkuId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int availableQty,
        String stockStatus,
        String productStatus,
        String publishStatus,
        String auditStatus
    ) {
        StorefrontProductSearchEntity entity = new StorefrontProductSearchEntity();
        entity.productId = productId;
        entity.merchantId = merchantId;
        entity.categoryId = categoryId;
        entity.title = title;
        entity.primarySkuId = primarySkuId;
        entity.minPrice = minPrice;
        entity.maxPrice = maxPrice;
        entity.availableQty = availableQty;
        entity.stockStatus = stockStatus;
        entity.productStatus = productStatus;
        entity.publishStatus = publishStatus;
        entity.auditStatus = auditStatus;
        return entity;
    }

    public static StorefrontProductSearchEntity from(
        ProductSpuEntity spu,
        ProductSkuEntity sku,
        PriceCurrentEntity price,
        InventoryBalanceEntity inventory
    ) {
        return of(
            spu.getId(),
            spu.getMerchantId(),
            spu.getCategoryId(),
            spu.getTitle(),
            sku.getId(),
            price == null ? BigDecimal.ZERO : price.getSalePrice(),
            price == null ? BigDecimal.ZERO : price.getListPrice(),
            inventory == null ? 0 : inventory.getAvailableQty(),
            inventory != null && inventory.getAvailableQty() > 0 ? "in_stock" : "out_of_stock",
            spu.getStatus(),
            spu.getPublishStatus(),
            spu.getAuditStatus()
        );
    }

    public Long getProductId() {
        return productId;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public String getStockStatus() {
        return stockStatus;
    }

    public String getProductStatus() {
        return productStatus;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getPrimarySkuId() {
        return primarySkuId;
    }

    public int getAvailableQty() {
        return availableQty;
    }
}
