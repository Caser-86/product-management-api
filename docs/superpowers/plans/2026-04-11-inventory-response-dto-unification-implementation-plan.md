# Inventory Response DTO Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the remaining map-based inventory API responses with typed DTOs while preserving all existing routes, permissions, and business behavior.

**Architecture:** Keep the existing inventory endpoints intact, add focused response DTOs in the `inventory.api` package, and update `InventoryService` plus `InventoryController` to return those DTOs end to end. Use OpenAPI contract assertions and existing controller tests to verify the response-contract upgrade without widening the feature scope.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, Springdoc OpenAPI, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`
  Replace map-based `ApiResponse` signatures with typed DTO responses.
- `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
  Replace map-returning service methods with typed DTOs.
- `backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java`
  Add failing OpenAPI schema assertions proving the endpoints now expose typed response components.
- `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
  Keep end-to-end regression coverage and add one extra assertion where useful.
- `backend/README.md`
  Document the typed inventory response objects.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryReservationResponse.java`
  Response DTO for reserve and release endpoints.
- `backend/src/main/java/com/example/ecommerce/inventory/api/InventorySnapshotResponse.java`
  Response DTO for inventory snapshot reads.
- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryAdjustmentResponse.java`
  Response DTO for adjustment results.
- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryRefundResponse.java`
  Response DTO for refund results.

## Task 1: Add failing contract tests for typed inventory responses

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java`

- [ ] **Step 1: Add a failing OpenAPI assertion for reservation and release responses**

Add:

```java
.andExpect(jsonPath("$.paths['/inventory/reservations'].post.responses['200'].content['application/json'].schema.properties.data.$ref")
    .value("#/components/schemas/InventoryReservationResponse"))
.andExpect(jsonPath("$.paths['/inventory/reservations/{reservationId}/release'].post.responses['200'].content['application/json'].schema.properties.data.$ref")
    .value("#/components/schemas/InventoryReservationResponse"))
```

- [ ] **Step 2: Add a failing OpenAPI assertion for snapshot, adjustment, and refund responses**

Add:

```java
.andExpect(jsonPath("$.paths['/admin/skus/{skuId}/inventory'].get.responses['200'].content['application/json'].schema.properties.data.$ref")
    .value("#/components/schemas/InventorySnapshotResponse"))
.andExpect(jsonPath("$.paths['/admin/skus/{skuId}/inventory/adjustments'].post.responses['200'].content['application/json'].schema.properties.data.$ref")
    .value("#/components/schemas/InventoryAdjustmentResponse"))
.andExpect(jsonPath("$.paths['/admin/inventory/refunds'].post.responses['200'].content['application/json'].schema.properties.data.$ref")
    .value("#/components/schemas/InventoryRefundResponse"))
```

- [ ] **Step 3: Run the OpenAPI documentation test**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.api.OpenApiDocumentationTest' --no-daemon
```

Expected: FAIL because those endpoints still expose generic map responses.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java
git commit -m "test: cover inventory response contracts"
```

## Task 2: Add typed inventory response DTOs

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryReservationResponse.java`
- Create: `backend/src/main/java/com/example/ecommerce/inventory/api/InventorySnapshotResponse.java`
- Create: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryAdjustmentResponse.java`
- Create: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryRefundResponse.java`

- [ ] **Step 1: Create the reservation response DTO**

Create:

```java
public record InventoryReservationResponse(
    String reservationId,
    String status
) {
}
```

- [ ] **Step 2: Create the inventory snapshot response DTO**

Create:

```java
public record InventorySnapshotResponse(
    Long skuId,
    int totalQty,
    int availableQty,
    int reservedQty,
    int soldQty
) {
}
```

- [ ] **Step 3: Create the adjustment and refund response DTOs**

Create:

```java
public record InventoryAdjustmentResponse(
    Long skuId,
    int totalQty,
    int availableQty,
    int reservedQty,
    int soldQty,
    String reason,
    Long operatorId
) {
}
```

```java
public record InventoryRefundResponse(
    Long skuId,
    int totalQty,
    int availableQty,
    int reservedQty,
    int soldQty,
    String bizId,
    boolean restock,
    String reason,
    Long operatorId
) {
}
```

- [ ] **Step 4: Re-run the OpenAPI documentation test**

Run the same command from Task 1 Step 3.

Expected: tests still fail because controller/service methods have not switched to those DTOs yet, but compilation succeeds.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/api/InventoryReservationResponse.java backend/src/main/java/com/example/ecommerce/inventory/api/InventorySnapshotResponse.java backend/src/main/java/com/example/ecommerce/inventory/api/InventoryAdjustmentResponse.java backend/src/main/java/com/example/ecommerce/inventory/api/InventoryRefundResponse.java
git commit -m "feat: add inventory response dtos"
```

## Task 3: Switch service and controller methods to typed responses

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`

- [ ] **Step 1: Update service return types**

Change:

```java
public Map<String, Object> snapshot(Long skuId)
public Map<String, Object> adjust(Long skuId, int delta, String reason, Long operatorId)
public Map<String, Object> refund(Long skuId, String bizId, int quantity, boolean restock, String reason, Long operatorId)
public String reserve(String reservationId, String bizId, List<InventoryReservationRequest.Item> items)
public String release(String reservationId, String bizId)
```

To typed responses:

```java
public InventoryReservationResponse reserve(...)
public InventoryReservationResponse release(...)
public InventorySnapshotResponse snapshot(Long skuId)
public InventoryAdjustmentResponse adjust(...)
public InventoryRefundResponse refund(...)
```

- [ ] **Step 2: Replace map construction with DTO construction**

Use patterns like:

```java
return new InventorySnapshotResponse(
    skuId,
    balance.getTotalQty(),
    balance.getAvailableQty(),
    balance.getReservedQty(),
    balance.getSoldQty()
);
```

```java
return new InventoryReservationResponse(reservationId, "reserved");
```

```java
return new InventoryReservationResponse(reservationId, "released");
```

Keep fallback defaults consistent with current behavior:

- `reason == null ? "" : reason`
- `operatorId == null ? 0L : operatorId`

- [ ] **Step 3: Update controller signatures**

Change controller methods to:

```java
public ApiResponse<InventoryReservationResponse> reserve(...)
public ApiResponse<InventoryReservationResponse> release(...)
public ApiResponse<InventorySnapshotResponse> inventory(...)
public ApiResponse<InventoryAdjustmentResponse> adjust(...)
public ApiResponse<InventoryRefundResponse> refund(...)
```

Remove the intermediate `Map.of(...)` shaping from controller methods and simply return the service DTOs.

- [ ] **Step 4: Run focused tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.api.OpenApiDocumentationTest' --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java
git commit -m "feat: unify inventory response contracts"
```

## Task 4: Document and verify the inventory response upgrade

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README**

Document that these inventory endpoints now return typed DTOs:

- `POST /inventory/reservations`
- `POST /inventory/reservations/{reservationId}/release`
- `GET /admin/skus/{skuId}/inventory`
- `POST /admin/skus/{skuId}/inventory/adjustments`
- `POST /admin/inventory/refunds`

Mention the core response fields for each group:

- reservation: `reservationId`, `status`
- snapshot: inventory quantity fields
- adjustment/refund: quantity fields plus action metadata

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

Expected: only inventory-response-dto-unification related files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe inventory response dtos"
```

## Spec Coverage Check

- Typed DTO replacement for all remaining inventory map responses: covered by Tasks 2 and 3.
- Endpoint stability and behavior preservation: covered by Task 3 plus the focused test run.
- Documentation update: covered by Task 4.

No spec sections are left without an implementation task.
