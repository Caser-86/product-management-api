package com.example.ecommerce.product.domain;

import com.example.ecommerce.shared.api.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductWorkflowStateTest {

    @Test
    void draft_defaults_to_pending_unpublished() {
        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-WF-1", "workflow-demo", 33L);

        assertThat(spu.getStatus()).isEqualTo("draft");
        assertThat(spu.getAuditStatus()).isEqualTo("pending");
        assertThat(spu.getPublishStatus()).isEqualTo("unpublished");
    }

    @Test
    void approve_moves_to_active_approved_with_audit_metadata() {
        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-WF-2", "workflow-approve", 33L);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        LocalDateTime auditAt = LocalDateTime.of(2026, 4, 11, 11, 0, 0);

        spu.submitForReview(submittedAt);
        spu.approve(9001L, "approved", auditAt);

        assertThat(spu.getStatus()).isEqualTo("active");
        assertThat(spu.getAuditStatus()).isEqualTo("approved");
        assertThat(spu.getPublishStatus()).isEqualTo("unpublished");
        assertThat(spu.getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(spu.getAuditAt()).isEqualTo(auditAt);
        assertThat(spu.getAuditBy()).isEqualTo(9001L);
        assertThat(spu.getAuditComment()).isEqualTo("approved");
    }

    @Test
    void approve_requires_product_submitted_for_review() {
        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-WF-2A", "workflow-approve-gate", 33L);

        assertThatThrownBy(() -> spu.approve(9001L, "approved", LocalDateTime.now()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void reject_requires_product_submitted_for_review() {
        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-WF-2B", "workflow-reject-gate", 33L);

        assertThatThrownBy(() -> spu.reject(9001L, "rejected", LocalDateTime.now()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void submit_for_review_fails_when_already_submitted() {
        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-WF-2C", "workflow-submit-twice", 33L);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 4, 11, 10, 0, 0);

        spu.submitForReview(submittedAt);

        assertThatThrownBy(() -> spu.submitForReview(submittedAt.plusMinutes(5)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void publish_requires_active_and_approved() {
        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-WF-3", "workflow-publish-invalid", 33L);

        assertThatThrownBy(() -> spu.publish(9001L, LocalDateTime.now()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void reset_to_draft_after_mutation_unpublishes_published_product() {
        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-WF-4", "workflow-reset", 33L);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 4, 11, 9, 0, 0);
        LocalDateTime auditAt = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 4, 11, 11, 0, 0);

        spu.submitForReview(submittedAt);
        spu.approve(9002L, "pass", auditAt);
        spu.publish(9003L, publishedAt);
        spu.resetToDraftAfterMutation();

        assertThat(spu.getStatus()).isEqualTo("draft");
        assertThat(spu.getAuditStatus()).isEqualTo("pending");
        assertThat(spu.getPublishStatus()).isEqualTo("unpublished");
        assertThat(spu.getPublishedAt()).isNull();
        assertThat(spu.getPublishedBy()).isNull();
    }
}
