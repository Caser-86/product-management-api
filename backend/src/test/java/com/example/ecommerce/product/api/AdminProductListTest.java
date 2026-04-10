package com.example.ecommerce.product.api;

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

    @Test
    @WithMockUser(username = "merchant-2001")
    void lists_products_for_merchant_scope() throws Exception {
        mockMvc.perform(get("/admin/products").param("merchantId", "2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }
}
