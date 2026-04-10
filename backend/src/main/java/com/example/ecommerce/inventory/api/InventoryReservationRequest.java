package com.example.ecommerce.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record InventoryReservationRequest(
    @Schema(description = "Idempotency key for retries", example = "order-8001-attempt-1")
    String idempotencyKey,
    @Schema(description = "Business order ID", example = "ORDER-8001")
    String bizId,
    @Schema(description = "Reservation line items")
    List<Item> items
) {
    public record Item(
        @Schema(description = "SKU ID", example = "20001")
        Long skuId,
        @Schema(description = "Reserved quantity", example = "2")
        int quantity
    ) {
    }
}
