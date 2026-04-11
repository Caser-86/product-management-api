package com.example.ecommerce.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Inventory snapshot for a SKU")
public record InventorySnapshotResponse(
    @Schema(description = "SKU ID", example = "20001")
    Long skuId,
    @Schema(description = "Total stock quantity", example = "10")
    int totalQty,
    @Schema(description = "Available stock quantity", example = "8")
    int availableQty,
    @Schema(description = "Reserved stock quantity", example = "1")
    int reservedQty,
    @Schema(description = "Sold stock quantity", example = "1")
    int soldQty
) {
}
