package com.example.ecommerce.inventory.api;

import java.util.List;

public record InventoryReservationRequest(String idempotencyKey, String bizId, List<Item> items) {
    public record Item(Long skuId, int quantity) {
    }
}
