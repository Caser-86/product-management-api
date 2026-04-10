# JWT Auth Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace raw identity headers with Bearer JWT authentication, add a lightweight login endpoint, and preserve the existing merchant/platform authorization behavior.

**Architecture:** Introduce Spring Security with a custom JWT authentication filter that restores the existing `AuthContext` into the service layer. Add a small auth module for configuration-backed local accounts, JWT signing/validation, and the `/auth/login` endpoint while keeping storefront reads anonymous and Swagger Bearer-aware.

**Tech Stack:** Spring Boot 3, Spring Security 6, Spring MVC, Spring Validation, Springdoc OpenAPI, JUnit 5, MockMvc, H2/MySQL-compatible configuration

---

## File Structure

### Create

- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthProperties.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUser.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUserProvider.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/JwtTokenService.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/LoginRequest.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/LoginResponse.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthController.java`
- `backend/src/main/java/com/example/ecommerce/shared/config/SecurityConfiguration.java`
- `backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilterTest.java`
- `backend/src/test/java/com/example/ecommerce/support/AuthTestTokens.java`

### Modify

- `backend/build.gradle.kts`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/example/ecommerce/shared/api/ErrorCode.java`
- `backend/src/main/java/com/example/ecommerce/shared/api/GlobalExceptionHandler.java`
- `backend/src/main/java/com/example/ecommerce/shared/api/OpenApiConfiguration.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthContextHolder.java`
- `backend/src/main/java/com/example/ecommerce/shared/config/WebMvcConfiguration.java`
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/shared/api/GlobalExceptionHandlerTest.java`
- `backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java`
- `backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java`
- `backend/README.md`

### Delete

- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthInterceptor.java`
- `backend/src/test/java/com/example/ecommerce/shared/auth/AuthInterceptorTest.java`

## Task 1: Add Security Dependencies And Auth Configuration Models

**Files:**
- Modify: `backend/build.gradle.kts`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthProperties.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUser.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUserProvider.java`
- Test: `backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java`

- [ ] **Step 1: Write the failing configuration-backed login test**

```java
@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void logs_in_with_configured_platform_user() throws Exception {
        mockMvc.perform(post("/auth/login")
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
            .andExpect(jsonPath("$.data.accessToken").isString());
    }
}
```

- [ ] **Step 2: Run the login test to verify it fails**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.AuthControllerTest.logs_in_with_configured_platform_user' --no-daemon
```

Expected:

- `FAIL` because `/auth/login` and the auth configuration model do not exist yet

- [ ] **Step 3: Add Spring Security and JWT library dependencies**

Update `backend/build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

- [ ] **Step 4: Add JWT and local-user configuration to `application.yml`**

Append this structure to `backend/src/main/resources/application.yml`:

```yaml
app:
  auth:
    jwt:
      issuer: ${APP_AUTH_JWT_ISSUER:product-management-api}
      secret: ${APP_AUTH_JWT_SECRET:change-me-for-local-development-only}
      access-token-ttl-minutes: ${APP_AUTH_JWT_ACCESS_TOKEN_TTL_MINUTES:60}
    users:
      - username: platform-admin
        password: platform-secret
        user-id: 9001
        role: PLATFORM_ADMIN
        merchant-id: 2001
      - username: merchant-admin
        password: merchant-secret
        user-id: 9002
        role: MERCHANT_ADMIN
        merchant-id: 2001
```

- [ ] **Step 5: Add configuration types and user provider**

Create `backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUser.java`:

```java
package com.example.ecommerce.shared.auth;

public record LocalAuthUser(
    String username,
    String password,
    Long userId,
    AuthRole role,
    Long merchantId
) {
}
```

Create `backend/src/main/java/com/example/ecommerce/shared/auth/AuthProperties.java`:

```java
package com.example.ecommerce.shared.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final List<User> users = new ArrayList<>();

    public Jwt getJwt() {
        return jwt;
    }

    public List<User> getUsers() {
        return users;
    }
```

Add the remaining nested property classes in the same file:

```java
    public static class Jwt {
        private String issuer;
        private String secret;
        private long accessTokenTtlMinutes;

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getAccessTokenTtlMinutes() { return accessTokenTtlMinutes; }
        public void setAccessTokenTtlMinutes(long accessTokenTtlMinutes) {
            this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        }
    }

    public static class User {
        private String username;
        private String password;
        private Long userId;
        private AuthRole role;
        private Long merchantId;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public AuthRole getRole() { return role; }
        public void setRole(AuthRole role) { this.role = role; }
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    }
}
```

Create `backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUserProvider.java`:

```java
package com.example.ecommerce.shared.auth;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LocalAuthUserProvider {

    private final AuthProperties authProperties;

    public LocalAuthUserProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public Optional<LocalAuthUser> findByUsername(String username) {
        return authProperties.getUsers().stream()
            .filter(user -> user.getUsername().equals(username))
            .findFirst()
            .map(user -> new LocalAuthUser(
                user.getUsername(),
                user.getPassword(),
                user.getUserId(),
                user.getRole(),
                user.getMerchantId()
            ));
    }
}
```

- [ ] **Step 6: Re-run the login test**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.AuthControllerTest.logs_in_with_configured_platform_user' --no-daemon
```

Expected:

- `FAIL` because the endpoint and token service still do not exist
- the failure should now be about missing controller/security wiring rather than missing configuration classes

- [ ] **Step 7: Commit Task 1**

```powershell
git add backend/build.gradle.kts `
        backend/src/main/resources/application.yml `
        backend/src/main/java/com/example/ecommerce/shared/auth/AuthProperties.java `
        backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUser.java `
        backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUserProvider.java `
        backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java
git commit -m "build: add jwt auth dependencies and config models"
```

## Task 2: Add Login Endpoint And JWT Token Service

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/JwtTokenService.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/LoginRequest.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/LoginResponse.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/api/ErrorCode.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/api/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java`

- [ ] **Step 1: Expand the login test with invalid credential coverage**

Add this test to `AuthControllerTest`:

```java
@Test
void rejects_invalid_credentials() throws Exception {
    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "platform-admin",
                  "password": "wrong-secret"
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
}
```

- [ ] **Step 2: Run the auth controller test class and verify it fails**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.AuthControllerTest' --no-daemon
```

Expected:

- `FAIL` because `/auth/login` still does not exist

- [ ] **Step 3: Add DTOs and JWT token service**

Create `backend/src/main/java/com/example/ecommerce/shared/auth/LoginRequest.java`:

```java
package com.example.ecommerce.shared.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "username is required")
    String username,
    @NotBlank(message = "password is required")
    String password
) {
}
```

Create `backend/src/main/java/com/example/ecommerce/shared/auth/LoginResponse.java`:

```java
package com.example.ecommerce.shared.auth;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserInfo user
) {
    public record UserInfo(
        Long userId,
        String username,
        String role,
        Long merchantId
    ) {
    }
}
```

Create `backend/src/main/java/com/example/ecommerce/shared/auth/JwtTokenService.java`:

```java
package com.example.ecommerce.shared.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtTokenService {

    private final AuthProperties authProperties;
    private final SecretKey signingKey;

    public JwtTokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.signingKey = Keys.hmacShaKeyFor(authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(LocalAuthUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(authProperties.getJwt().getAccessTokenTtlMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
            .subject(user.username())
            .issuer(authProperties.getJwt().getIssuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim("uid", user.userId())
            .claim("role", user.role().name())
            .claim("merchantId", user.merchantId())
            .signWith(signingKey)
            .compact();
    }

    public long expiresInSeconds() {
        return authProperties.getJwt().getAccessTokenTtlMinutes() * 60;
    }
}
```

- [ ] **Step 4: Add error code and login controller**

Add this enum entry to `backend/src/main/java/com/example/ecommerce/shared/api/ErrorCode.java`:

```java
AUTH_INVALID_CREDENTIALS(401, "AUTH_INVALID_CREDENTIALS", "invalid credentials"),
```

Create `backend/src/main/java/com/example/ecommerce/shared/auth/AuthController.java`:

```java
package com.example.ecommerce.shared.auth;

import com.example.ecommerce.shared.api.ApiResponse;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LocalAuthUserProvider userProvider;
    private final JwtTokenService jwtTokenService;

    public AuthController(LocalAuthUserProvider userProvider, JwtTokenService jwtTokenService) {
        this.userProvider = userProvider;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LocalAuthUser user = userProvider.findByUsername(request.username())
            .filter(candidate -> candidate.password().equals(request.password()))
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials"));

        return ApiResponse.success(new LoginResponse(
            jwtTokenService.issueToken(user),
            "Bearer",
            jwtTokenService.expiresInSeconds(),
            new LoginResponse.UserInfo(
                user.userId(),
                user.username(),
                user.role().name(),
                user.merchantId()
            )
        ));
    }
}
```

- [ ] **Step 5: Re-run the auth controller tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.AuthControllerTest' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit Task 2**

```powershell
git add backend/src/main/java/com/example/ecommerce/shared/auth/JwtTokenService.java `
        backend/src/main/java/com/example/ecommerce/shared/auth/LoginRequest.java `
        backend/src/main/java/com/example/ecommerce/shared/auth/LoginResponse.java `
        backend/src/main/java/com/example/ecommerce/shared/auth/AuthController.java `
        backend/src/main/java/com/example/ecommerce/shared/api/ErrorCode.java `
        backend/src/main/java/com/example/ecommerce/shared/api/GlobalExceptionHandler.java `
        backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java
git commit -m "feat: add local login and jwt issuance"
```

## Task 3: Replace Header Authentication With Spring Security JWT Filter

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/config/SecurityConfiguration.java`
- Create: `backend/src/test/java/com/example/ecommerce/support/AuthTestTokens.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthContextHolder.java`
- Delete: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthInterceptor.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/config/WebMvcConfiguration.java`
- Delete: `backend/src/test/java/com/example/ecommerce/shared/auth/AuthInterceptorTest.java`
- Create: `backend/src/test/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilterTest.java`

- [ ] **Step 1: Write the failing security tests**

Create `backend/src/test/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilterTest.java`:

```java
@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestTokens authTestTokens;

    @Test
    void rejects_missing_token_for_admin_endpoint() throws Exception {
        mockMvc.perform(get("/admin/products"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void allows_bearer_token_for_admin_endpoint() throws Exception {
        mockMvc.perform(get("/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authTestTokens.platformAdminToken()))
            .andExpect(status().isOk());
    }

    @Test
    void allows_anonymous_storefront_access() throws Exception {
        mockMvc.perform(get("/products"))
            .andExpect(status().isOk());
    }

    @Test
    void rejects_expired_token_for_admin_endpoint() throws Exception {
        mockMvc.perform(get("/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authTestTokens.expiredPlatformAdminToken()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void allows_anonymous_openapi_access() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run the JWT filter tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.JwtAuthenticationFilterTest' --no-daemon
```

Expected:

- `FAIL` because the filter chain and test token helper do not exist yet

- [ ] **Step 3: Add test token helper for valid and expired JWTs**

Create `backend/src/test/java/com/example/ecommerce/support/AuthTestTokens.java`:

```java
package com.example.ecommerce.support;

import com.example.ecommerce.shared.auth.AuthProperties;
import com.example.ecommerce.shared.auth.AuthRole;
import com.example.ecommerce.shared.auth.LocalAuthUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class AuthTestTokens {

    private final AuthProperties authProperties;

    public AuthTestTokens(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String platformAdminToken() {
        return issueToken(new LocalAuthUser("platform-admin", "platform-secret", 9001L, AuthRole.PLATFORM_ADMIN, 2001L), Instant.now().plusSeconds(3600));
    }

    public String expiredPlatformAdminToken() {
        return issueToken(new LocalAuthUser("platform-admin", "platform-secret", 9001L, AuthRole.PLATFORM_ADMIN, 2001L), Instant.now().minusSeconds(60));
    }

    private String issueToken(LocalAuthUser user, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
            .subject(user.username())
            .issuer(authProperties.getJwt().getIssuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim("uid", user.userId())
            .claim("role", user.role().name())
            .claim("merchantId", user.merchantId())
            .signWith(key)
            .compact();
    }
}
```

- [ ] **Step 4: Add JWT parsing and authentication filter**

Create `backend/src/main/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilter.java`:

```java
package com.example.ecommerce.shared.auth;

import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthProperties authProperties;

    public JwtAuthenticationFilter(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                Claims claims = parseClaims(header.substring(7));
                AuthRole role = AuthRole.parse(String.valueOf(claims.get("role")));
                Long userId = Long.valueOf(String.valueOf(claims.get("uid")));
                Long merchantId = Long.valueOf(String.valueOf(claims.get("merchantId")));

                AuthContext context = new AuthContext(userId, role, merchantId);
                AuthContextHolder.set(context);
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        claims.getSubject(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                    )
                );
            }

            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        try {
            return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(authProperties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED, "invalid bearer token");
        }
    }
}
```

- [ ] **Step 5: Add filter chain and remove old interceptor**

Create `backend/src/main/java/com/example/ecommerce/shared/config/SecurityConfiguration.java`:

```java
package com.example.ecommerce.shared.config;

import com.example.ecommerce.shared.auth.AuthProperties;
import com.example.ecommerce.shared.auth.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties authProperties) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/products").permitAll()
                .requestMatchers("/admin/**", "/inventory/reservations/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(new JwtAuthenticationFilter(authProperties), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

Then:

- delete `AuthInterceptor.java`
- simplify `WebMvcConfiguration.java` so it no longer registers an auth interceptor
- delete `AuthInterceptorTest.java`

- [ ] **Step 6: Re-run the JWT filter tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.JwtAuthenticationFilterTest' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit Task 3**

```powershell
git add backend/src/main/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilter.java `
        backend/src/main/java/com/example/ecommerce/shared/config/SecurityConfiguration.java `
        backend/src/test/java/com/example/ecommerce/support/AuthTestTokens.java `
        backend/src/main/java/com/example/ecommerce/shared/auth/AuthContextHolder.java `
        backend/src/main/java/com/example/ecommerce/shared/config/WebMvcConfiguration.java `
        backend/src/test/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilterTest.java
git rm backend/src/main/java/com/example/ecommerce/shared/auth/AuthInterceptor.java `
       backend/src/test/java/com/example/ecommerce/shared/auth/AuthInterceptorTest.java
git commit -m "feat: protect endpoints with jwt security filter"
```

## Task 4: Add OpenAPI Bearer Auth And Developer-Facing Documentation Hooks

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/shared/api/OpenApiConfiguration.java`
- Modify: `backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java`

- [ ] **Step 1: Write the failing OpenAPI security assertion**

Extend `OpenApiDocumentationTest` with:

```java
@Test
void exposes_bearer_auth_scheme() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
}
```

- [ ] **Step 2: Run the OpenAPI doc test and verify it fails**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.api.OpenApiDocumentationTest' --no-daemon
```

Expected:

- `FAIL` because no Bearer auth scheme is declared yet

- [ ] **Step 3: Add Bearer scheme to the OpenAPI configuration**

Update `backend/src/main/java/com/example/ecommerce/shared/api/OpenApiConfiguration.java`:

```java
package com.example.ecommerce.shared.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI productManagementOpenApi() {
        return new OpenAPI()
            .info(
                new Info()
                    .title("Product Management API")
                    .description("Spring Boot ecommerce product management service")
                    .version("v1")
                    .contact(new Contact().name("Caser-86"))
            )
            .components(new Components().addSecuritySchemes(
                "bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

- [ ] **Step 4: Re-run the OpenAPI doc test**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.api.OpenApiDocumentationTest' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit Task 4**

```powershell
git add backend/src/main/java/com/example/ecommerce/shared/api/OpenApiConfiguration.java `
        backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java
git commit -m "docs: expose bearer auth in openapi"
```

## Task 5: Migrate Integration Tests From Identity Headers To Bearer Tokens

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/shared/api/GlobalExceptionHandlerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java`

- [ ] **Step 1: Extend the shared token helper for merchant-scoped test cases**

Update `backend/src/test/java/com/example/ecommerce/support/AuthTestTokens.java` with:

```java
    public String merchantAdminToken() {
        return merchantAdminToken(2001L);
    }

    public String merchantAdminToken(Long merchantId) {
        return issueToken(
            new LocalAuthUser("merchant-" + merchantId, "merchant-secret", 9100L + merchantId, AuthRole.MERCHANT_ADMIN, merchantId),
            Instant.now().plusSeconds(3600)
        );
    }
```

- [ ] **Step 2: Convert one controller test class first and verify the pattern**

For `AdminProductControllerTest`, replace direct header usage like:

```java
.header("X-User-Id", "9001")
.header("X-Role", "PLATFORM_ADMIN")
.header("X-Merchant-Id", "2001")
```

with:

```java
.header(HttpHeaders.AUTHORIZATION, "Bearer " + authTestTokens.platformAdminToken())
```

Merchant cases should use:

```java
.header(HttpHeaders.AUTHORIZATION, "Bearer " + authTestTokens.merchantAdminToken())
```

- [ ] **Step 3: Run the converted controller test to verify the pattern passes**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductControllerTest' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 4: Migrate the remaining integration test classes**

Apply the same conversion pattern to:

- `AdminProductListTest`
- `AdminProductWorkflowControllerTest`
- `InventoryControllerTest`
- `PricingControllerTest`
- `GlobalExceptionHandlerTest`
- `ProductManagementFlowTest`

For cross-merchant tests, use `authTestTokens.merchantAdminToken(3001L)` or platform tokens as appropriate.

- [ ] **Step 5: Run the migrated integration test batch**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test `
  --tests 'com.example.ecommerce.product.api.AdminProductControllerTest' `
  --tests 'com.example.ecommerce.product.api.AdminProductListTest' `
  --tests 'com.example.ecommerce.product.api.AdminProductWorkflowControllerTest' `
  --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest' `
  --tests 'com.example.ecommerce.pricing.api.PricingControllerTest' `
  --tests 'com.example.ecommerce.shared.api.GlobalExceptionHandlerTest' `
  --tests 'com.example.ecommerce.e2e.ProductManagementFlowTest' `
  --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit Task 5**

```powershell
git add backend/src/test/java/com/example/ecommerce/support/AuthTestTokens.java `
        backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java `
        backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java `
        backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java `
        backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java `
        backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java `
        backend/src/test/java/com/example/ecommerce/shared/api/GlobalExceptionHandlerTest.java `
        backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java
git commit -m "test: switch auth integration tests to bearer tokens"
```

## Task 6: Full Regression, README, And Final Cleanup

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README for JWT login and Swagger usage**

Add a new section to `backend/README.md`:

````md
## Authentication

Protected admin and inventory write endpoints now require `Authorization: Bearer <token>`.

Default local accounts:

- `platform-admin / platform-secret`
- `merchant-admin / merchant-secret`

Get a token:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"platform-admin","password":"platform-secret"}'
```

Use the returned token in Swagger through the `Authorize` button or send it manually as a Bearer token.
````

- [ ] **Step 2: Run the full backend test suite**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 3: Check for accidental leftovers**

Run:

```powershell
git status --short
git diff --stat
```

Expected:

- only JWT auth source, tests, and README changes are present

- [ ] **Step 4: Commit Task 6**

```powershell
git add backend/README.md
git commit -m "docs: describe jwt login workflow"
```

## Spec Coverage Check

- Bearer JWT replaces raw identity headers: covered by Task 3 and Task 5
- Lightweight login endpoint: covered by Task 1 and Task 2
- Configuration-defined local users: covered by Task 1
- Reuse of existing `AuthContext` authorization behavior: covered by Task 3
- Anonymous storefront and Swagger access: covered by Task 3 and Task 4
- Swagger Bearer support: covered by Task 4
- README and developer guidance: covered by Task 6
- Full regression pass: covered by Task 6

## Placeholder Scan

- No `TODO`, `TBD`, or deferred placeholders remain
- Each task includes concrete files, commands, and expected test outcomes
- Migration from header auth to Bearer auth is explicit rather than implied

## Type Consistency Check

- Security property names stay under `app.auth.jwt` and `app.auth.users`
- JWT claims use `uid`, `role`, and `merchantId` consistently across token issue and parse flow
- Test helper method names stay aligned with `platformAdminToken()` and `merchantAdminToken(...)`
- Login endpoint remains `POST /auth/login` throughout the plan
