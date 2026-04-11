# Storefront Search Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add platform-admin-only APIs to refresh one storefront search projection row or synchronously rebuild the full storefront projection with actionable summary output.

**Architecture:** Keep `ProductSearchProjector` as the single-product projection writer and add an admin orchestration layer that pages through products, calls projector refresh per product, captures failures, and returns rebuild statistics. Reuse the existing JWT security and service-layer auth patterns so rebuild operations behave like the rest of the admin API surface.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Security, Spring Data JPA, MySQL-backed projection table, JUnit 5, MockMvc

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java`
  Clarify single-product refresh behavior and expose enough signal for admin rebuild responses.
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
  Add ordered paging query support for full rebuild orchestration.
- `backend/src/main/java/com/example/ecommerce/shared/api/ErrorCode.java`
  Add any rebuild-specific admin error codes only if the existing set is insufficient.
- `backend/src/main/java/com/example/ecommerce/shared/api/OpenApiConfiguration.java`
  Register new admin rebuild endpoints in generated docs if tags or summaries need updates.
- `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`
  Extend storefront regression coverage so rebuild operations do not break visibility rules.
- `backend/README.md`
  Document refresh/rebuild endpoints and operator usage.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/search/api/StorefrontSearchAdminController.java`
  Admin endpoints for single refresh and full rebuild.
- `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchAdminService.java`
  Batch rebuild orchestration, timing, and failure capture.
- `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProjectionRefreshResponse.java`
  Response DTO for single-product refresh.
- `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProjectionRebuildResponse.java`
  Summary DTO for full rebuild, including nested failure items.

### New test files to create

- `backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java`
  End-to-end MockMvc coverage for auth, refresh, rebuild, and failure summaries.

## Task 1: Add repository and projector support for admin rebuild

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
- Test: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java`

- [ ] **Step 1: Write the failing test for rebuilding a missing projection row**

```java
@Test
void platform_admin_refreshes_missing_projection_row() throws Exception {
    Long productId = createPublishedProduct("refresh-hoodie");
    storefrontProductSearchRepository.deleteById(productId);

    mockMvc.perform(withBearer(post("/admin/search/storefront/products/{productId}/refresh", productId), platformAdminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.productId").value(productId))
        .andExpect(jsonPath("$.data.status").value("refreshed"));

    assertThat(storefrontProductSearchRepository.findById(productId)).isPresent();
}
```

- [ ] **Step 2: Run the test to verify the endpoint does not exist yet**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontSearchAdminControllerTest.platform_admin_refreshes_missing_projection_row' --no-daemon
```

Expected: FAIL with `404`, `NoHandlerFound`, or test compilation failure because the controller/DTOs do not exist yet.

- [ ] **Step 3: Add minimal repository support for ordered product scanning**

Update `StorefrontProductSearchRepository.java` dependencies only if needed and add a product ID paging method to the product-side repository actually used by the rebuild service. If the existing `ProductSpuRepository` already exposes ordered paging, use it; otherwise add:

```java
Page<ProductSpuEntity> findAllByOrderByIdAsc(Pageable pageable);
```

If a lighter ID-only query is preferable, add:

```java
@Query("select p.id from ProductSpuEntity p order by p.id asc")
Page<Long> findIdsForProjectionRebuild(Pageable pageable);
```

Keep `ProductSearchProjector.refresh(Long productId)` as the only single-product writer.

- [ ] **Step 4: Run compilation-focused tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontSearchAdminControllerTest.platform_admin_refreshes_missing_projection_row' --no-daemon
```

Expected: FAIL later in request flow, proving the repository/projection support now compiles and the remaining work is in service/controller wiring.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java backend/src/main/java/com/example/ecommerce/search/domain/*.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java
git commit -m "refactor: prepare storefront projection rebuild support"
```

## Task 2: Implement admin rebuild service and response models

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchAdminService.java`
- Create: `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProjectionRefreshResponse.java`
- Create: `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProjectionRebuildResponse.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java`
- Test: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java`

- [ ] **Step 1: Write failing tests for rebuild summary behavior**

Add tests like:

```java
@Test
void platform_admin_rebuilds_all_projection_rows() throws Exception {
    Long firstId = createPublishedProduct("rebuild-alpha");
    Long secondId = createPublishedProduct("rebuild-beta");
    storefrontProductSearchRepository.deleteAll();

    mockMvc.perform(withBearer(post("/admin/search/storefront/rebuild"), platformAdminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.processedCount").value(2))
        .andExpect(jsonPath("$.data.successCount").value(2))
        .andExpect(jsonPath("$.data.failureCount").value(0));

    assertThat(storefrontProductSearchRepository.findById(firstId)).isPresent();
    assertThat(storefrontProductSearchRepository.findById(secondId)).isPresent();
}
```

```java
@Test
void rebuild_reports_failures_without_aborting() throws Exception {
    Long validId = createPublishedProduct("rebuild-good");
    Long brokenId = createPublishedProductWithoutSku("rebuild-broken");
    storefrontProductSearchRepository.deleteAll();

    mockMvc.perform(withBearer(post("/admin/search/storefront/rebuild"), platformAdminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.processedCount").value(2))
        .andExpect(jsonPath("$.data.successCount").value(1))
        .andExpect(jsonPath("$.data.failureCount").value(1))
        .andExpect(jsonPath("$.data.failures[0].productId").value(brokenId));

    assertThat(storefrontProductSearchRepository.findById(validId)).isPresent();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontSearchAdminControllerTest.platform_admin_rebuilds_all_projection_rows' --tests 'com.example.ecommerce.search.api.StorefrontSearchAdminControllerTest.rebuild_reports_failures_without_aborting' --no-daemon
```

Expected: FAIL because rebuild service, DTOs, and endpoint wiring do not exist yet.

- [ ] **Step 3: Implement the admin orchestration service and DTOs**

Create `StorefrontSearchAdminService.java` with logic equivalent to:

```java
@Service
public class StorefrontSearchAdminService {

    private static final int REBUILD_PAGE_SIZE = 100;

    private final ProductSpuRepository productSpuRepository;
    private final ProductSearchProjector productSearchProjector;

    public StorefrontSearchAdminService(
        ProductSpuRepository productSpuRepository,
        ProductSearchProjector productSearchProjector
    ) {
        this.productSpuRepository = productSpuRepository;
        this.productSearchProjector = productSearchProjector;
    }

    @Transactional
    public StorefrontProjectionRefreshResponse refreshProduct(Long productId) {
        productSearchProjector.refresh(productId);
        return new StorefrontProjectionRefreshResponse(productId, "refreshed");
    }

    @Transactional
    public StorefrontProjectionRebuildResponse rebuildAll() {
        long startedAt = System.currentTimeMillis();
        int processedCount = 0;
        int successCount = 0;
        List<StorefrontProjectionRebuildResponse.Failure> failures = new ArrayList<>();
        PageRequest pageRequest = PageRequest.of(0, REBUILD_PAGE_SIZE);
        Page<Long> page;

        do {
            page = productSpuRepository.findIdsForProjectionRebuild(pageRequest);
            for (Long productId : page.getContent()) {
                processedCount++;
                try {
                    productSearchProjector.refresh(productId);
                    successCount++;
                } catch (BusinessException ex) {
                    failures.add(new StorefrontProjectionRebuildResponse.Failure(productId, ex.getCode().name(), ex.getMessage()));
                } catch (Exception ex) {
                    failures.add(new StorefrontProjectionRebuildResponse.Failure(productId, ErrorCode.COMMON_INTERNAL_ERROR.name(), ex.getMessage()));
                }
            }
            pageRequest = page.nextPageable();
        } while (page.hasNext());

        return new StorefrontProjectionRebuildResponse(
            processedCount,
            successCount,
            failures.size(),
            System.currentTimeMillis() - startedAt,
            failures
        );
    }
}
```

Keep response DTOs as records.

- [ ] **Step 4: Run rebuild-focused tests**

Run the same test command from Step 2.

Expected: FAIL only because controller and authorization are not wired yet.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchAdminService.java backend/src/main/java/com/example/ecommerce/search/api/StorefrontProjectionRefreshResponse.java backend/src/main/java/com/example/ecommerce/search/api/StorefrontProjectionRebuildResponse.java backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java
git commit -m "feat: add storefront projection rebuild service"
```

## Task 3: Expose admin rebuild endpoints and enforce platform-admin-only access

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/search/api/StorefrontSearchAdminController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthRole.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/config/SecurityConfiguration.java`
- Test: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java`

- [ ] **Step 1: Write failing authorization tests**

Add tests like:

```java
@Test
void anonymous_cannot_refresh_projection() throws Exception {
    mockMvc.perform(post("/admin/search/storefront/products/{productId}/refresh", 999L))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
}
```

```java
@Test
void merchant_admin_cannot_rebuild_projection() throws Exception {
    mockMvc.perform(withBearer(post("/admin/search/storefront/rebuild"), merchantAdminToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
}
```

- [ ] **Step 2: Run the authorization tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontSearchAdminControllerTest.anonymous_cannot_refresh_projection' --tests 'com.example.ecommerce.search.api.StorefrontSearchAdminControllerTest.merchant_admin_cannot_rebuild_projection' --no-daemon
```

Expected: FAIL because the admin controller path is not yet registered and role checks are missing.

- [ ] **Step 3: Implement controller and platform-admin service checks**

Create `StorefrontSearchAdminController.java` with endpoints:

```java
@RestController
@RequestMapping("/admin/search/storefront")
public class StorefrontSearchAdminController {

    private final StorefrontSearchAdminService storefrontSearchAdminService;

    public StorefrontSearchAdminController(StorefrontSearchAdminService storefrontSearchAdminService) {
        this.storefrontSearchAdminService = storefrontSearchAdminService;
    }

    @PostMapping("/products/{productId}/refresh")
    public ApiResponse<StorefrontProjectionRefreshResponse> refresh(@PathVariable Long productId) {
        return ApiResponse.success(storefrontSearchAdminService.refreshProduct(productId));
    }

    @PostMapping("/rebuild")
    public ApiResponse<StorefrontProjectionRebuildResponse> rebuild() {
        return ApiResponse.success(storefrontSearchAdminService.rebuildAll());
    }
}
```

In `StorefrontSearchAdminService`, enforce:

```java
AuthContext authContext = AuthContextHolder.getRequired();
if (authContext.role() != AuthRole.PLATFORM_ADMIN) {
    throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "platform admin role required");
}
```

Do not open any extra anonymous paths in `SecurityConfiguration`; the `/admin/**` rule should already require authentication.

- [ ] **Step 4: Run the controller test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontSearchAdminControllerTest' --no-daemon
```

Expected: PASS for anonymous rejection, merchant rejection, single refresh, and rebuild summary coverage.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/api/StorefrontSearchAdminController.java backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchAdminService.java backend/src/main/java/com/example/ecommerce/shared/config/SecurityConfiguration.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java
git commit -m "feat: add storefront projection admin endpoints"
```

## Task 4: Add visibility regression coverage for rebuild behavior

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java`

- [ ] **Step 1: Write failing regression tests for non-visible products**

Add a storefront regression test:

```java
@Test
void rebuild_does_not_make_unpublished_products_visible() throws Exception {
    Long productId = createApprovedButUnpublishedProduct("hidden-hoodie");
    storefrontProductSearchRepository.deleteAll();

    mockMvc.perform(withBearer(post("/admin/search/storefront/rebuild"), platformAdminToken()))
        .andExpect(status().isOk());

    mockMvc.perform(get("/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[*].id").value(not(hasItem(productId.intValue()))));
}
```

- [ ] **Step 2: Run the regression tests to verify they fail if visibility rules drift**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --tests 'com.example.ecommerce.search.api.StorefrontSearchAdminControllerTest.rebuild_does_not_make_unpublished_products_visible' --no-daemon
```

Expected: PASS if the existing projection rules already hold; if it fails, fix the projection refresh path before moving on.

- [ ] **Step 3: Tighten any helper setup needed for workflow visibility cases**

Use existing workflow helpers from product workflow tests rather than introducing duplicate setup logic. Keep the rebuild tests focused on visibility, not on re-testing the full publish/review workflow.

- [ ] **Step 4: Re-run the storefront regression batch**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontSearchAdminControllerTest.java
git commit -m "test: cover storefront rebuild visibility rules"
```

## Task 5: Document and verify the maintenance workflow

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Write the failing documentation expectation manually**

Document these missing items in `backend/README.md`:

- rebuild endpoints and their purpose
- `PLATFORM_ADMIN` restriction
- when to choose single refresh versus full rebuild
- sample response fields for rebuild summaries

- [ ] **Step 2: Update README with operator instructions**

Add a section similar to:

```md
## Storefront Projection Maintenance

Platform admins can repair storefront search data with:

- `POST /admin/search/storefront/products/{productId}/refresh`
- `POST /admin/search/storefront/rebuild`

Use single refresh when one product is missing or stale. Use full rebuild after
projection table cleanup, schema backfill, or suspected drift.

Full rebuild responses include:

- `processedCount`
- `successCount`
- `failureCount`
- `durationMs`
- `failures`
```

- [ ] **Step 3: Run full verification**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Inspect final diff and working tree**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only the planned rebuild-related files are modified.

- [ ] **Step 5: Commit**

```bash
git add backend/README.md
git commit -m "docs: add storefront projection maintenance guide"
```

## Spec Coverage Check

- Single-product refresh endpoint: covered by Tasks 1 and 3.
- Full synchronous rebuild endpoint: covered by Tasks 2 and 3.
- Platform-admin-only restriction: covered by Task 3.
- Rebuild summary counts and failures: covered by Task 2.
- Visibility preservation after rebuild: covered by Task 4.
- Documentation for operator workflow: covered by Task 5.

No spec sections are left without an implementation task.
