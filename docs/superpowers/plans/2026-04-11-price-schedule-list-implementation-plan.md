# Price Schedule List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `GET /admin/skus/{skuId}/price-schedules` with typed paginated responses while preserving current pricing authorization behavior.

**Architecture:** Keep the route SKU-scoped, add focused list DTOs in the pricing API package, extend the schedule repository with pageable SKU reads ordered by `effectiveAt desc, id desc`, and let `PricingService` parse stored target-price JSON into typed nested objects. Reuse the existing merchant-scope assertions so the new read path behaves like the current price-history and price-update endpoints.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, Jackson, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java`
  Add the new GET endpoint and pagination params.
- `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
  Add SKU schedule-list reads, input clamping, typed mapping, and JSON parsing reuse.
- `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleRepository.java`
  Add pageable SKU-scoped repository access sorted by newest-effective first.
- `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleEntity.java`
  Expose any missing getters needed by the typed list response.
- `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
  Add end-to-end tests for schedule listing, pagination, and merchant-scope rules.
- `backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java`
  Assert the new GET endpoint publishes the typed OpenAPI schema.
- `backend/README.md`
  Document the new admin schedule-list endpoint and params.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/pricing/api/PriceScheduleListResponse.java`
  Typed paginated response DTO for SKU schedule reads.

## Task 1: Add failing controller and OpenAPI tests for schedule-list reads

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java`

- [ ] **Step 1: Add a failing end-to-end test for listing schedules for one SKU**

Add:

```java
@Test
void lists_price_schedules_for_a_sku() throws Exception {
    createSchedule(skuId, 299.00, 239.00, LocalDateTime.now().plusDays(1), "promo-1", 7001L);
    createSchedule(skuId, 319.00, 259.00, LocalDateTime.now().plusDays(2), "promo-2", 7002L);

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-schedules", skuId), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.pageSize").value(20))
        .andExpect(jsonPath("$.data.total").value(2))
        .andExpect(jsonPath("$.data.items[0].status").value("pending"))
        .andExpect(jsonPath("$.data.items[0].targetPrice.listPrice").value(319.00))
        .andExpect(jsonPath("$.data.items[0].targetPrice.salePrice").value(259.00))
        .andExpect(jsonPath("$.data.items[0].effectiveAt").exists())
        .andExpect(jsonPath("$.data.items[0].createdAt").exists());
}
```

- [ ] **Step 2: Add failing pagination and clamping tests**

Add:

```java
@Test
void price_schedule_list_supports_pagination() throws Exception {
    createSchedule(skuId, 100.00, 90.00, LocalDateTime.now().plusHours(1), "promo-1", 7001L);
    createSchedule(skuId, 110.00, 95.00, LocalDateTime.now().plusHours(2), "promo-2", 7002L);
    createSchedule(skuId, 120.00, 99.00, LocalDateTime.now().plusHours(3), "promo-3", 7003L);

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-schedules", skuId)
            .param("page", "2")
            .param("pageSize", "1"), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.pageSize").value(1))
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].targetPrice.listPrice").value(110.00));
}
```

```java
@Test
void price_schedule_list_clamps_page_size_to_maximum() throws Exception {
    createSchedule(skuId, 100.00, 90.00, LocalDateTime.now().plusHours(1), "promo-1", 7001L);

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-schedules", skuId)
            .param("pageSize", "999"), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pageSize").value(100));
}
```

- [ ] **Step 3: Add a failing merchant-scope regression test**

Add:

```java
@Test
void merchant_admin_cannot_list_price_schedules_for_other_merchant_sku() throws Exception {
    ProductSpuEntity foreignSpu = ProductSpuEntity.draft(4001L, "SPU-PRC-SCHED-2", "pricing-foreign", 44L);
    foreignSpu.addSku(ProductSkuEntity.of(4001L, "SKU-PRC-SCHED-2", "{\"color\":\"white\"}", "pricing-hash-sched-2"));
    Long foreignSkuId = productSpuRepository.save(foreignSpu).getSkus().get(0).getId();

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-schedules", foreignSkuId), merchantAdminToken(9002L, 2001L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
}
```

- [ ] **Step 4: Add a schedule-creation helper to the test class**

Add:

```java
private void createSchedule(Long skuId, double listPrice, double salePrice, LocalDateTime effectiveAt, String reason, long operatorId) throws Exception {
    mockMvc.perform(withBearer(post("/admin/skus/{skuId}/price-schedules", skuId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "listPrice": %.2f,
                  "salePrice": %.2f,
                  "effectiveAt": "%s",
                  "reason": "%s",
                  "operatorId": %d
                }
                """.formatted(listPrice, salePrice, effectiveAt, reason, operatorId)))
        .andExpect(status().isOk());
}
```

- [ ] **Step 5: Add a failing OpenAPI assertion for the GET route**

Add:

```java
.andExpect(jsonPath("$.paths['/admin/skus/{skuId}/price-schedules'].get.responses['200'].content['*/*'].schema.$ref")
    .value("#/components/schemas/ApiResponsePriceScheduleListResponse"))
```

- [ ] **Step 6: Run the focused pricing and OpenAPI tests**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.api.PricingControllerTest' --tests 'com.example.ecommerce.shared.api.OpenApiDocumentationTest' --no-daemon
```

Expected: FAIL because the GET route and typed response do not exist yet.

- [ ] **Step 7: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java
git commit -m "test: cover sku price schedule list reads"
```

## Task 2: Add typed list DTOs and pageable repository support

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/pricing/api/PriceScheduleListResponse.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleRepository.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleEntity.java`

- [ ] **Step 1: Create the typed response DTO**

Create:

```java
public record PriceScheduleListResponse(
    List<Item> items,
    int page,
    int pageSize,
    long total
) {
    public record Item(
        Long scheduleId,
        String status,
        LocalDateTime effectiveAt,
        LocalDateTime expireAt,
        PriceSnapshot targetPrice,
        LocalDateTime createdAt
    ) {
    }

    public record PriceSnapshot(
        BigDecimal listPrice,
        BigDecimal salePrice
    ) {
    }
}
```

- [ ] **Step 2: Add a pageable SKU-scoped repository method**

Add:

```java
Page<PriceScheduleEntity> findBySkuId(Long skuId, Pageable pageable);
```

Use a `PageRequest` sorted by:

```java
Sort.by(Sort.Order.desc("effectiveAt"), Sort.Order.desc("id"))
```

- [ ] **Step 3: Expose missing entity fields through getters**

Add getters if missing:

```java
public LocalDateTime getExpireAt() {
    return expireAt;
}

public LocalDateTime getCreatedAt() {
    return createdAt;
}
```

- [ ] **Step 4: Re-run the focused test command**

Run the same command from Task 1 Step 6.

Expected: tests still fail because controller and service mapping are not implemented yet, but compilation succeeds.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/pricing/api/PriceScheduleListResponse.java backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleRepository.java backend/src/main/java/com/example/ecommerce/pricing/domain/PriceScheduleEntity.java
git commit -m "feat: add typed sku schedule list dto"
```

## Task 3: Implement service and controller schedule-list reads

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java`

- [ ] **Step 1: Add the service read method signature**

Add:

```java
public PriceScheduleListResponse scheduleList(Long skuId, int page, int pageSize)
```

Clamp inputs:

```java
int safePage = Math.max(page, 1);
int safePageSize = Math.min(Math.max(pageSize, 1), 100);
```

- [ ] **Step 2: Query schedules and map them into typed items**

Use:

```java
var pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Order.desc("effectiveAt"), Sort.Order.desc("id")));
var pageResult = priceScheduleRepository.findBySkuId(skuId, pageable);
var items = pageResult.getContent().stream()
    .map(schedule -> new PriceScheduleListResponse.Item(
        schedule.getId(),
        schedule.getStatus(),
        schedule.getEffectiveAt(),
        schedule.getExpireAt(),
        parseSchedulePriceSnapshot(schedule.getTargetPriceJson()),
        schedule.getCreatedAt()
    ))
    .toList();
return new PriceScheduleListResponse(items, safePage, safePageSize, pageResult.getTotalElements());
```

Keep the existing SKU lookup and merchant-scope assertion before the pageable query.

- [ ] **Step 3: Add a helper to parse stored target-price JSON**

Add:

```java
private PriceScheduleListResponse.PriceSnapshot parseSchedulePriceSnapshot(String rawJson) {
    JsonNode node = parseJson(rawJson);
    return new PriceScheduleListResponse.PriceSnapshot(
        decimalNode(node, "listPrice"),
        decimalNode(node, "salePrice")
    );
}
```

Reuse the existing `parseJson(...)` helper and add a small `decimalNode(...)` helper only if needed.

- [ ] **Step 4: Add the new controller route**

Add:

```java
@GetMapping("/admin/skus/{skuId}/price-schedules")
@Operation(summary = "List price schedules", description = "Returns scheduled future price changes for a SKU.")
public ApiResponse<PriceScheduleListResponse> scheduleList(
    @Parameter(description = "SKU ID", example = "20001")
    @PathVariable Long skuId,
    @Parameter(description = "Page number starting from 1", example = "1")
    @RequestParam(defaultValue = "1") int page,
    @Parameter(description = "Page size, maximum 100", example = "20")
    @RequestParam(defaultValue = "20") int pageSize
) {
    return ApiResponse.success(pricingService.scheduleList(skuId, page, pageSize));
}
```

- [ ] **Step 5: Run the focused pricing and OpenAPI tests**

Run the same command from Task 1 Step 6.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java
git commit -m "feat: add sku price schedule list api"
```

## Task 4: Document and fully verify the schedule-list feature

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Document the new endpoint in the pricing section**

Add bullets describing:

```text
GET /admin/skus/{skuId}/price-schedules
- supports page and pageSize
- returns typed targetPrice objects
- lists pending and applied schedules for the SKU
```

- [ ] **Step 2: Add the new spec and plan references to the docs list**

Add:

```text
- docs/superpowers/specs/2026-04-11-price-schedule-list-design.md
- docs/superpowers/plans/2026-04-11-price-schedule-list-implementation-plan.md
```

- [ ] **Step 3: Run the full backend verification**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: add sku price schedule list docs"
```
