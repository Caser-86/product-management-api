package com.example.ecommerce.product.api;

import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryEntity;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AdminProductControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-Role";
    private static final String MERCHANT_ID_HEADER = "X-Merchant-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private ProductWorkflowHistoryRepository workflowHistoryRepository;

    @Test
    void creates_product_and_reads_it_back() throws Exception {
        ProductCreateRequest request = ProductCreateRequest.sample();

        var createResult = mockMvc.perform(post("/admin/products")
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("draft"))
            .andExpect(jsonPath("$.data.auditStatus").value("pending"))
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"))
            .andReturn();

        long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

        mockMvc.perform(get("/admin/products/{productId}", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value(request.title()))
            .andExpect(jsonPath("$.data.status").value("draft"))
            .andExpect(jsonPath("$.data.auditStatus").value("pending"))
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"))
            .andExpect(jsonPath("$.data.auditComment").isEmpty())
            .andExpect(jsonPath("$.data.submittedAt").isEmpty())
            .andExpect(jsonPath("$.data.auditAt").isEmpty())
            .andExpect(jsonPath("$.data.publishedAt").isEmpty());
    }

    @Test
    void updates_product_basics() throws Exception {
        ProductCreateRequest request = ProductCreateRequest.sample();
        var createResult = mockMvc.perform(post("/admin/products")
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

        mockMvc.perform(put("/admin/products/{productId}", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "updated-hoodie",
                      "categoryId": 66
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("updated-hoodie"))
            .andExpect(jsonPath("$.data.categoryId").value(66))
            .andExpect(jsonPath("$.data.status").value("draft"))
            .andExpect(jsonPath("$.data.auditStatus").value("pending"))
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));
    }

    @Test
    void updating_previously_published_product_resets_workflow_state() throws Exception {
        long productId = createPublishedProduct(2001L);

        mockMvc.perform(put("/admin/products/{productId}", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "updated-after-publish",
                      "categoryId": 77
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("updated-after-publish"))
            .andExpect(jsonPath("$.data.categoryId").value(77))
            .andExpect(jsonPath("$.data.status").value("draft"))
            .andExpect(jsonPath("$.data.auditStatus").value("pending"))
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));

        assertNewestHistory(
            productId,
            "update_reset",
            "active",
            "draft",
            "approved",
            "pending",
            "published",
            "unpublished",
            9001L,
            "PLATFORM_ADMIN",
            "product updated after approval"
        );
    }

    @Test
    void updating_unpublished_but_approved_product_resets_workflow_state() throws Exception {
        long productId = createUnpublishedApprovedProduct(2001L);

        mockMvc.perform(put("/admin/products/{productId}", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "updated-after-unpublish",
                      "categoryId": 88
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("updated-after-unpublish"))
            .andExpect(jsonPath("$.data.categoryId").value(88))
            .andExpect(jsonPath("$.data.status").value("draft"))
            .andExpect(jsonPath("$.data.auditStatus").value("pending"))
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));

        assertNewestHistory(
            productId,
            "update_reset",
            "active",
            "draft",
            "approved",
            "pending",
            "unpublished",
            "unpublished",
            9001L,
            "PLATFORM_ADMIN",
            "product updated after approval"
        );
    }

    @Test
    void deletes_product_and_hides_it_from_reads() throws Exception {
        ProductCreateRequest request = ProductCreateRequest.sample();
        var createResult = mockMvc.perform(post("/admin/products")
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

        mockMvc.perform(delete("/admin/products/{productId}", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(get("/admin/products/{productId}", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void merchant_admin_cannot_update_other_merchant_product() throws Exception {
        long productId = productSpuRepository.save(ProductSpuEntity.draft(4001L, "SPU-SEC-4001", "foreign-product", 66L)).getId();

        mockMvc.perform(put("/admin/products/{productId}", productId)
                .header(USER_ID_HEADER, "9002")
                .header(ROLE_HEADER, "MERCHANT_ADMIN")
                .header(MERCHANT_ID_HEADER, "3001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "renamed",
                      "categoryId": 10
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
    }

    private long createPublishedProduct(Long merchantId) {
        ProductSpuEntity product = ProductSpuEntity.draft(
            merchantId,
            "SPU-UPDATE-PUBLISHED-" + System.nanoTime(),
            "published-product",
            66L
        );
        product.submitForReview(LocalDateTime.of(2026, 4, 11, 7, 0, 0));
        product.approve(9011L, "approved for publish", LocalDateTime.of(2026, 4, 11, 8, 0, 0));
        product.publish(9012L, LocalDateTime.of(2026, 4, 11, 9, 0, 0));
        return productSpuRepository.save(product).getId();
    }

    private long createUnpublishedApprovedProduct(Long merchantId) {
        ProductSpuEntity product = ProductSpuEntity.draft(
            merchantId,
            "SPU-UPDATE-UNPUBLISHED-" + System.nanoTime(),
            "approved-product",
            66L
        );
        product.submitForReview(LocalDateTime.of(2026, 4, 11, 7, 0, 0));
        product.approve(9011L, "approved for publish", LocalDateTime.of(2026, 4, 11, 8, 0, 0));
        product.publish(9012L, LocalDateTime.of(2026, 4, 11, 9, 0, 0));
        product.unpublish();
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
        org.assertj.core.api.Assertions.assertThat(histories).isNotEmpty();
        ProductWorkflowHistoryEntity newest = histories.get(0);
        org.assertj.core.api.Assertions.assertThat(newest.getAction()).isEqualTo(action);
        org.assertj.core.api.Assertions.assertThat(newest.getFromStatus()).isEqualTo(fromStatus);
        org.assertj.core.api.Assertions.assertThat(newest.getToStatus()).isEqualTo(toStatus);
        org.assertj.core.api.Assertions.assertThat(newest.getFromAuditStatus()).isEqualTo(fromAuditStatus);
        org.assertj.core.api.Assertions.assertThat(newest.getToAuditStatus()).isEqualTo(toAuditStatus);
        org.assertj.core.api.Assertions.assertThat(newest.getFromPublishStatus()).isEqualTo(fromPublishStatus);
        org.assertj.core.api.Assertions.assertThat(newest.getToPublishStatus()).isEqualTo(toPublishStatus);
        org.assertj.core.api.Assertions.assertThat(newest.getOperatorId()).isEqualTo(operatorId);
        org.assertj.core.api.Assertions.assertThat(newest.getOperatorRole()).isEqualTo(operatorRole);
        org.assertj.core.api.Assertions.assertThat(newest.getComment()).isEqualTo(comment);
    }
}
