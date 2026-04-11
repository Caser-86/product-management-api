package com.example.ecommerce.search.application;

import org.springframework.data.domain.Sort;

public enum StorefrontSearchSort {
    NEWEST(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("productId"))),
    PRICE_ASC(Sort.by(Sort.Order.asc("minPrice"), Sort.Order.desc("productId"))),
    PRICE_DESC(Sort.by(Sort.Order.desc("maxPrice"), Sort.Order.desc("productId")));

    private final Sort sort;

    StorefrontSearchSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    public static StorefrontSearchSort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEWEST;
        }
        return switch (raw.trim().toLowerCase()) {
            case "newest" -> NEWEST;
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            default -> throw new IllegalArgumentException("unsupported storefront sort");
        };
    }
}
