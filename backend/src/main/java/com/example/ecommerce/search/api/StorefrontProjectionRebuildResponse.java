package com.example.ecommerce.search.api;

import java.util.List;

public record StorefrontProjectionRebuildResponse(
    int processedCount,
    int successCount,
    int failureCount,
    long durationMs,
    List<Failure> failures
) {
    public record Failure(Long productId, String errorCode, String message) {
    }
}
