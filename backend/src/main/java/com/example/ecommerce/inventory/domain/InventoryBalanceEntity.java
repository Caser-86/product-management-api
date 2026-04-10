package com.example.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    public int getSoldQty() {
        return soldQty;
    }

    public void reserve(int quantity) {
        this.availableQty -= quantity;
        this.reservedQty += quantity;
    }

    public void confirm(int quantity) {
        this.reservedQty -= quantity;
        this.soldQty += quantity;
    }
}
