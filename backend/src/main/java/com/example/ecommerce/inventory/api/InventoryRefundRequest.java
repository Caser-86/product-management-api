package com.example.ecommerce.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventoryRefundRequest(
    @NotBlank(message = "bizId is required")
    String bizId,
    @NotNull(message = "skuId is required")
    Long skuId,
    @Min(value = 1, message = "quantity must be greater than 0")
    int quantity,
    @NotNull(message = "restock is required")
    Boolean restock,
    String reason,
    Long operatorId
) {
}
