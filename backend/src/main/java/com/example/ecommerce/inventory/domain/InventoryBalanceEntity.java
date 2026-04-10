package com.example.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;

@Entity
@Table(name = "inventory_balance")
public class InventoryBalanceEntity {

    @Id
    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "sold_qty", nullable = false)
    private int soldQty;

    @Version
    @Column(nullable = false)
    private Long version;

    protected InventoryBalanceEntity() {
    }

    public static InventoryBalanceEntity initial(Long skuId, Long merchantId, int quantity) {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.skuId = skuId;
        entity.merchantId = merchantId;
        entity.totalQty = quantity;
        entity.availableQty = quantity;
        entity.reservedQty = 0;
        entity.soldQty = 0;
        return entity;
    }

    public int getAvailableQty() {
        return availableQty;
    }

    public int getTotalQty() {
        return totalQty;
    }

    public int getReservedQty() {
        return reservedQty;
    }

    public int getSoldQty() {
        return soldQty;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation quantity must be positive");
        }
        if (availableQty < quantity) {
            throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT, "inventory insufficient");
        }
        this.availableQty -= quantity;
        this.reservedQty += quantity;
    }

    public void confirm(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "confirmation quantity must be positive");
        }
        if (reservedQty < quantity) {
            throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT, "reserved inventory insufficient");
        }
        this.reservedQty -= quantity;
        this.soldQty += quantity;
    }

    public void adjust(int delta) {
        if (delta == 0) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "adjustment delta must not be zero");
        }
        if (totalQty + delta < soldQty + reservedQty) {
            throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT, "inventory adjustment exceeds available stock");
        }
        if (availableQty + delta < 0) {
            throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT, "inventory adjustment exceeds available stock");
        }
        this.totalQty += delta;
        this.availableQty += delta;
    }
}
