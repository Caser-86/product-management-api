package com.example.ecommerce.inventory.api;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(
    @NotNull(message = "delta is required")
    Integer delta,
    String reason,
    Long operatorId
) {
}
