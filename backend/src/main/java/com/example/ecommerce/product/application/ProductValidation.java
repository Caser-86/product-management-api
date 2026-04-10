package com.example.ecommerce.product.application;

import com.example.ecommerce.product.api.ProductCreateRequest;

import java.util.HashSet;

public final class ProductValidation {

    private ProductValidation() {
    }

    public static void validateUniqueSpecHashes(ProductCreateRequest request) {
        HashSet<String> seen = new HashSet<>();
        for (ProductCreateRequest.SkuInput sku : request.skus()) {
            if (!seen.add(sku.specHash())) {
                throw new IllegalArgumentException("duplicate sku spec hash");
            }
        }
    }
}
