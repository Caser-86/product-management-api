package com.example.ecommerce.product.api;

import java.util.List;

public record ProductListResponse(List<ProductResponse> items, int page, int pageSize, long total) {
}
