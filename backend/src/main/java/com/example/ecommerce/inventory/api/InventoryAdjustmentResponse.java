package com.example.ecommerce.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Inventory adjustment result")
public record InventoryAdjustmentResponse(
    @Schema(description = "SKU ID", example = "20001")
    Long skuId,
    @Schema(description = "Total stock quantity", example = "15")
    int totalQty,
    @Schema(description = "Available stock quantity", example = "15")
    int availableQty,
    @Schema(description = "Reserved stock quantity", example = "0")
    int reservedQty,
    @Schema(description = "Sold stock quantity", example = "0")
    int soldQty,
    @Schema(description = "Adjustment reason", example = "manual restock")
    String reason,
    @Schema(description = "Operator ID", example = "9001")
    Long operatorId
) {
}
