# Inventory History Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade `GET /admin/skus/{skuId}/inventory/history` to return typed paginated inventory-ledger responses while preserving current inventory authorization and error behavior.

**Architecture:** Keep the existing route, add focused response DTOs in the inventory API package, switch ledger reads to pageable repository access, and have `InventoryService` map ledger entities into typed response items with bounded page sizing. Preserve the current inventory-module semantics by continuing to validate the SKU through the inventory balance lookup before reading ledger rows.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`
  Add `page` and `pageSize` query parameters and return the typed response DTO.
- `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
  Add pageable history reads, clamp paging inputs, and map ledger rows into typed response items.
- `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryLedgerRepository.java`
  Add pageable repository access for SKU inventory history.
- `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
  Add end-to-end tests for typed payloads, pagination, and page-size clamping.
- `backend/README.md`
  Document the new inventory-history response shape and pagination parameters.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryHistoryResponse.java`
  Typed response DTO for paginated inventory-history reads.

## Task 1: Add failing controller tests for typed inventory-history reads

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`

- [ ] **Step 1: Add a failing test for typed inventory-history items**

Add:

```java
@Test
void inventory_history_returns_typed_items_with_paging_metadata() throws Exception {
    mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", ownSkuId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "delta": 3,
                  "reason": "manual restock",
                  "operatorId": 9001
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory/history", ownSkuId), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.pageSize").value(20))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].bizType").value("adjust"))
        .andExpect(jsonPath("$.data.items[0].bizId").value("manual restock"))
        .andExpect(jsonPath("$.data.items[0].deltaAvailable").value(3))
        .andExpect(jsonPath("$.data.items[0].deltaReserved").value(0));
}
```

- [ ] **Step 2: Add failing pagination and clamping tests**

Add:

```java
@Test
void inventory_history_supports_pagination() throws Exception {
    mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", ownSkuId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "delta": 1,
                  "reason": "restock-1",
                  "operatorId": 9001
                }
                """))
        .andExpect(status().isOk());
    mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", ownSkuId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "delta": 2,
                  "reason": "restock-2",
                  "operatorId": 9001
                }
                """))
        .andExpect(status().isOk());
    mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", ownSkuId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "delta": 3,
                  "reason": "restock-3",
                  "operatorId": 9001
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory/history", ownSkuId)
            .param("page", "2")
            .param("pageSize", "1"), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.pageSize").value(1))
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].bizId").value("restock-2"));
}
```

```java
@Test
void inventory_history_clamps_page_size_to_maximum() throws Exception {
    mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", ownSkuId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "delta": 1,
                  "reason": "restock-clamp",
                  "operatorId": 9001
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory/history", ownSkuId)
            .param("pageSize", "999"), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pageSize").value(100));
}
```

- [ ] **Step 3: Add a failing cross-merchant permission regression test**

Add:

```java
@Test
void merchant_admin_cannot_read_inventory_history_for_other_merchant_sku() throws Exception {
    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory/history", foreignSkuId), merchantAdminToken(9002L, 2001L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
}
```

- [ ] **Step 4: Run the inventory controller test class**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest' --no-daemon
```

Expected: FAIL because the endpoint still returns the old map-based shape with no pagination metadata.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java
git commit -m "test: cover paginated inventory history reads"
```

## Task 2: Add typed response DTOs and pageable repository support

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryHistoryResponse.java`
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryLedgerRepository.java`

- [ ] **Step 1: Create the typed response DTO**

Create:

```java
public record InventoryHistoryResponse(
    List<Item> items,
    int page,
    int pageSize,
    long total
) {
    public record Item(
        String bizType,
        String bizId,
        int deltaAvailable,
        int deltaReserved,
        LocalDateTime createdAt
    ) {
    }
}
```

- [ ] **Step 2: Add pageable repository support**

Replace the list-only method with:

```java
Page<InventoryLedgerEntity> findBySkuId(Long skuId, Pageable pageable);
```

Use a `PageRequest` sorted by:

```java
Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
```

- [ ] **Step 3: Re-run the inventory controller tests**

Run the same command from Task 1 Step 4.

Expected: tests still fail because service/controller mapping has not yet been upgraded, but compilation succeeds.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/api/InventoryHistoryResponse.java backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryLedgerRepository.java
git commit -m "feat: add typed inventory history dto"
```

## Task 3: Upgrade service and controller to paginated typed responses

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`

- [ ] **Step 1: Change the service history signature**

Update:

```java
public InventoryHistoryResponse history(Long skuId, int page, int pageSize)
```

Clamp inputs:

```java
int safePage = Math.max(page, 1);
int safePageSize = Math.min(Math.max(pageSize, 1), 100);
```

- [ ] **Step 2: Add pageable query and typed mapping**

Use a pageable query and map each ledger row into `InventoryHistoryResponse.Item`:

```java
var pageable = PageRequest.of(
    safePage - 1,
    safePageSize,
    Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
);
var pageResult = inventoryLedgerRepository.findBySkuId(skuId, pageable);
var items = pageResult.getContent().stream()
    .map(ledger -> new InventoryHistoryResponse.Item(
        ledger.getBizType(),
        ledger.getBizId(),
        ledger.getDeltaAvailable(),
        ledger.getDeltaReserved(),
        ledger.getCreatedAt()
    ))
    .toList();
return new InventoryHistoryResponse(items, safePage, safePageSize, pageResult.getTotalElements());
```

- [ ] **Step 3: Update the controller signature**

Change the endpoint to:

```java
public ApiResponse<InventoryHistoryResponse> history(
    @PathVariable Long skuId,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int pageSize
) {
    return ApiResponse.success(inventoryService.history(skuId, page, pageSize));
}
```

Add Swagger parameter descriptions for both paging params.

- [ ] **Step 4: Re-run the inventory controller test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java
git commit -m "feat: paginate inventory history"
```

## Task 4: Document and verify the inventory-history upgrade

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README**

Document:

- `GET /admin/skus/{skuId}/inventory/history?page=1&pageSize=20`
- typed inventory history item fields
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

Expected: only inventory-history-pagination related files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe paginated inventory history"
```

## Spec Coverage Check

- Typed response DTOs: covered by Tasks 1 to 3.
- Pagination and page-size clamping: covered by Tasks 1 and 3.
- Authorization and existing error semantics: covered by Task 1 and reused service validation in Task 3.
- Documentation and verification: covered by Task 4.

No spec sections are left without an implementation task.
