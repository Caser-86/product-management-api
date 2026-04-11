package com.example.ecommerce.shared.auth;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
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
class AuthControllerTest {

    private static final String JWT_SECRET = "change-me-for-local-development-only";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Test
    void logs_in_with_default_platform_admin_credentials() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "platform-admin",
                      "password": "platform-secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.user.role").value("PLATFORM_ADMIN"))
            .andExpect(jsonPath("$.data.expiresIn").value(3600))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.data.accessToken");
        Claims claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(accessToken)
            .getPayload();

        org.junit.jupiter.api.Assertions.assertEquals(9001, claims.get("uid", Integer.class));
        org.junit.jupiter.api.Assertions.assertNull(claims.get("userId"));
    }

    @Test
    void rejects_login_when_credentials_are_invalid() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "platform-admin",
                      "password": "wrong-password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void disabled_user_is_rejected() throws Exception {
        disableUser("merchant-admin");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "merchant-admin",
                      "password": "merchant-secret"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    private void disableUser(String username) {
        AuthUserEntity user = authUserRepository.findByUsername(username).orElseThrow();
        setField(user, "status", "disabled");
        authUserRepository.save(user);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
