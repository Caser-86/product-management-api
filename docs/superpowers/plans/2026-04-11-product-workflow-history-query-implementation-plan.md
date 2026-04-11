# Product Workflow History Query Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an admin endpoint for querying a product's workflow history while preserving existing merchant-scope rules and product response contracts.

**Architecture:** Reuse the existing `product_workflow_history` table and repository, add focused response DTOs for history reads, and extend `ProductCommandService` plus `AdminProductController` with one new read path. Keep deleted products readable through the audit endpoint so history remains useful after catalog cleanup.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java`
  Add the new workflow-history endpoint.
- `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
  Add the workflow-history read path and scope enforcement.
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java`
  Add end-to-end coverage for history reads, permissions, and ordering.
- `backend/README.md`
  Document the new admin history endpoint.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowHistoryResponse.java`
  Response DTO for workflow-history reads.

## Task 1: Add failing API tests for workflow-history reads

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java`

- [ ] **Step 1: Add a failing platform-admin history read test**

Add:

```java
@Test
void platform_admin_can_read_workflow_history() throws Exception {
    long productId = createPublishedProduct(3001L);
    workflowHistoryRepository.save(ProductWorkflowHistoryEntity.of(
        productId,
        "publish",
        "active",
        "active",
        "approved",
        "approved",
        "unpublished",
        "published",
        9012L,
        "PLATFORM_ADMIN",
        "publish event"
    ));

    mockMvc.perform(withBearer(get("/admin/products/{productId}/workflow-history", productId), platformAdminToken(9001L, 3001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items[0].action").value("publish"));
}
```

- [ ] **Step 2: Add failing merchant-scope and empty-history tests**

Add:

```java
@Test
void merchant_admin_can_read_own_product_history() throws Exception {
    long productId = createDraftProduct(3001L, "SPU-WF-HISTORY-OWN");

    mockMvc.perform(withBearer(get("/admin/products/{productId}/workflow-history", productId), merchantAdminToken(9101L, 3001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items.length()").value(0));
}
```

```java
@Test
void merchant_admin_cannot_read_other_merchant_history() throws Exception {
    long productId = createDraftProduct(5001L, "SPU-WF-HISTORY-OTHER");

    mockMvc.perform(withBearer(get("/admin/products/{productId}/workflow-history", productId), merchantAdminToken(9102L, 3001L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
}
```

- [ ] **Step 3: Add a failing ordering test**

Add:

```java
@Test
void workflow_history_is_sorted_newest_first() throws Exception {
    long productId = createDraftProduct(3001L, "SPU-WF-HISTORY-ORDER");
    workflowHistoryRepository.save(ProductWorkflowHistoryEntity.of(
        productId, "submit_for_review", "draft", "draft", "pending", "pending", "unpublished", "unpublished",
        9103L, "MERCHANT_ADMIN", "older entry"
    ));
    workflowHistoryRepository.save(ProductWorkflowHistoryEntity.of(
        productId, "approve", "draft", "active", "pending", "approved", "unpublished", "unpublished",
        9003L, "PLATFORM_ADMIN", "newer entry"
    ));

    mockMvc.perform(withBearer(get("/admin/products/{productId}/workflow-history", productId), platformAdminToken(9001L, 3001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].action").value("approve"));
}
```

- [ ] **Step 4: Run the workflow controller test class**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductWorkflowControllerTest' --no-daemon
```

Expected: FAIL because the history endpoint does not exist yet.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java
git commit -m "test: cover product workflow history reads"
```

## Task 2: Add workflow-history response DTO and service mapping

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowHistoryResponse.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`

- [ ] **Step 1: Create the response DTO**

Create:

```java
public record ProductWorkflowHistoryResponse(List<Item> items) {
    public record Item(
        String action,
        String fromStatus,
        String toStatus,
        String fromAuditStatus,
        String toAuditStatus,
        String fromPublishStatus,
        String toPublishStatus,
        Long operatorId,
        String operatorRole,
        String comment,
        LocalDateTime createdAt
    ) {
    }
}
```

- [ ] **Step 2: Add a workflow-history read method to the service**

Add a read method similar to:

```java
@Transactional(readOnly = true)
public ProductWorkflowHistoryResponse workflowHistory(Long productId) {
    ProductSpuEntity spu = spuRepository.findById(productId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
    assertWorkflowHistoryScope(spu);
    var items = productWorkflowHistoryRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
        .map(history -> new ProductWorkflowHistoryResponse.Item(
            history.getAction(),
            history.getFromStatus(),
            history.getToStatus(),
            history.getFromAuditStatus(),
            history.getToAuditStatus(),
            history.getFromPublishStatus(),
            history.getToPublishStatus(),
            history.getOperatorId(),
            history.getOperatorRole(),
            history.getComment(),
            history.getCreatedAt()
        ))
        .toList();
    return new ProductWorkflowHistoryResponse(items);
}
```

Add scope helper behavior:

- platform admin: allow
- merchant admin: only same merchant
- otherwise `PRODUCT_NOT_FOUND` for cross-merchant reads

- [ ] **Step 3: Re-run the workflow controller test class**

Run the same command from Task 1 Step 4.

Expected: tests still fail because the controller endpoint is not yet wired, but service code should compile.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowHistoryResponse.java backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java
git commit -m "feat: add product workflow history service"
```

## Task 3: Expose the admin workflow-history endpoint

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java`
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java`

- [ ] **Step 1: Add the controller endpoint**

Add:

```java
@GetMapping("/{productId}/workflow-history")
@Operation(summary = "Get workflow history", description = "Returns workflow history for a product.")
public ApiResponse<ProductWorkflowHistoryResponse> workflowHistory(@PathVariable Long productId) {
    return ApiResponse.success(productCommandService.workflowHistory(productId));
}
```

- [ ] **Step 2: Re-run the workflow controller test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductWorkflowControllerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java
git commit -m "feat: add admin workflow history endpoint"
```

## Task 4: Document and verify the workflow-history API

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README**

Document the new endpoint:

- `GET /admin/products/{productId}/workflow-history`

Mention:

- platform admins can read any product history
- merchant admins can read only their own merchant's products

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

Expected: only workflow-history-query related files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe workflow history endpoint"
```

## Spec Coverage Check

- Dedicated workflow-history endpoint: covered by Tasks 1 and 3.
- Merchant/platform permission rules: covered by Tasks 1 and 2.
- Empty-history and ordering behavior: covered by Task 1.
- Dedicated response DTOs: covered by Task 2.
- Documentation and final verification: covered by Task 4.

No spec sections are left without an implementation task.
