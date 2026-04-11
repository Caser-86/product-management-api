package com.example.ecommerce.inventory.api;

import jakarta.validation.constraints.NotBlank;

public record InventoryReleaseRequest(
    @NotBlank(message = "bizId is required")
    String bizId
) {
}
