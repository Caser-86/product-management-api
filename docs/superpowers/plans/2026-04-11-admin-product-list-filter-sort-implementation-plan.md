# Admin Product List Filter and Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the admin product list API with workflow filters, title keyword search, and explicit sorting while preserving merchant-scope enforcement.

**Architecture:** Keep `GET /admin/products` and the existing `ProductListResponse`, but replace the current simple repository calls with a custom query path that can compose optional workflow predicates and dynamic sorting. The service will continue to own merchant-scope resolution, while the repository handles query composition.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA Criteria API, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java`
  Add new optional query parameters and document them in OpenAPI.
- `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
  Parse new filters and sort values, preserve merchant-scope enforcement, and delegate through a custom query path.
- `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java`
  Extend the repository with a custom admin-list query interface.
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`
  Add end-to-end coverage for workflow filters, keyword search, sorting, and invalid input handling.
- `backend/README.md`
  Document the new admin query parameters and supported sort values.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/product/application/AdminProductListSort.java`
  Enum of supported admin list sort values.
- `backend/src/main/java/com/example/ecommerce/product/domain/AdminProductListQueryRepository.java`
  Custom repository contract for the admin product list.
- `backend/src/main/java/com/example/ecommerce/product/domain/AdminProductListQueryRepositoryImpl.java`
  Criteria-based repository implementation for composed admin filters and sorting.

## Task 1: Add failing admin list tests for filters and sorting

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`

- [ ] **Step 1: Add a failing workflow-filter test**

Add:

```java
@Test
void platform_admin_filters_products_by_workflow_state() throws Exception {
    ProductSpuEntity approved = ProductSpuEntity.draft(2001L, "SPU-LIST-APPROVED", "approved-product", 33L);
    approved.submitForReview(java.time.LocalDateTime.now().minusHours(2));
    approved.approve(9001L, "ok", java.time.LocalDateTime.now().minusHours(1));
    productSpuRepository.save(approved);

    mockMvc.perform(withBearer(get("/admin/products"), platformAdminToken(9001L, 2001L))
            .param("auditStatus", "approved"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].auditStatus").value("approved"));
}
```

- [ ] **Step 2: Add a failing keyword and sort test**

Add:

```java
@Test
void sorts_products_by_title_ascending() throws Exception {
    productSpuRepository.deleteAll();
    workflowHistoryRepository.deleteAll();
    productSpuRepository.save(ProductSpuEntity.draft(2001L, "SPU-LIST-B", "zebra-product", 33L));
    productSpuRepository.save(ProductSpuEntity.draft(2001L, "SPU-LIST-A", "alpha-product", 33L));

    mockMvc.perform(withBearer(get("/admin/products"), merchantAdminToken(9002L, 2001L))
            .param("sort", "title_asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].title").value("alpha-product"));
}
```

```java
@Test
void filters_products_by_keyword() throws Exception {
    mockMvc.perform(withBearer(get("/admin/products"), merchantAdminToken(9002L, 2001L))
            .param("keyword", "2001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
}
```

- [ ] **Step 3: Add a failing invalid-sort test**

Add:

```java
@Test
void rejects_unknown_admin_product_sort() throws Exception {
    mockMvc.perform(withBearer(get("/admin/products"), platformAdminToken(9001L, 2001L))
            .param("sort", "random"))
        .andExpect(status().isBadRequest());
}
```

- [ ] **Step 4: Run the admin list test class**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductListTest' --no-daemon
```

Expected: FAIL because the API does not yet support these filters and sort values.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java
git commit -m "test: cover admin product list filters and sorting"
```

## Task 2: Add custom repository support for admin product list queries

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/product/domain/AdminProductListQueryRepository.java`
- Create: `backend/src/main/java/com/example/ecommerce/product/domain/AdminProductListQueryRepositoryImpl.java`
- Create: `backend/src/main/java/com/example/ecommerce/product/application/AdminProductListSort.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java`

- [ ] **Step 1: Define the custom repository contract**

Create:

```java
public interface AdminProductListQueryRepository {
    Page<ProductSpuEntity> searchAdminProducts(
        Long merchantId,
        String status,
        String auditStatus,
        String publishStatus,
        String keyword,
        AdminProductListSort sort,
        Pageable pageable
    );
}
```

- [ ] **Step 2: Define the supported sort enum**

Create `AdminProductListSort.java`:

```java
public enum AdminProductListSort {
    CREATED_DESC(Sort.by(Sort.Order.desc("id"))),
    TITLE_ASC(Sort.by(Sort.Order.asc("title"), Sort.Order.desc("id"))),
    TITLE_DESC(Sort.by(Sort.Order.desc("title"), Sort.Order.desc("id")));

    private final Sort sort;

    AdminProductListSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    public static AdminProductListSort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CREATED_DESC;
        }
        return switch (raw.trim().toLowerCase()) {
            case "created_desc" -> CREATED_DESC;
            case "title_asc" -> TITLE_ASC;
            case "title_desc" -> TITLE_DESC;
            default -> throw new IllegalArgumentException("unsupported admin product sort");
        };
    }
}
```

- [ ] **Step 3: Implement the criteria repository**

The implementation should:

- always exclude `status = deleted`
- optionally filter by `merchantId`
- optionally filter by `status`
- optionally filter by `auditStatus`
- optionally filter by `publishStatus`
- optionally filter by title keyword with case-insensitive partial match
- apply sorting from `AdminProductListSort`

- [ ] **Step 4: Extend `ProductSpuRepository`**

Update:

```java
public interface ProductSpuRepository extends JpaRepository<ProductSpuEntity, Long>, AdminProductListQueryRepository {
}
```

- [ ] **Step 5: Re-run the admin list test class**

Run the same command from Task 1 Step 4.

Expected: tests may still fail until the service and controller are wired, but compilation and repository path should be ready.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/product/domain/AdminProductListQueryRepository.java backend/src/main/java/com/example/ecommerce/product/domain/AdminProductListQueryRepositoryImpl.java backend/src/main/java/com/example/ecommerce/product/application/AdminProductListSort.java backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java
git commit -m "feat: add admin product list query infrastructure"
```

## Task 3: Wire controller and service parameters

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`

- [ ] **Step 1: Extend the controller contract**

Add optional parameters:

```java
@RequestParam(required = false) String status,
@RequestParam(required = false) String auditStatus,
@RequestParam(required = false) String publishStatus,
@RequestParam(required = false) String keyword,
@RequestParam(required = false) String sort,
```

And document them with `@Parameter`.

- [ ] **Step 2: Update `ProductCommandService.list(...)`**

Change the signature to:

```java
public ProductListResponse list(
    Long merchantId,
    String status,
    String auditStatus,
    String publishStatus,
    String keyword,
    String sort,
    int page,
    int pageSize
)
```

Behavior:

- keep current `page` and `pageSize` lower-bound clamp
- resolve effective merchant scope exactly as today
- validate supported workflow values
- parse sort through `AdminProductListSort.parse`
- delegate through `spuRepository.searchAdminProducts(...)`

Validation can use `BusinessException(COMMON_VALIDATION_FAILED, "...")`.

- [ ] **Step 3: Re-run the admin list test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductListTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java
git commit -m "feat: add admin product list filters and sorting"
```

## Task 4: Document and verify the admin list enhancement

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README**

Document the admin product list additions:

- `status`
- `auditStatus`
- `publishStatus`
- `keyword`
- `sort`

Supported sort values:

- `created_desc`
- `title_asc`
- `title_desc`

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

Expected: only admin product list filter/sort files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe admin product list query options"
```

## Spec Coverage Check

- Workflow filtering: covered by Tasks 1, 2, and 3.
- Keyword search: covered by Tasks 1 and 3.
- Supported sorting and default order: covered by Tasks 1, 2, and 3.
- Merchant-scope preservation: covered by Tasks 1 and 3.
- Documentation and final verification: covered by Task 4.

No spec sections are left without an implementation task.
