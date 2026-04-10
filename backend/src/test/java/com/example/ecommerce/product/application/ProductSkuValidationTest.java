package com.example.ecommerce.product.application;

import com.example.ecommerce.product.api.ProductCreateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSkuValidationTest {

    @Test
    void rejects_duplicate_spec_hash_within_same_spu() {
        ProductCreateRequest request = new ProductCreateRequest(
            2001L,
            "merchant",
            "男士连帽卫衣",
            33L,
            List.of(
                new ProductCreateRequest.SkuInput("SKU-1", "{\"颜色\":\"黑色\",\"尺寸\":\"M\"}", "same-hash", 100),
                new ProductCreateRequest.SkuInput("SKU-2", "{\"颜色\":\"黑色\",\"尺寸\":\"M\"}", "same-hash", 100)
            )
        );

        assertThatThrownBy(() -> ProductValidation.validateUniqueSpecHashes(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("duplicate sku spec hash");
    }
}
