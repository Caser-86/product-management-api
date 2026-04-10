package com.example.ecommerce.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductUpdateRequest(
    @Schema(description = "Updated product title", example = "updated-hoodie")
    @NotBlank(message = "title is required")
    String title,
    @Schema(description = "Updated category ID", example = "66")
    @NotNull(message = "categoryId is required")
    Long categoryId
) {
}
