package com.example.ecommerce.product.api;

import java.time.LocalDateTime;

public record ProductResponse(
    Long id,
    String title,
    Long merchantId,
    Long categoryId,
    String status,
    String auditStatus,
    String publishStatus,
    String auditComment,
    LocalDateTime submittedAt,
    LocalDateTime auditAt,
    LocalDateTime publishedAt
) {
}
