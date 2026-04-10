package com.example.ecommerce.product.api;

import java.util.List;

public record ProductCreateRequest(
    Long merchantId,
    String productType,
    String title,
    Long categoryId,
    List<SkuInput> skus
) {
    public static ProductCreateRequest sample() {
        return new ProductCreateRequest(
            2001L,
            "merchant",
            "男士连帽卫衣",
            33L,
            List.of(new SkuInput("SKU-1001-BLK-M", "{\"颜色\":\"黑色\",\"尺寸\":\"M\"}", "spec-hash-1"))
        );
    }

    public record SkuInput(String skuCode, String specSnapshot, String specHash) {
    }
}
