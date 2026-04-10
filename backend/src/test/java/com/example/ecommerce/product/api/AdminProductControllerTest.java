package com.example.ecommerce.product.api;

import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
}
