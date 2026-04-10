package com.example.ecommerce.product.api;

import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AdminProductListTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @BeforeEach
    void setUp() {
        productSpuRepository.deleteAll();
        productSpuRepository.save(ProductSpuEntity.draft(2001L, "SPU-LIST-2001", "merchant-2001-product", 33L));
        productSpuRepository.save(ProductSpuEntity.draft(2002L, "SPU-LIST-2002", "merchant-2002-product", 33L));
    }

    @Test
    @WithMockUser(username = "merchant-2001")
    void lists_products_for_merchant_scope() throws Exception {
        mockMvc.perform(get("/admin/products")
                .param("merchantId", "2001")
                .param("page", "0")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1));
    }
}
