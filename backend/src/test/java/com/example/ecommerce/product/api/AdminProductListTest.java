package com.example.ecommerce.product.api;

import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryRepository;
import com.example.ecommerce.support.AuthTestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductListTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private ProductWorkflowHistoryRepository workflowHistoryRepository;

    @Autowired
    private AuthTestTokens authTestTokens;

    @BeforeEach
    void setUp() {
        workflowHistoryRepository.deleteAll();
        productSpuRepository.deleteAll();
        productSpuRepository.save(ProductSpuEntity.draft(2001L, "SPU-LIST-2001", "merchant-2001-product", 33L));
        productSpuRepository.save(ProductSpuEntity.draft(2002L, "SPU-LIST-2002", "merchant-2002-product", 33L));
    }

    @Test
    void lists_products_for_merchant_scope() throws Exception {
        mockMvc.perform(withBearer(get("/admin/products"), merchantAdminToken(9002L, 2001L))
                .param("merchantId", "2001")
                .param("page", "0")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.items[0].status").value("draft"))
            .andExpect(jsonPath("$.data.items[0].auditStatus").value("pending"))
            .andExpect(jsonPath("$.data.items[0].publishStatus").value("unpublished"));
    }

    @Test
    void merchant_admin_uses_own_merchant_scope_even_when_querying_another_merchant() throws Exception {
        productSpuRepository.save(ProductSpuEntity.draft(3001L, "SPU-LIST-3001", "merchant-3001-product", 44L));

        mockMvc.perform(withBearer(get("/admin/products"), merchantAdminToken(9003L, 3001L))
                .param("merchantId", "4001")
                .param("page", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].merchantId").value(3001))
            .andExpect(jsonPath("$.data.items[0].status").value("draft"))
            .andExpect(jsonPath("$.data.items[0].auditStatus").value("pending"))
            .andExpect(jsonPath("$.data.items[0].publishStatus").value("unpublished"));
    }

    @Test
    void platform_admin_filters_products_by_workflow_state() throws Exception {
        ProductSpuEntity approved = ProductSpuEntity.draft(2001L, "SPU-LIST-APPROVED", "approved-product", 33L);
        approved.submitForReview(LocalDateTime.now().minusHours(2));
        approved.approve(9001L, "ok", LocalDateTime.now().minusHours(1));
        productSpuRepository.save(approved);

        mockMvc.perform(withBearer(get("/admin/products"), platformAdminToken(9001L, 2001L))
                .param("auditStatus", "approved"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].auditStatus").value("approved"));
    }

    @Test
    void sorts_products_by_title_ascending() throws Exception {
        workflowHistoryRepository.deleteAll();
        productSpuRepository.deleteAll();
        productSpuRepository.save(ProductSpuEntity.draft(2001L, "SPU-LIST-B", "zebra-product", 33L));
        productSpuRepository.save(ProductSpuEntity.draft(2001L, "SPU-LIST-A", "alpha-product", 33L));

        mockMvc.perform(withBearer(get("/admin/products"), merchantAdminToken(9002L, 2001L))
                .param("sort", "title_asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].title").value("alpha-product"));
    }

    @Test
    void filters_products_by_keyword() throws Exception {
        mockMvc.perform(withBearer(get("/admin/products"), merchantAdminToken(9002L, 2001L))
                .param("keyword", "2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void rejects_unknown_admin_product_sort() throws Exception {
        mockMvc.perform(withBearer(get("/admin/products"), platformAdminToken(9001L, 2001L))
                .param("sort", "random"))
            .andExpect(status().isBadRequest());
    }

    private String platformAdminToken(long userId, long merchantId) {
        return authTestTokens.platformAdminToken(userId, merchantId);
    }

    private String merchantAdminToken(long userId, long merchantId) {
        return authTestTokens.merchantAdminToken(userId, merchantId);
    }

    private MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder requestBuilder, String token) {
        return requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
