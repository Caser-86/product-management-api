package com.example.ecommerce.product.domain;

import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_spu")
public class ProductSpuEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "spu_code", nullable = false)
    private String spuCode;

    @Column(nullable = false)
    private String title;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String status;

    @Column(name = "publish_status", nullable = false)
    private String publishStatus;

    @Column(name = "audit_status", nullable = false)
    private String auditStatus;

    @Column(name = "audit_comment")
    private String auditComment;

    @Column(name = "audit_by")
    private Long auditBy;

    @Column(name = "audit_at")
    private LocalDateTime auditAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    @OneToMany(mappedBy = "spu", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSkuEntity> skus = new ArrayList<>();

    protected ProductSpuEntity() {
    }

    public static ProductSpuEntity draft(Long merchantId, String spuCode, String title, Long categoryId) {
        ProductSpuEntity entity = new ProductSpuEntity();
        entity.merchantId = merchantId;
        entity.spuCode = spuCode;
        entity.title = title;
        entity.categoryId = categoryId;
        entity.status = "draft";
        entity.publishStatus = "unpublished";
        entity.auditStatus = "pending";
        return entity;
    }

    public void addSku(ProductSkuEntity sku) {
        sku.attachTo(this);
        skus.add(sku);
    }

    public void updateBasics(String title, Long categoryId) {
        this.title = title;
        this.categoryId = categoryId;
    }

    public void submitForReview(LocalDateTime submittedAt) {
        if (!"draft".equals(status) || !"pending".equals(auditStatus) || !"unpublished".equals(publishStatus)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product cannot be submitted for review");
        }
        if (this.submittedAt != null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product already submitted for review");
        }
        this.submittedAt = submittedAt;
    }

    public void resubmitForReview(LocalDateTime submittedAt) {
        if (!"draft".equals(status) || !"rejected".equals(auditStatus) || !"unpublished".equals(publishStatus)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product cannot be resubmitted for review");
        }
        this.auditStatus = "pending";
        this.auditComment = null;
        this.auditBy = null;
        this.auditAt = null;
        this.submittedAt = submittedAt;
    }

    public void approve(Long auditorId, String auditComment, LocalDateTime auditAt) {
        if (!"draft".equals(status) || !"pending".equals(auditStatus) || submittedAt == null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product cannot be approved");
        }
        this.status = "active";
        this.auditStatus = "approved";
        this.publishStatus = "unpublished";
        this.auditBy = auditorId;
        this.auditComment = auditComment;
        this.auditAt = auditAt;
    }

    public void reject(Long auditorId, String auditComment, LocalDateTime auditAt) {
        if (!"draft".equals(status) || !"pending".equals(auditStatus) || submittedAt == null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product cannot be rejected");
        }
        this.auditStatus = "rejected";
        this.auditBy = auditorId;
        this.auditComment = auditComment;
        this.auditAt = auditAt;
    }

    public void publish(Long operatorId, LocalDateTime publishedAt) {
        if (!"active".equals(status) || !"approved".equals(auditStatus) || !"unpublished".equals(publishStatus)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product cannot be published");
        }
        this.publishStatus = "published";
        this.publishedBy = operatorId;
        this.publishedAt = publishedAt;
    }

    public void unpublish() {
        if (!"published".equals(publishStatus)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product cannot be unpublished");
        }
        this.publishStatus = "unpublished";
        this.publishedBy = null;
        this.publishedAt = null;
    }

    public void resetToDraftAfterMutation() {
        this.status = "draft";
        this.auditStatus = "pending";
        this.publishStatus = "unpublished";
        this.auditComment = null;
        this.auditBy = null;
        this.auditAt = null;
        this.submittedAt = null;
        this.publishedAt = null;
        this.publishedBy = null;
    }

    public void archive() {
        this.status = "deleted";
        this.publishStatus = "unpublished";
    }

    public boolean isDeleted() {
        return "deleted".equals(status);
    }

    public Long getId() {
        return id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public String getTitle() {
        return title;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getStatus() {
        return status;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public String getAuditStatus() {
        return auditStatus;
    }

    public String getAuditComment() {
        return auditComment;
    }

    public Long getAuditBy() {
        return auditBy;
    }

    public LocalDateTime getAuditAt() {
        return auditAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public List<ProductSkuEntity> getSkus() {
        return skus;
    }
}
