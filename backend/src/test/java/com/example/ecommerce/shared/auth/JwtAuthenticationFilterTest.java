package com.example.ecommerce.shared.auth;

import com.example.ecommerce.support.AuthTestTokens;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.auth.jwt.issuer=product-management-api-test",
    "app.auth.jwt.secret=change-me-for-local-development-only",
    "app.auth.jwt.access-token-ttl-minutes=60",
    "app.auth.users[0].username=platform-admin",
    "app.auth.users[0].password=platform-secret",
    "app.auth.users[0].user-id=9001",
    "app.auth.users[0].role=PLATFORM_ADMIN",
    "app.auth.users[0].merchant-id=2001",
    "app.auth.users[1].username=merchant-admin",
    "app.auth.users[1].password=merchant-secret",
    "app.auth.users[1].user-id=9002",
    "app.auth.users[1].role=MERCHANT_ADMIN",
    "app.auth.users[1].merchant-id=2001"
})
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestTokens authTestTokens;

    @Autowired
    private AuthProperties authProperties;

    @Test
    void permitsAnonymousLoginRequests() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "platform-admin",
                      "password": "wrong-password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void permitsAnonymousStorefrontAndOpenApiRequests() throws Exception {
        mockMvc.perform(get("/products"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    @Test
    void ignoresInvalidAuthorizationHeaderForAnonymousRoutes() throws Exception {
        mockMvc.perform(get("/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer definitely-invalid"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer definitely-invalid"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer definitely-invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "platform-admin",
                      "password": "wrong-password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsAnonymousRequestsForProtectedRoutes() throws Exception {
        mockMvc.perform(get("/admin/products")
                .param("merchantId", "2001"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/inventory/reservations/res-1/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bizId": "biz-1",
                      "operatorType": "SYSTEM"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsProtectedAdminRouteWithValidBearerToken() throws Exception {
        mockMvc.perform(get("/admin/products")
                .param("merchantId", "2001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authTestTokens.platformAdminToken()))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsExpiredBearerTokenForProtectedAdminRoute() throws Exception {
        mockMvc.perform(get("/admin/products")
                .param("merchantId", "2001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authTestTokens.expiredPlatformAdminToken()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void authenticatesBearerTokenAndClearsThreadLocalsInFinally() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authProperties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/products");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authTestTokens.platformAdminToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<AuthContext> authContextDuringChain = new AtomicReference<>();
        AtomicReference<Authentication> authenticationDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            authContextDuringChain.set(AuthContextHolder.getRequired());
            authenticationDuringChain.set(SecurityContextHolder.getContext().getAuthentication());
        };

        filter.doFilter(request, response, chain);

        assertEquals(new AuthContext(9001L, AuthRole.PLATFORM_ADMIN, 2001L), authContextDuringChain.get());
        assertNotNull(authenticationDuringChain.get());
        assertEquals("9001", authenticationDuringChain.get().getName());
        assertNull(AuthContextHolder.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
