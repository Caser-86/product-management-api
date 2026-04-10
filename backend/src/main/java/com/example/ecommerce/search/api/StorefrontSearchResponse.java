package com.example.ecommerce.search.api;

import java.util.List;

public record StorefrontSearchResponse(List<Item> items, int page, int pageSize, long total) {
    public record Item(Long productId, String title, double minPrice, double maxPrice, String stockStatus) {
    }
}
