package com.example.ecommerce.product.api;

import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryEntity;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AdminProductWorkflowControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-Role";
    private static final String MERCHANT_ID_HEADER = "X-Merchant-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private ProductWorkflowHistoryRepository workflowHistoryRepository;

    @BeforeEach
    void setUp() {
        workflowHistoryRepository.deleteAll();
        productSpuRepository.deleteAll();
    }

    @Test
    void platform_admin_can_approve_submitted_product() throws Exception {
        long productId = createSubmittedProduct(2001L);

        mockMvc.perform(post("/admin/products/{productId}/approve", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "approved by platform"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(productId))
            .andExpect(jsonPath("$.data.status").value("active"))
            .andExpect(jsonPath("$.data.auditStatus").value("approved"))
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"))
            .andExpect(jsonPath("$.data.auditComment").value("approved by platform"));

        var histories = workflowHistoryRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
        assertThat(histories).isNotEmpty();
        ProductWorkflowHistoryEntity newest = histories.get(0);
        assertThat(newest.getAction()).isEqualTo("approve");
        assertThat(newest.getFromStatus()).isEqualTo("draft");
        assertThat(newest.getToStatus()).isEqualTo("active");
        assertThat(newest.getFromAuditStatus()).isEqualTo("pending");
        assertThat(newest.getToAuditStatus()).isEqualTo("approved");
        assertThat(newest.getFromPublishStatus()).isEqualTo("unpublished");
        assertThat(newest.getToPublishStatus()).isEqualTo("unpublished");
        assertThat(newest.getOperatorId()).isEqualTo(9001L);
        assertThat(newest.getOperatorRole()).isEqualTo("PLATFORM_ADMIN");
        assertThat(newest.getComment()).isEqualTo("approved by platform");
    }

    @Test
    void merchant_admin_cannot_approve_product() throws Exception {
        long productId = createSubmittedProduct(3001L);

        mockMvc.perform(post("/admin/products/{productId}/approve", productId)
                .header(USER_ID_HEADER, "9101")
                .header(ROLE_HEADER, "MERCHANT_ADMIN")
                .header(MERCHANT_ID_HEADER, "3001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "merchant tries to approve"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
    }

    @Test
    void platform_admin_cannot_submit_for_review() throws Exception {
        long productId = productSpuRepository.save(
            ProductSpuEntity.draft(2001L, "SPU-WF-SUBMIT-P", "submit-product", 66L)
        ).getId();

        mockMvc.perform(post("/admin/products/{productId}/submit-for-review", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "submit from platform"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
    }

    @Test
    void merchant_submit_for_review_returns_not_found_for_other_merchant_product() throws Exception {
        long productId = productSpuRepository.save(
            ProductSpuEntity.draft(5001L, "SPU-WF-SUBMIT-M", "submit-product", 66L)
        ).getId();

        mockMvc.perform(post("/admin/products/{productId}/submit-for-review", productId)
                .header(USER_ID_HEADER, "9002")
                .header(ROLE_HEADER, "MERCHANT_ADMIN")
                .header(MERCHANT_ID_HEADER, "3001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "submit as wrong merchant"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void merchant_submit_for_review_writes_workflow_history() throws Exception {
        long productId = createDraftProduct(3001L, "SPU-WF-HIST-SUBMIT");

        mockMvc.perform(post("/admin/products/{productId}/submit-for-review", productId)
                .header(USER_ID_HEADER, "9102")
                .header(ROLE_HEADER, "MERCHANT_ADMIN")
                .header(MERCHANT_ID_HEADER, "3001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "merchant submit comment"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.auditStatus").value("pending"));

        assertNewestHistory(
            productId,
            "submit_for_review",
            "draft",
            "draft",
            "pending",
            "pending",
            "unpublished",
            "unpublished",
            9102L,
            "MERCHANT_ADMIN",
            "merchant submit comment"
        );
    }

    @Test
    void platform_reject_writes_workflow_history() throws Exception {
        long productId = createSubmittedProduct(3001L);

        mockMvc.perform(post("/admin/products/{productId}/reject", productId)
                .header(USER_ID_HEADER, "9005")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "3001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "reject for missing docs"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.auditStatus").value("rejected"))
            .andExpect(jsonPath("$.data.auditComment").value("reject for missing docs"));

        assertNewestHistory(
            productId,
            "reject",
            "draft",
            "draft",
            "pending",
            "rejected",
            "unpublished",
            "unpublished",
            9005L,
            "PLATFORM_ADMIN",
            "reject for missing docs"
        );
    }

    @Test
    void platform_publish_writes_workflow_history() throws Exception {
        long productId = createApprovedProduct(3001L);

        mockMvc.perform(post("/admin/products/{productId}/publish", productId)
                .header(USER_ID_HEADER, "9006")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "3001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "publish to storefront"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("active"))
            .andExpect(jsonPath("$.data.auditStatus").value("approved"))
            .andExpect(jsonPath("$.data.publishStatus").value("published"));

        assertNewestHistory(
            productId,
            "publish",
            "active",
            "active",
            "approved",
            "approved",
            "unpublished",
            "published",
            9006L,
            "PLATFORM_ADMIN",
            "publish to storefront"
        );
    }

    @Test
    void merchant_resubmit_writes_workflow_history() throws Exception {
        long productId = createRejectedProduct(3001L);

        mockMvc.perform(post("/admin/products/{productId}/resubmit-for-review", productId)
                .header(USER_ID_HEADER, "9103")
                .header(ROLE_HEADER, "MERCHANT_ADMIN")
                .header(MERCHANT_ID_HEADER, "3001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "resubmit after fixes"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.auditStatus").value("pending"));

        assertNewestHistory(
            productId,
            "resubmit_for_review",
            "draft",
            "draft",
            "rejected",
            "pending",
            "unpublished",
            "unpublished",
            9103L,
            "MERCHANT_ADMIN",
            "resubmit after fixes"
        );
    }

    @Test
    void platform_unpublish_writes_workflow_history() throws Exception {
        long productId = createPublishedProduct(3001L);

        mockMvc.perform(post("/admin/products/{productId}/unpublish", productId)
                .header(USER_ID_HEADER, "9007")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "3001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "take down for review"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));

        assertNewestHistory(
            productId,
            "unpublish",
            "active",
            "active",
            "approved",
            "approved",
            "published",
            "unpublished",
            9007L,
            "PLATFORM_ADMIN",
            "take down for review"
        );
    }

    private long createSubmittedProduct(Long merchantId) {
        ProductSpuEntity product = ProductSpuEntity.draft(
            merchantId,
            "SPU-WF-API-" + merchantId + "-" + System.nanoTime(),
            "workflow-product",
            66L
        );
        product.submitForReview(LocalDateTime.of(2026, 4, 11, 12, 0, 0));
        return productSpuRepository.save(product).getId();
    }

    private long createDraftProduct(Long merchantId, String spuCodePrefix) {
        ProductSpuEntity product = ProductSpuEntity.draft(
            merchantId,
            spuCodePrefix + "-" + System.nanoTime(),
            "workflow-product",
            66L
        );
        return productSpuRepository.save(product).getId();
    }

    private long createApprovedProduct(Long merchantId) {
        ProductSpuEntity product = ProductSpuEntity.draft(
            merchantId,
            "SPU-WF-APPROVED-" + System.nanoTime(),
            "workflow-product",
            66L
        );
        product.submitForReview(LocalDateTime.of(2026, 4, 11, 9, 0, 0));
        product.approve(9009L, "approved for publish", LocalDateTime.of(2026, 4, 11, 10, 0, 0));
        return productSpuRepository.save(product).getId();
    }

    private long createRejectedProduct(Long merchantId) {
        ProductSpuEntity product = ProductSpuEntity.draft(
            merchantId,
            "SPU-WF-REJECTED-" + System.nanoTime(),
            "workflow-product",
            66L
        );
        product.submitForReview(LocalDateTime.of(2026, 4, 11, 8, 0, 0));
        product.reject(9010L, "initial rejection", LocalDateTime.of(2026, 4, 11, 9, 0, 0));
        return productSpuRepository.save(product).getId();
    }

    private long createPublishedProduct(Long merchantId) {
        ProductSpuEntity product = ProductSpuEntity.draft(
            merchantId,
            "SPU-WF-PUBLISHED-" + System.nanoTime(),
            "workflow-product",
            66L
        );
        product.submitForReview(LocalDateTime.of(2026, 4, 11, 7, 0, 0));
        product.approve(9011L, "approved for publish", LocalDateTime.of(2026, 4, 11, 8, 0, 0));
        product.publish(9012L, LocalDateTime.of(2026, 4, 11, 9, 0, 0));
        return productSpuRepository.save(product).getId();
    }

    private void assertNewestHistory(
        long productId,
        String action,
        String fromStatus,
        String toStatus,
        String fromAuditStatus,
        String toAuditStatus,
        String fromPublishStatus,
        String toPublishStatus,
        long operatorId,
        String operatorRole,
        String comment
    ) {
        var histories = workflowHistoryRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
        assertThat(histories).isNotEmpty();
        ProductWorkflowHistoryEntity newest = histories.get(0);
        assertThat(newest.getAction()).isEqualTo(action);
        assertThat(newest.getFromStatus()).isEqualTo(fromStatus);
        assertThat(newest.getToStatus()).isEqualTo(toStatus);
        assertThat(newest.getFromAuditStatus()).isEqualTo(fromAuditStatus);
        assertThat(newest.getToAuditStatus()).isEqualTo(toAuditStatus);
        assertThat(newest.getFromPublishStatus()).isEqualTo(fromPublishStatus);
        assertThat(newest.getToPublishStatus()).isEqualTo(toPublishStatus);
        assertThat(newest.getOperatorId()).isEqualTo(operatorId);
        assertThat(newest.getOperatorRole()).isEqualTo(operatorRole);
        assertThat(newest.getComment()).isEqualTo(comment);
    }
}
