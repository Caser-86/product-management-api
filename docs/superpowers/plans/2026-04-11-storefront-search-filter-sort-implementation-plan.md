# Storefront Search Filter and Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend storefront product search with price-range filtering and explicit sort options while preserving the existing response shape and visibility rules.

**Architecture:** Keep the existing `GET /products` endpoint and search projection table, but replace the current combinatorial derived-query approach with a repository path that can compose optional filters and dynamic sorting cleanly. Validation will remain at the controller/service boundary, while query execution stays in the search module.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, JPA Criteria/Specification-style querying, MySQL/H2, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProductController.java`
  Add new storefront query parameters.
- `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java`
  Validate search inputs, choose sort order, and delegate through a maintainable query path.
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
  Add custom repository support beyond the existing derived-query methods.
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchEntity.java`
  Reuse existing fields for sorting and filtering; no schema change expected, but keep accessors aligned if needed.
- `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`
  Add end-to-end storefront tests for price filtering, sorting, and invalid input cases.
- `backend/README.md`
  Document new query parameters and supported sort values.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepository.java`
  Interface for composed storefront queries.
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepositoryImpl.java`
  Criteria-based query implementation with optional filters and dynamic sorting.
- `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchSort.java`
  Enum of supported storefront sort values and their Spring `Sort` mapping.

## Task 1: Add failing storefront tests for price filters and sorting

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`

- [ ] **Step 1: Add a failing price-range filter test**

Add:

```java
@Test
void filters_products_by_price_range() throws Exception {
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        2001L,
        2001L,
        33L,
        "range-hoodie",
        30001L,
        new BigDecimal("99.00"),
        new BigDecimal("149.00"),
        6,
        "in_stock",
        "active",
        "published",
        "approved"
    ));
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        2002L,
        2001L,
        33L,
        "range-coat",
        30002L,
        new BigDecimal("299.00"),
        new BigDecimal("349.00"),
        2,
        "in_stock",
        "active",
        "published",
        "approved"
    ));

    mockMvc.perform(get("/products")
            .param("minPrice", "120")
            .param("maxPrice", "200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].title").value("range-hoodie"));
}
```

- [ ] **Step 2: Add failing sort-order tests**

Add:

```java
@Test
void sorts_products_by_price_ascending() throws Exception {
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        2003L, 2001L, 33L, "sort-budget", 30003L,
        new BigDecimal("59.00"), new BigDecimal("99.00"), 5,
        "in_stock", "active", "published", "approved"
    ));
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
        2004L, 2001L, 33L, "sort-premium", 30004L,
        new BigDecimal("259.00"), new BigDecimal("399.00"), 5,
        "in_stock", "active", "published", "approved"
    ));

    mockMvc.perform(get("/products").param("sort", "price_asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].title").value("sort-budget"));
}
```

```java
@Test
void rejects_unknown_sort_value() throws Exception {
    mockMvc.perform(get("/products").param("sort", "random"))
        .andExpect(status().isBadRequest());
}
```

- [ ] **Step 3: Run the storefront controller test class**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --no-daemon
```

Expected: FAIL because the endpoint does not yet understand the new parameters.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java
git commit -m "test: cover storefront price filters and sort options"
```

## Task 2: Add sortable and filterable storefront query support

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepository.java`
- Create: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepositoryImpl.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
- Create: `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchSort.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java`

- [ ] **Step 1: Add a failing compilation target for the new query path**

In `StorefrontSearchService`, temporarily sketch the intended delegation:

```java
storefrontProductSearchRepository.searchVisibleProducts(
    effectiveKeyword,
    categoryId,
    minPrice,
    maxPrice,
    storefrontSearchSort,
    pageable
);
```

Do not add the implementation yet.

- [ ] **Step 2: Run the storefront test again**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --no-daemon
```

Expected: FAIL at compile time because the custom repository path does not exist yet.

- [ ] **Step 3: Implement the custom repository and sort enum**

Create `StorefrontSearchSort.java`:

```java
public enum StorefrontSearchSort {
    NEWEST(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("productId"))),
    PRICE_ASC(Sort.by(Sort.Order.asc("minPrice"), Sort.Order.desc("productId"))),
    PRICE_DESC(Sort.by(Sort.Order.desc("maxPrice"), Sort.Order.desc("productId")));

    private final Sort sort;

    StorefrontSearchSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    public static StorefrontSearchSort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEWEST;
        }
        return switch (raw.trim().toLowerCase()) {
            case "newest" -> NEWEST;
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            default -> throw new IllegalArgumentException("unsupported storefront sort");
        };
    }
}
```

Create `StorefrontProductSearchCustomRepository.java`:

```java
public interface StorefrontProductSearchCustomRepository {
    Page<StorefrontProductSearchEntity> searchVisibleProducts(
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        StorefrontSearchSort sort,
        Pageable pageable
    );
}
```

Implement `StorefrontProductSearchCustomRepositoryImpl.java` with Criteria API:

- always filter `productStatus != deleted`
- always filter `publishStatus = published`
- always filter `auditStatus = approved`
- optionally add title `like`
- optionally add `categoryId`
- optionally add `maxPrice >= minPrice`
- optionally add `minPrice <= maxPrice`
- apply dynamic sort from the enum

Update `StorefrontProductSearchRepository`:

```java
public interface StorefrontProductSearchRepository
    extends JpaRepository<StorefrontProductSearchEntity, Long>, StorefrontProductSearchCustomRepository {
}
```

- [ ] **Step 4: Update `StorefrontSearchService` to validate and delegate**

Extend the signature to:

```java
public StorefrontSearchResponse search(
    String keyword,
    Long categoryId,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    String sort,
    int page,
    int pageSize
)
```

Validation rules:

- reject negative `minPrice` or `maxPrice`
- reject `minPrice > maxPrice`
- parse sort through `StorefrontSearchSort.parse`

Then delegate to the custom repository method.

- [ ] **Step 5: Re-run the storefront controller test class**

Run the same command from Task 1 Step 3.

Expected: PASS for the new filter and sort coverage.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepository.java backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchCustomRepositoryImpl.java backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchSort.java backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java
git commit -m "feat: add storefront search filters and sorting"
```

## Task 3: Extend the controller contract and validation coverage

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/search/api/StorefrontProductController.java`
- Modify: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`

- [ ] **Step 1: Wire the new request parameters into the controller**

Update the endpoint signature:

```java
public ApiResponse<StorefrontSearchResponse> search(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) Long categoryId,
    @RequestParam(required = false) BigDecimal minPrice,
    @RequestParam(required = false) BigDecimal maxPrice,
    @RequestParam(required = false) String sort,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int pageSize
) {
    return ApiResponse.success(
        storefrontSearchService.search(keyword, categoryId, minPrice, maxPrice, sort, page, pageSize)
    );
}
```

Add OpenAPI parameter descriptions for:

- `minPrice`
- `maxPrice`
- `sort`

- [ ] **Step 2: Add a failing invalid-range test**

Add:

```java
@Test
void rejects_invalid_price_range() throws Exception {
    mockMvc.perform(get("/products")
            .param("minPrice", "300")
            .param("maxPrice", "100"))
        .andExpect(status().isBadRequest());
}
```

- [ ] **Step 3: Run the storefront controller test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/api/StorefrontProductController.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java
git commit -m "feat: extend storefront search query contract"
```

## Task 4: Document and verify the storefront search enhancement

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Document the new storefront query parameters**

Add a short note under the storefront search section covering:

- `minPrice`
- `maxPrice`
- `sort`
- supported sort values:
  - `newest`
  - `price_asc`
  - `price_desc`

- [ ] **Step 2: Run full backend verification**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Inspect the final diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only storefront search filter/sort files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe storefront search filters and sorting"
```

## Spec Coverage Check

- Price-range filtering: covered by Tasks 1 and 2.
- Supported sort values and defaults: covered by Tasks 1, 2, and 3.
- Validation of unsupported or invalid inputs: covered by Tasks 1 and 3.
- Reuse of the existing projection table without schema changes: covered by Task 2.
- Documentation updates and verification: covered by Task 4.

No spec sections are left without an implementation task.
