package com.example.ecommerce.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Inventory refund result")
public record InventoryRefundResponse(
    @Schema(description = "SKU ID", example = "20001")
    Long skuId,
    @Schema(description = "Total stock quantity", example = "10")
    int totalQty,
    @Schema(description = "Available stock quantity", example = "9")
    int availableQty,
    @Schema(description = "Reserved stock quantity", example = "0")
    int reservedQty,
    @Schema(description = "Sold stock quantity", example = "1")
    int soldQty,
    @Schema(description = "Business order ID", example = "ORDER-REFUND-1")
    String bizId,
    @Schema(description = "Whether the refund restocked sellable quantity", example = "true")
    boolean restock,
    @Schema(description = "Refund reason", example = "customer cancellation")
    String reason,
    @Schema(description = "Operator ID", example = "9001")
    Long operatorId
) {
}
