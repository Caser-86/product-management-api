package com.example.ecommerce.pricing.api;

public record PriceUpdateRequest(Double listPrice, Double salePrice, String reason, Long operatorId) {
}
