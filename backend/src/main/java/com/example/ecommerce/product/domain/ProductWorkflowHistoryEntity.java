package com.example.ecommerce.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "product_workflow_history")
public class ProductWorkflowHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String action;

    @Column(name = "from_status", nullable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "from_audit_status", nullable = false)
    private String fromAuditStatus;

    @Column(name = "to_audit_status", nullable = false)
    private String toAuditStatus;

    @Column(name = "from_publish_status", nullable = false)
    private String fromPublishStatus;

    @Column(name = "to_publish_status", nullable = false)
    private String toPublishStatus;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_role")
    private String operatorRole;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProductWorkflowHistoryEntity() {
    }

    public static ProductWorkflowHistoryEntity of(
        Long productId,
        String action,
        String fromStatus,
        String toStatus,
        String fromAuditStatus,
        String toAuditStatus,
        String fromPublishStatus,
        String toPublishStatus,
        Long operatorId,
        String operatorRole,
        String comment
    ) {
        ProductWorkflowHistoryEntity entity = new ProductWorkflowHistoryEntity();
        entity.productId = Objects.requireNonNull(productId, "productId must not be null");
        entity.action = requireMaxLength(Objects.requireNonNull(action, "action must not be null"), 32, "action");
        entity.fromStatus = requireMaxLength(Objects.requireNonNull(fromStatus, "fromStatus must not be null"), 20, "fromStatus");
        entity.toStatus = requireMaxLength(Objects.requireNonNull(toStatus, "toStatus must not be null"), 20, "toStatus");
        entity.fromAuditStatus = requireMaxLength(
            Objects.requireNonNull(fromAuditStatus, "fromAuditStatus must not be null"),
            20,
            "fromAuditStatus"
        );
        entity.toAuditStatus = requireMaxLength(
            Objects.requireNonNull(toAuditStatus, "toAuditStatus must not be null"),
            20,
            "toAuditStatus"
        );
        entity.fromPublishStatus = requireMaxLength(
            Objects.requireNonNull(fromPublishStatus, "fromPublishStatus must not be null"),
            20,
            "fromPublishStatus"
        );
        entity.toPublishStatus = requireMaxLength(
            Objects.requireNonNull(toPublishStatus, "toPublishStatus must not be null"),
            20,
            "toPublishStatus"
        );
        entity.operatorId = operatorId;
        entity.operatorRole = requireNullableMaxLength(operatorRole, 32, "operatorRole");
        entity.comment = requireNullableMaxLength(comment, 500, "comment");
        return entity;
    }

    private static String requireMaxLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be <= " + maxLength);
        }
        return value;
    }

    private static String requireNullableMaxLength(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        return requireMaxLength(value, maxLength, fieldName);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getAction() {
        return action;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getFromAuditStatus() {
        return fromAuditStatus;
    }

    public String getToAuditStatus() {
        return toAuditStatus;
    }

    public String getFromPublishStatus() {
        return fromPublishStatus;
    }

    public String getToPublishStatus() {
        return toPublishStatus;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getComment() {
        return comment;
    }

    public String getOperatorRole() {
        return operatorRole;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
