package com.example.ecommerce.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(
    @Schema(description = "Positive for restock, negative for deduction", example = "5")
    @NotNull(message = "delta is required")
    Integer delta,
    @Schema(description = "Adjustment reason", example = "manual restock")
    String reason,
    @Schema(description = "Operator ID", example = "9001")
    Long operatorId
) {
}
