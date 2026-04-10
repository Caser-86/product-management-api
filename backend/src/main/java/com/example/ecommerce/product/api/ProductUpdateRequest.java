package com.example.ecommerce.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductUpdateRequest(
    @NotBlank(message = "title is required")
    String title,
    @NotNull(message = "categoryId is required")
    Long categoryId
) {
}
