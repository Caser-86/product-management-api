package com.example.ecommerce.product.application;

import org.springframework.data.domain.Sort;

public enum AdminProductListSort {
    CREATED_DESC(Sort.by(Sort.Order.desc("id"))),
    TITLE_ASC(Sort.by(Sort.Order.asc("title"), Sort.Order.desc("id"))),
    TITLE_DESC(Sort.by(Sort.Order.desc("title"), Sort.Order.desc("id")));

    private final Sort sort;

    AdminProductListSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    public static AdminProductListSort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CREATED_DESC;
        }
        return switch (raw.trim().toLowerCase()) {
            case "created_desc" -> CREATED_DESC;
            case "title_asc" -> TITLE_ASC;
            case "title_desc" -> TITLE_DESC;
            default -> throw new IllegalArgumentException("unsupported admin product sort");
        };
    }
}
