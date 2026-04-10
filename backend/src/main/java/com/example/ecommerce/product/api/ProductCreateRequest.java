package com.example.ecommerce.product.api;

import java.util.List;
import java.util.UUID;

public record ProductCreateRequest(
    Long merchantId,
    String productType,
    String title,
    Long categoryId,
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

    public record SkuInput(String skuCode, String specSnapshot, String specHash, int initialStock) {
    }
}
