# Price History Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade `GET /admin/skus/{skuId}/price-history` to return typed price-history DTOs with pagination while preserving current authorization behavior.

**Architecture:** Keep the existing route, add focused response DTOs in the pricing API package, switch the repository query to pageable reads, and have `PricingService` map stored JSON snapshots into typed nested price objects. Clamp paging inputs in the service/controller path so the endpoint stays bounded and predictable.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, Jackson, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java`
  Add `page` and `pageSize` query parameters and return the typed response DTO.
- `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
  Add pageable history reads, parse JSON into typed price snapshots, and clamp page inputs.
- `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceHistoryRepository.java`
  Add pageable repository access for SKU price history.
- `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
  Add end-to-end tests for typed payloads, pagination, and page-size clamping.
- `backend/README.md`
  Document the new history response shape and pagination params.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/pricing/api/PriceHistoryResponse.java`
  Typed response DTO for paginated price-history reads.

## Task 1: Add failing controller tests for typed price-history reads

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`

- [ ] **Step 1: Add a failing test for typed price snapshots**

Add:

```java
@Test
void price_history_returns_typed_price_snapshots() throws Exception {
    updatePrice(skuId, 189.00, 149.00, "launch price", 501L);
    updatePrice(skuId, 199.00, 159.00, "raise price", 502L);

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", skuId), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].changeType").value("manual"))
        .andExpect(jsonPath("$.data.items[0].newPrice.listPrice").value(199.00))
        .andExpect(jsonPath("$.data.items[0].newPrice.salePrice").value(159.00))
        .andExpect(jsonPath("$.data.items[0].oldPrice.listPrice").value(189.00))
        .andExpect(jsonPath("$.data.items[0].oldPrice.salePrice").value(149.00));
}
```

- [ ] **Step 2: Add failing pagination and clamping tests**

Add:

```java
@Test
void price_history_supports_pagination() throws Exception {
    updatePrice(skuId, 100.00, 90.00, "price-1", 501L);
    updatePrice(skuId, 110.00, 95.00, "price-2", 502L);
    updatePrice(skuId, 120.00, 99.00, "price-3", 503L);

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", skuId)
            .param("page", "2")
            .param("pageSize", "1"), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.pageSize").value(1))
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].reason").value("price-2"));
}
```

```java
@Test
void price_history_clamps_page_size_to_maximum() throws Exception {
    updatePrice(skuId, 100.00, 90.00, "price-1", 501L);

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", skuId)
            .param("pageSize", "999"), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pageSize").value(100));
}
```

- [ ] **Step 3: Add a failing cross-merchant permission regression test**

Add:

```java
@Test
void merchant_admin_cannot_read_price_history_for_other_merchant_sku() throws Exception {
    ProductSpuEntity foreignSpu = ProductSpuEntity.draft(4001L, "SPU-PRC-HISTORY-2", "pricing-foreign", 44L);
    foreignSpu.addSku(ProductSkuEntity.of(4001L, "SKU-PRC-HISTORY-2", "{\"color\":\"white\"}", "pricing-hash-history-2"));
    Long foreignSkuId = productSpuRepository.save(foreignSpu).getSkus().get(0).getId();

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", foreignSkuId), merchantAdminToken(9002L, 2001L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
}
```

- [ ] **Step 4: Add a small test helper for price writes**

Add a helper method in the test class:

```java
private void updatePrice(Long skuId, double listPrice, double salePrice, String reason, long operatorId) throws Exception {
    mockMvc.perform(withBearer(patch("/admin/skus/{skuId}/prices", skuId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "listPrice": %.2f,
                  "salePrice": %.2f,
                  "reason": "%s",
                  "operatorId": %d
                }
                """.formatted(listPrice, salePrice, reason, operatorId)))
        .andExpect(status().isOk());
}
```

- [ ] **Step 5: Run the pricing controller test class**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.api.PricingControllerTest' --no-daemon
```

Expected: FAIL because the endpoint still returns the old map-based shape.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java
git commit -m "test: cover paginated price history reads"
```

## Task 2: Add typed response DTOs and pageable repository support

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/pricing/api/PriceHistoryResponse.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceHistoryRepository.java`

- [ ] **Step 1: Create the typed response DTO**

Create:

```java
public record PriceHistoryResponse(
    List<Item> items,
    int page,
    int pageSize,
    long total
) {
    public record Item(
        String changeType,
        PriceSnapshot oldPrice,
        PriceSnapshot newPrice,
        String reason,
        Long operatorId,
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

- [ ] **Step 2: Add pageable repository support**

Replace the list-only method with a pageable method:

```java
Page<PriceHistoryEntity> findBySkuId(Long skuId, Pageable pageable);
```

Use a `PageRequest` sorted by:

```java
Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
```

- [ ] **Step 3: Re-run the pricing controller tests**

Run the same command from Task 1 Step 5.

Expected: tests still fail because service/controller mapping has not been upgraded yet, but compilation succeeds.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/pricing/api/PriceHistoryResponse.java backend/src/main/java/com/example/ecommerce/pricing/domain/PriceHistoryRepository.java
git commit -m "feat: add typed price history dto"
```

## Task 3: Upgrade service and controller to paginated typed responses

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java`

- [ ] **Step 1: Change the service history signature**

Update:

```java
public PriceHistoryResponse history(Long skuId, int page, int pageSize)
```

Clamp inputs:

```java
int safePage = Math.max(page, 1);
int safePageSize = Math.min(Math.max(pageSize, 1), 100);
```

- [ ] **Step 2: Add pageable query and typed mapping**

Use a pageable query and map each row into `PriceHistoryResponse.Item`:

```java
var pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
var pageResult = priceHistoryRepository.findBySkuId(skuId, pageable);
var items = pageResult.getContent().stream()
    .map(history -> new PriceHistoryResponse.Item(
        history.getChangeType(),
        parsePriceSnapshot(history.getOldPriceJson()),
        parsePriceSnapshot(history.getNewPriceJson()),
        history.getReason(),
        history.getOperatorId(),
        history.getCreatedAt()
    ))
    .toList();
return new PriceHistoryResponse(items, safePage, safePageSize, pageResult.getTotalElements());
```

For empty JSON (`{}`), return `null` for `oldPrice`.

- [ ] **Step 3: Add a helper to parse price snapshots**

Add a helper similar to:

```java
private PriceHistoryResponse.PriceSnapshot parsePriceSnapshot(String rawJson) {
    JsonNode node = parseJson(rawJson);
    if (!node.hasNonNull("listPrice") && !node.hasNonNull("salePrice")) {
        return null;
    }
    return new PriceHistoryResponse.PriceSnapshot(
        node.path("listPrice").decimalValue(),
        node.path("salePrice").decimalValue()
    );
}
```

- [ ] **Step 4: Update the controller signature**

Change the endpoint to:

```java
public ApiResponse<PriceHistoryResponse> history(
    @PathVariable Long skuId,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int pageSize
) {
    return ApiResponse.success(pricingService.history(skuId, page, pageSize));
}
```

Add Swagger parameter descriptions for both paging params.

- [ ] **Step 5: Re-run the pricing controller test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.api.PricingControllerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java
git commit -m "feat: paginate price history"
```

## Task 4: Document and verify the price-history upgrade

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README**

Document:

- `GET /admin/skus/{skuId}/price-history?page=1&pageSize=20`
- typed `oldPrice/newPrice` objects
- `pageSize` maximum of `100`

- [ ] **Step 2: Run full backend verification**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Inspect final diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only price-history-pagination related files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe paginated price history"
```

## Spec Coverage Check

- Typed response DTOs: covered by Tasks 1 to 3.
- Pagination and page-size clamping: covered by Tasks 1 and 3.
- Authorization preservation: covered by Task 1 and reused service scope checks in Task 3.
- Documentation and verification: covered by Task 4.

No spec sections are left without an implementation task.
