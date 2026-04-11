package com.example.ecommerce.product.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Workflow history entries for a product")
public record ProductWorkflowHistoryResponse(
    @Schema(description = "Workflow history items in reverse chronological order")
    List<Item> items
) {

    @Schema(description = "One workflow transition entry")
    public record Item(
        @Schema(description = "Workflow action name", example = "approve")
        String action,
        @Schema(description = "Product status before the action", example = "draft")
        String fromStatus,
        @Schema(description = "Product status after the action", example = "active")
        String toStatus,
        @Schema(description = "Audit status before the action", example = "pending")
        String fromAuditStatus,
        @Schema(description = "Audit status after the action", example = "approved")
        String toAuditStatus,
        @Schema(description = "Publish status before the action", example = "unpublished")
        String fromPublishStatus,
        @Schema(description = "Publish status after the action", example = "published")
        String toPublishStatus,
        @Schema(description = "Operator user ID", example = "9001")
        Long operatorId,
        @Schema(description = "Operator role", example = "PLATFORM_ADMIN")
        String operatorRole,
        @Schema(description = "Optional workflow comment", example = "approved by platform")
        String comment,
        @Schema(description = "Time when the history row was created")
        LocalDateTime createdAt
    ) {
    }
}
