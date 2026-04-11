# Storefront Search Stock and Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-stock-only storefront filter and bounded pagination behavior while preserving the current search response shape.

**Architecture:** Extend the existing storefront custom repository query path with one additional optional predicate and tighten pagination normalization in the service layer. Keep the same projection table and controller endpoint, adding only one shopper-facing query parameter and stronger regression tests for stability.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA Criteria API, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProductController.java`
  Add the new optional `inStockOnly` parameter and document it in OpenAPI.
- `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java`
  Clamp pagination bounds and pass the in-stock filter through the query path.
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepository.java`
  Extend the custom query contract with the optional stock filter.
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepositoryImpl.java`
  Apply the in-stock predicate and preserve deterministic sort behavior.
- `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`
  Add end-to-end coverage for in-stock-only filtering, page-size clamping, and stable sorting under ties.
- `backend/README.md`
  Document the new parameter and storefront page-size cap.

### New files to create

None.

## Task 1: Add failing storefront tests for stock filtering and pagination bounds

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`

- [ ] **Step 1: Add a failing in-stock-only filter test**

Add:

```java
@Test
void filters_to_only_in_stock_products() throws Exception {
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        3001L, 2001L, 33L, "stock-hoodie", 40001L,
        new BigDecimal("99.00"), new BigDecimal("149.00"), 5,
        "in_stock", "active", "published", "approved"
    ));
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        3002L, 2001L, 33L, "stock-jacket", 40002L,
        new BigDecimal("129.00"), new BigDecimal("199.00"), 0,
        "out_of_stock", "active", "published", "approved"
    ));

    mockMvc.perform(get("/products").param("inStockOnly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].title").value("stock-hoodie"));
}
```

- [ ] **Step 2: Add a failing page-size clamp test**

Add:

```java
@Test
void clamps_page_size_to_maximum() throws Exception {
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        3003L, 2001L, 33L, "page-a", 40003L,
        new BigDecimal("49.00"), new BigDecimal("79.00"), 3,
        "in_stock", "active", "published", "approved"
    ));

    mockMvc.perform(get("/products").param("pageSize", "999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pageSize").value(100));
}
```

- [ ] **Step 3: Add a failing stable-sort tie-break test**

Add:

```java
@Test
void keeps_price_sort_stable_when_prices_tie() throws Exception {
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        3004L, 2001L, 33L, "tie-first", 40004L,
        new BigDecimal("88.00"), new BigDecimal("120.00"), 4,
        "in_stock", "active", "published", "approved"
    ));
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        3005L, 2001L, 33L, "tie-second", 40005L,
        new BigDecimal("88.00"), new BigDecimal("140.00"), 4,
        "in_stock", "active", "published", "approved"
    ));

    mockMvc.perform(get("/products").param("sort", "price_asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].productId").value(3005))
        .andExpect(jsonPath("$.data.items[1].productId").value(3004));
}
```

- [ ] **Step 4: Run the storefront controller test class**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --no-daemon
```

Expected: FAIL because the API does not yet support the new filter and page-size cap.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java
git commit -m "test: cover storefront stock filter and pagination bounds"
```

## Task 2: Extend the storefront query path with the stock filter

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepository.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepositoryImpl.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java`

- [ ] **Step 1: Extend the custom repository contract**

Update the method signature to include:

```java
Boolean inStockOnly
```

For example:

```java
Page<StorefrontProductSearchEntity> searchVisibleProducts(
    String keyword,
    Long categoryId,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Boolean inStockOnly,
    StorefrontSearchSort sort,
    Pageable pageable
);
```

- [ ] **Step 2: Apply the in-stock predicate in the repository implementation**

When `Boolean.TRUE.equals(inStockOnly)`, add:

```java
predicates.add(criteriaBuilder.greaterThan(root.get("availableQty"), 0));
```

Do not change sorting or existing visibility predicates.

- [ ] **Step 3: Pass the new parameter through the service**

Update the service signature to include:

```java
Boolean inStockOnly
```

And delegate it through the repository call.

- [ ] **Step 4: Re-run the storefront controller test class**

Run the same command from Task 1 Step 4.

Expected: the stock-filter test should pass, while the page-size clamp test may still fail until Task 3.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepository.java backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepositoryImpl.java backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java
git commit -m "feat: add storefront in-stock filtering"
```

## Task 3: Clamp storefront pagination and extend the controller contract

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProductController.java`
- Modify: `backend/src/main/java/com/example\ecommerce\search\application\StorefrontSearchService.java`
- Modify: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`

- [ ] **Step 1: Add `inStockOnly` to the controller**

Extend the endpoint signature with:

```java
@Parameter(description = "When true, only return products with available stock", example = "true")
@RequestParam(required = false) Boolean inStockOnly,
```

And pass it through to the service call.

- [ ] **Step 2: Clamp page size in the service**

In `StorefrontSearchService`, replace the current page-size normalization with:

```java
private static final int MAX_PAGE_SIZE = 100;

int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
```

Keep `page` clamped to at least `1`.

- [ ] **Step 3: Re-run the storefront controller test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/api/StorefrontProductController.java backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java
git commit -m "feat: clamp storefront pagination inputs"
```

## Task 4: Document and verify the storefront browse refinement

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update the storefront search documentation**

Document:

- `inStockOnly`
- storefront `pageSize` maximum `100`

Suggested note:

```md
Storefront search also supports `inStockOnly=true` to hide out-of-stock items.
`pageSize` is clamped to a maximum of `100`.
```

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

Expected: only storefront search stock/pagination files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe storefront stock filtering"
```

## Spec Coverage Check

- In-stock-only filtering: covered by Tasks 1 and 2.
- Page-size upper bound: covered by Tasks 1 and 3.
- Stable sorting under ties: covered by Task 1.
- Controller and README updates: covered by Tasks 3 and 4.
- Full verification: covered by Task 4.

No spec sections are left without an implementation task.
