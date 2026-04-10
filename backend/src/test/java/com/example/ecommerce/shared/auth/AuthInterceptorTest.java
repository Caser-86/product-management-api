package com.example.ecommerce.shared.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AuthInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsMissingHeadersForProtectedAdminEndpoint() throws Exception {
        mockMvc.perform(get("/admin/products")
                .param("merchantId", "2001"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnonymousAccessForStorefrontEndpoint() throws Exception {
        mockMvc.perform(get("/products"))
            .andExpect(status().isOk());
    }
}
