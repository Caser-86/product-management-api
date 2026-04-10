# Auth, Inventory Ledger, and Price Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add request-header based authorization, inventory ledger persistence and history, and automatic due price schedule execution to the existing product management API.

**Architecture:** Keep the current Spring Boot modular-monolith structure, add a lightweight web-layer auth context with merchant-scope enforcement, write immutable inventory ledger rows in the same transaction as stock mutations, and centralize scheduled price execution in `PricingService` behind a scheduler-driven batch entrypoint.

**Tech Stack:** Spring Boot 3.3, Spring MVC interceptor, Spring scheduling, Spring Data JPA, Flyway, JUnit 5, MockMvc, H2

---

## File Map

### New Files

- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthContext.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthRole.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthContextHolder.java`
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthInterceptor.java`
- `backend/src/main/java/com/example/ecommerce/shared/config/WebMvcConfiguration.java`
- `backend/src/main/java/com/example/ecommerce/shared/config/SchedulingConfiguration.java`
- `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryLedgerRepository.java`
- `backend/src/main/java/com/example/ecommerce/pricing/application/PriceScheduleRunner.java`
- `backend/src/test/java/com/example/ecommerce/shared/auth/AuthInterceptorTest.java`

### Modified Files

- `backend/src/main/java/com/example/ecommerce/shared/api/ErrorCode.java`
- `backend/src/main/java/com/example/ecommerce/shared/api/BusinessException.java`
- `backend/src/main/java/com/example/ecommerce/shared/api/GlobalExceptionHandler.java`
- `backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java`
- `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java`
- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`
- `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- `backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java`
- `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
- `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleRepository.java`
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`
- `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleJobTest.java`
- `backend/README.md`

### Existing Files To Read During Implementation

- `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProductController.java`
- `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuEntity.java`
- `backend/src/main/java/com/example/ecommerce/product/domain/ProductSkuEntity.java`
- `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryReservationEntity.java`
- `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleEntity.java`

## Task 1: Add Authentication Context and Interceptor

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthContext.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthRole.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthContextHolder.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthInterceptor.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/config/WebMvcConfiguration.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/api/ErrorCode.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/api/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/example/ecommerce/shared/auth/AuthInterceptorTest.java`

- [ ] **Step 1: Write the failing authentication tests**

```java
package com.example.ecommerce.shared.auth;

import com.example.ecommerce.search.api.StorefrontProductController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StorefrontProductController.class)
@Import({AuthInterceptor.class, WebMvcConfiguration.class})
class AuthInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsMissingHeadersForProtectedAdminEndpoint() throws Exception {
        mockMvc.perform(get("/admin/products").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnonymousAccessForStorefrontEndpoint() throws Exception {
        mockMvc.perform(get("/products").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.shared.auth.AuthInterceptorTest" --no-daemon`

Expected: FAIL because the auth interceptor classes and unauthorized mapping do not exist yet.

- [ ] **Step 3: Write the minimal auth context and interceptor**

```java
package com.example.ecommerce.shared.auth;

public record AuthContext(Long userId, AuthRole role, Long merchantId) {

    public boolean isPlatformAdmin() {
        return role == AuthRole.PLATFORM_ADMIN;
    }
}
```

```java
package com.example.ecommerce.shared.auth;

public enum AuthRole {
    PLATFORM_ADMIN,
    MERCHANT_ADMIN;

    public static AuthRole parse(String raw) {
        try {
            return AuthRole.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("invalid role");
        }
    }
}
```

```java
package com.example.ecommerce.shared.auth;

public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthContext context) {
        HOLDER.set(context);
    }

    public static AuthContext getRequired() {
        AuthContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("auth context missing");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
```

```java
package com.example.ecommerce.shared.auth;

import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!requiresAuth(request.getRequestURI())) {
            return true;
        }

        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-Role");
        String merchantId = request.getHeader("X-Merchant-Id");
        if (userId == null || role == null || merchantId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED, "authentication headers are required");
        }

        AuthContextHolder.set(new AuthContext(Long.valueOf(userId), AuthRole.parse(role), Long.valueOf(merchantId)));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }

    private boolean requiresAuth(String path) {
        return path.startsWith("/admin/")
            || path.startsWith("/inventory/reservations")
            || path.startsWith("/admin/price-schedules");
    }
}
```

```java
package com.example.ecommerce.shared.config;

import com.example.ecommerce.shared.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfiguration(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor);
    }
}
```

- [ ] **Step 4: Add auth error codes and handler mapping**

```java
AUTH_UNAUTHENTICATED,
AUTH_INVALID_ROLE,
AUTH_MERCHANT_SCOPE_DENIED,
```

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
    HttpStatus status = switch (ex.getCode()) {
        case AUTH_UNAUTHENTICATED, AUTH_INVALID_ROLE -> HttpStatus.UNAUTHORIZED;
        case AUTH_MERCHANT_SCOPE_DENIED -> HttpStatus.FORBIDDEN;
        default -> HttpStatus.BAD_REQUEST;
    };
    return ResponseEntity.status(status).body(ApiResponse.failure(ex.getCode().name(), ex.getMessage()));
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.shared.auth.AuthInterceptorTest" --no-daemon`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/shared/auth backend/src/main/java/com/example/ecommerce/shared/config backend/src/main/java/com/example/ecommerce/shared/api/ErrorCode.java backend/src/main/java/com/example/ecommerce/shared/api/GlobalExceptionHandler.java backend/src/test/java/com/example/ecommerce/shared/auth/AuthInterceptorTest.java
git commit -m "feat: add header-based auth interceptor"
```

## Task 2: Enforce Merchant Scope in Product Flows

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java`
- Test: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java`
- Test: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`

- [ ] **Step 1: Write the failing scope tests**

```java
@Test
void merchantAdminCannotQueryAnotherMerchantProducts() throws Exception {
    mockMvc.perform(get("/admin/products")
            .header("X-User-Id", "9002")
            .header("X-Role", "MERCHANT_ADMIN")
            .header("X-Merchant-Id", "3001")
            .param("merchantId", "4001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
}

@Test
void merchantAdminCannotUpdateOtherMerchantProduct() throws Exception {
    mockMvc.perform(put("/admin/products/{productId}", otherMerchantProductId)
            .header("X-User-Id", "9002")
            .header("X-Role", "MERCHANT_ADMIN")
            .header("X-Merchant-Id", "3001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"renamed","categoryId":10}
                """))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.product.api.AdminProduct*" --no-daemon`

Expected: FAIL because services currently trust request merchant inputs and do not enforce merchant ownership.

- [ ] **Step 3: Implement effective merchant resolution**

```java
private Long effectiveMerchantId(Long requestedMerchantId) {
    AuthContext auth = AuthContextHolder.getRequired();
    if (auth.isPlatformAdmin()) {
        return requestedMerchantId;
    }
    return auth.merchantId();
}
```

```java
private void assertMerchantScope(Long resourceMerchantId) {
    AuthContext auth = AuthContextHolder.getRequired();
    if (!auth.isPlatformAdmin() && !auth.merchantId().equals(resourceMerchantId)) {
        throw new BusinessException(ErrorCode.AUTH_MERCHANT_SCOPE_DENIED, "merchant scope denied");
    }
}
```

- [ ] **Step 4: Update product create, list, update, and delete paths**

```java
public ProductListResponse list(Long merchantId, int page, int pageSize) {
    Long effectiveMerchantId = effectiveMerchantId(merchantId);
    Page<ProductSpuEntity> products = effectiveMerchantId == null
        ? productSpuRepository.findByStatusNot("deleted", PageRequest.of(page - 1, pageSize))
        : productSpuRepository.findByMerchantIdAndStatusNot(effectiveMerchantId, "deleted", PageRequest.of(page - 1, pageSize));
    return mapList(products);
}
```

```java
public ProductResponse update(Long productId, ProductUpdateRequest request) {
    ProductSpuEntity spu = productSpuRepository.findById(productId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
    assertMerchantScope(spu.getMerchantId());
    spu.updateBasicInfo(request.title(), request.categoryId());
    return ProductResponse.from(spu);
}
```

- [ ] **Step 5: Run targeted tests to verify they pass**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.product.api.AdminProduct*" --no-daemon`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java
git commit -m "feat: enforce merchant scope for admin products"
```

## Task 3: Protect Inventory and Pricing Mutations

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
- Test: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
- Test: `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`

- [ ] **Step 1: Write the failing protected-endpoint tests**

```java
@Test
void reserveRequiresAuthenticationHeaders() throws Exception {
    mockMvc.perform(post("/inventory/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"idempotencyKey":"res-1","bizId":"order-1","items":[{"skuId":1,"quantity":1}]}
                """))
        .andExpect(status().isUnauthorized());
}

@Test
void merchantAdminCannotAdjustInventoryForOtherMerchantSku() throws Exception {
    mockMvc.perform(post("/admin/skus/{skuId}/inventory/adjustments", foreignSkuId)
            .header("X-User-Id", "9002")
            .header("X-Role", "MERCHANT_ADMIN")
            .header("X-Merchant-Id", "3001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"delta":5,"reason":"restock","operatorId":9002}
                """))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.inventory.api.InventoryControllerTest" --tests "com.example.ecommerce.pricing.api.PricingControllerTest" --no-daemon`

Expected: FAIL because protected endpoints do not yet enforce merchant scope and auth paths.

- [ ] **Step 3: Add merchant-scope assertions to inventory and pricing services**

```java
private void assertSkuScope(Long skuId) {
    AuthContext auth = AuthContextHolder.getRequired();
    Long merchantId = productSkuRepository.findById(skuId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "sku not found"))
        .getMerchantId();
    if (!auth.isPlatformAdmin() && !auth.merchantId().equals(merchantId)) {
        throw new BusinessException(ErrorCode.AUTH_MERCHANT_SCOPE_DENIED, "merchant scope denied");
    }
}
```

- [ ] **Step 4: Run targeted tests to verify they pass**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.inventory.api.InventoryControllerTest" --tests "com.example.ecommerce.pricing.api.PricingControllerTest" --no-daemon`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java
git commit -m "feat: protect inventory and pricing operations"
```

## Task 4: Persist Inventory Ledger Rows

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryLedgerRepository.java`
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- Test: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`

- [ ] **Step 1: Write the failing ledger tests**

```java
@Test
void reserveCreatesInventoryLedgerEntry() throws Exception {
    mockMvc.perform(post("/inventory/reservations")
            .header("X-User-Id", "9001")
            .header("X-Role", "PLATFORM_ADMIN")
            .header("X-Merchant-Id", "2001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"idempotencyKey":"reserve-1","bizId":"order-1","items":[{"skuId":20001,"quantity":2}]}
                """))
        .andExpect(status().isOk());

    assertThat(inventoryLedgerRepository.findAll()).hasSize(1);
    assertThat(inventoryLedgerRepository.findAll().get(0).getDeltaAvailable()).isEqualTo(-2);
}
```

- [ ] **Step 2: Run targeted inventory tests to verify they fail**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.inventory.api.InventoryControllerTest" --no-daemon`

Expected: FAIL because no ledger repository exists and no ledger rows are written.

- [ ] **Step 3: Add repository and save ledger rows in the same transaction**

```java
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntity, Long> {

    List<InventoryLedgerEntity> findBySkuIdOrderByIdDesc(Long skuId);
}
```

```java
inventoryLedgerRepository.save(
    InventoryLedgerEntity.of(item.skuId(), balance.getMerchantId(), "reserve", bizId, -item.quantity(), item.quantity())
);
```

```java
inventoryLedgerRepository.save(
    InventoryLedgerEntity.of(reservation.getSkuId(), balance.getMerchantId(), "confirm", bizId, 0, -reservation.getQuantity())
);
```

```java
inventoryLedgerRepository.save(
    InventoryLedgerEntity.of(skuId, balance.getMerchantId(), "adjust", "manual-adjust", delta, 0)
);
```

- [ ] **Step 4: Run targeted inventory tests to verify they pass**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.inventory.api.InventoryControllerTest" --no-daemon`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryLedgerRepository.java backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java
git commit -m "feat: persist inventory ledger entries"
```

## Task 5: Expose Inventory History Endpoint

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- Test: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`

- [ ] **Step 1: Write the failing history-endpoint test**

```java
@Test
void inventoryHistoryReturnsLedgerEntries() throws Exception {
    mockMvc.perform(get("/admin/skus/{skuId}/inventory/history", 20001L)
            .header("X-User-Id", "9001")
            .header("X-Role", "PLATFORM_ADMIN")
            .header("X-Merchant-Id", "2001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
}
```

- [ ] **Step 2: Run targeted inventory tests to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.inventory.api.InventoryControllerTest" --no-daemon`

Expected: FAIL because the history endpoint does not exist yet.

- [ ] **Step 3: Add controller and service history mapping**

```java
@GetMapping("/admin/skus/{skuId}/inventory/history")
public ApiResponse<Map<String, Object>> history(@PathVariable Long skuId) {
    return ApiResponse.success(inventoryService.history(skuId));
}
```

```java
public Map<String, Object> history(Long skuId) {
    assertSkuScope(skuId);
    List<Map<String, Object>> items = inventoryLedgerRepository.findBySkuIdOrderByIdDesc(skuId).stream()
        .map(ledger -> Map.<String, Object>of(
            "bizType", ledger.getBizType(),
            "bizId", ledger.getBizId(),
            "deltaAvailable", ledger.getDeltaAvailable(),
            "deltaReserved", ledger.getDeltaReserved(),
            "createdAt", ledger.getCreatedAt().toString()
        ))
        .toList();
    return Map.of("items", items);
}
```

- [ ] **Step 4: Run targeted inventory tests to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.inventory.api.InventoryControllerTest" --no-daemon`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java
git commit -m "feat: add inventory history endpoint"
```

## Task 6: Enable Due Schedule Batch Execution

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/shared/config/SchedulingConfiguration.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleRepository.java`
- Test: `backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleJobTest.java`

- [ ] **Step 1: Write the failing due-schedule tests**

```java
@Test
void appliesOnlyDuePendingSchedules() {
    pricingService.applyDueSchedules(10);

    assertThat(priceHistoryRepository.findBySkuIdOrderByIdDesc(skuId)).hasSize(1);
    assertThat(priceScheduleRepository.findById(dueScheduleId)).get().extracting(PriceScheduleEntity::getStatus)
        .isEqualTo("applied");
}
```

- [ ] **Step 2: Run targeted pricing tests to verify they fail**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.pricing.application.PriceScheduleJobTest" --no-daemon`

Expected: FAIL because no due-schedule batch method exists.

- [ ] **Step 3: Add repository query and pricing batch method**

```java
public interface PriceScheduleRepository extends JpaRepository<PriceScheduleEntity, Long> {

    List<PriceScheduleEntity> findTop20ByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(String status, LocalDateTime effectiveAt);
}
```

```java
@Transactional
public int applyDueSchedules(int limit) {
    List<PriceScheduleEntity> dueSchedules = priceScheduleRepository
        .findTop20ByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc("pending", LocalDateTime.now());

    int applied = 0;
    for (PriceScheduleEntity schedule : dueSchedules.stream().limit(limit).toList()) {
        applyScheduledPrice(schedule.getId());
        applied++;
    }
    return applied;
}
```

```java
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
```

- [ ] **Step 4: Run targeted pricing tests to verify they pass**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.pricing.application.PriceScheduleJobTest" --no-daemon`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/shared/config/SchedulingConfiguration.java backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleRepository.java backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleJobTest.java
git commit -m "feat: add due price schedule batch execution"
```

## Task 7: Add Scheduler Runner

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/pricing/application/PriceScheduleRunner.java`
- Modify: `backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleJobTest.java`

- [ ] **Step 1: Write the failing scheduler-runner test**

```java
@Test
void schedulerDelegatesToPricingServiceBatchExecution() {
    PriceScheduleRunner runner = new PriceScheduleRunner(pricingService);

    int applied = runner.runDueSchedules();

    assertThat(applied).isEqualTo(1);
}
```

- [ ] **Step 2: Run targeted pricing tests to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.pricing.application.PriceScheduleJobTest" --no-daemon`

Expected: FAIL because the scheduler runner does not exist.

- [ ] **Step 3: Add scheduled runner component**

```java
package com.example.ecommerce.pricing.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceScheduleRunner {

    private final PricingService pricingService;

    public PriceScheduleRunner(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @Scheduled(fixedDelayString = "${pricing.schedule.fixed-delay-ms:30000}")
    public int runDueSchedules() {
        return pricingService.applyDueSchedules(20);
    }
}
```

- [ ] **Step 4: Run targeted pricing tests to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.pricing.application.PriceScheduleJobTest" --no-daemon`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/pricing/application/PriceScheduleRunner.java backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleJobTest.java
git commit -m "feat: add scheduled price runner"
```

## Task 8: Update Docs and Run Full Verification

**Files:**
- Modify: `backend/README.md`
- Test: `backend/src/test/java/com/example/ecommerce/shared/auth/AuthInterceptorTest.java`
- Test: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
- Test: `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
- Test: `backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleJobTest.java`

- [ ] **Step 1: Write the failing documentation expectation**

```markdown
## Authentication

Protected endpoints require:

- `X-User-Id`
- `X-Role`
- `X-Merchant-Id`
```

- [ ] **Step 2: Update README with auth and scheduling behavior**

```markdown
## Authentication

Admin, inventory write, and pricing write endpoints require request headers:

- `X-User-Id`
- `X-Role`
- `X-Merchant-Id`

`PLATFORM_ADMIN` may operate across merchants.
`MERCHANT_ADMIN` is restricted to its own merchant.

## Price Scheduling

Due price schedules are applied automatically by the in-process scheduler.
```

- [ ] **Step 3: Run the full backend test suite**

Run: `.\gradlew.bat clean test --no-daemon`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add backend/README.md backend/src/test/java/com/example/ecommerce/shared/auth/AuthInterceptorTest.java backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleJobTest.java
git commit -m "docs: document auth and scheduling behavior"
```

## Self-Review

- Spec coverage:
  - header-based auth and merchant scope are covered by Tasks 1 to 3
  - inventory ledger persistence and history endpoint are covered by Tasks 4 and 5
  - due-schedule batch execution and scheduler runner are covered by Tasks 6 and 7
  - docs and full verification are covered by Task 8
- Placeholder scan:
  - no `TODO`, `TBD`, or “similar to previous task” placeholders remain
- Type consistency:
  - auth types, service method names, and repository query names are consistent across later tasks
