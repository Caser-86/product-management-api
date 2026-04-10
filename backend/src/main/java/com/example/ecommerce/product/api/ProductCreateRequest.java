package com.example.ecommerce.product.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record ProductCreateRequest(
    @Schema(description = "Merchant ID", example = "2001")
    Long merchantId,
    @Schema(description = "Product ownership type", example = "merchant")
    String productType,
    @Schema(description = "Product title", example = "mens-hoodie")
    String title,
    @Schema(description = "Leaf category ID", example = "33")
    Long categoryId,
    @Schema(description = "SKU definitions for this product")
    List<SkuInput> skus
) {
    public static ProductCreateRequest sample() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return new ProductCreateRequest(
            2001L,
            "merchant",
            "男士连帽卫衣",
            33L,
            List.of(new SkuInput("SKU-" + suffix + "-BLK-M", "{\"颜色\":\"黑色\",\"尺寸\":\"M\"}", "spec-hash-" + suffix, 100))
        );
    }

    public record SkuInput(
        @Schema(description = "SKU code", example = "SKU-1001-BLK-M")
        String skuCode,
        @Schema(description = "Serialized spec snapshot", example = "{\"color\":\"black\",\"size\":\"M\"}")
        String specSnapshot,
        @Schema(description = "Spec hash", example = "spec-hash-1001")
        String specHash,
        @Schema(description = "Initial stock quantity", example = "100")
        int initialStock
    ) {
    }
}
