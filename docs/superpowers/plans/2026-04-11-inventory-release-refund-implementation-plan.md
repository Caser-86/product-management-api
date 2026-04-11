# Inventory Release and Refund Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add release and refund inventory flows so reserved stock can be returned and sold stock can be refunded with explicit restock behavior.

**Architecture:** Extend the current inventory lifecycle rather than redesigning it. Reservation release stays tied to the existing reservation entity and inventory balance counters, while refund operates directly on SKU inventory and writes explicit ledger business types. Both reverse flows refresh the storefront projection after stock changes.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, Spring Security, MySQL-backed inventory tables, JUnit 5, MockMvc

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryReservationEntity.java`
  Add `released` lifecycle support.
- `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryBalanceEntity.java`
  Add release and refund stock counter transitions.
- `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
  Add release/refund orchestration, validation, ledger writes, and projection refresh.
- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`
  Expose release and refund endpoints.
- `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
  Cover release and refund behavior.
- `backend/README.md`
  Document reverse inventory flows and refund restock semantics.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryReleaseRequest.java`
  Request DTO for reservation release.
- `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryRefundRequest.java`
  Request DTO for refund handling.

## Task 1: Extend reservation and balance domain behavior

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryReservationEntity.java`
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryBalanceEntity.java`
- Test: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`

- [ ] **Step 1: Write failing tests for release and refund counter behavior**

Add tests such as:

```java
@Test
void releases_reserved_inventory_back_to_available() throws Exception {
    String reservationId = reserveInventory(ownSkuId, "ORDER-REL-1", 2);

    mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/release", reservationId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"bizId":"ORDER-REL-1"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("released"));

    mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory", ownSkuId), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.availableQty").value(10))
        .andExpect(jsonPath("$.data.reservedQty").value(0));
}
```

```java
@Test
void refunds_with_restock_move_sold_back_to_available() throws Exception {
    String reservationId = reserveInventory(ownSkuId, "ORDER-REFUND-1", 2);
    confirmReservation(reservationId, "ORDER-REFUND-1");

    mockMvc.perform(withBearer(post("/admin/inventory/refunds"), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "bizId":"ORDER-REFUND-1",
                  "skuId": %d,
                  "quantity": 1,
                  "restock": true,
                  "reason": "customer cancellation",
                  "operatorId": 9001
                }
                """.formatted(ownSkuId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.availableQty").value(9))
        .andExpect(jsonPath("$.data.soldQty").value(1))
        .andExpect(jsonPath("$.data.restock").value(true));
}
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest.releases_reserved_inventory_back_to_available' --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest.refunds_with_restock_move_sold_back_to_available' --no-daemon
```

Expected: FAIL because release/refund endpoints and domain transitions do not exist yet.

- [ ] **Step 3: Add minimal reservation and balance behaviors**

Update `InventoryReservationEntity.java` with:

```java
public boolean isReleased() {
    return "released".equals(status);
}

public void release() {
    this.status = "released";
}
```

Update `InventoryBalanceEntity.java` with:

```java
public void release(int quantity) {
    if (reservedQty < quantity) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reserved quantity insufficient");
    }
    reservedQty -= quantity;
    availableQty += quantity;
}

public void refund(int quantity, boolean restock) {
    if (quantity <= 0) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "refund quantity must be positive");
    }
    if (soldQty < quantity) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "sold quantity insufficient");
    }
    soldQty -= quantity;
    if (restock) {
        availableQty += quantity;
    }
}
```

- [ ] **Step 4: Re-run the focused tests**

Run the same command from Step 2.

Expected: FAIL later in controller/service flow, proving the domain layer now supports the reverse transitions.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryReservationEntity.java backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryBalanceEntity.java backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java
git commit -m "feat: add inventory release and refund domain transitions"
```

## Task 2: Implement service-level release and refund orchestration

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- Test: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`

- [ ] **Step 1: Write failing tests for invalid reverse flows**

Add tests like:

```java
@Test
void confirmed_reservation_cannot_be_released() throws Exception {
    String reservationId = reserveInventory(ownSkuId, "ORDER-REL-2", 1);
    confirmReservation(reservationId, "ORDER-REL-2");

    mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/release", reservationId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"bizId":"ORDER-REL-2"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
}
```

```java
@Test
void refund_without_restock_only_reduces_sold_quantity() throws Exception {
    String reservationId = reserveInventory(ownSkuId, "ORDER-REFUND-2", 2);
    confirmReservation(reservationId, "ORDER-REFUND-2");

    mockMvc.perform(withBearer(post("/admin/inventory/refunds"), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "bizId":"ORDER-REFUND-2",
                  "skuId": %d,
                  "quantity": 1,
                  "restock": false,
                  "reason": "damaged return",
                  "operatorId": 9001
                }
                """.formatted(ownSkuId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.availableQty").value(8))
        .andExpect(jsonPath("$.data.soldQty").value(1))
        .andExpect(jsonPath("$.data.restock").value(false));
}
```

- [ ] **Step 2: Run the reverse-flow tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest.confirmed_reservation_cannot_be_released' --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest.refund_without_restock_only_reduces_sold_quantity' --no-daemon
```

Expected: FAIL because the service does not yet orchestrate release/refund behavior.

- [ ] **Step 3: Implement release and refund methods in `InventoryService`**

Add methods with logic like:

```java
@Transactional
public String release(String reservationId, String bizId) {
    InventoryReservationEntity reservation = inventoryReservationRepository.findById(reservationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation not found"));
    if (!reservation.hasBizId(bizId)) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation bizId mismatch");
    }
    if (reservation.isConfirmed() || reservation.isReleased()) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation cannot be released");
    }
    var balance = inventoryBalanceRepository.findById(reservation.getSkuId())
        .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
    assertMerchantScope(balance.getMerchantId());
    balance.release(reservation.getQuantity());
    reservation.release();
    inventoryLedgerRepository.save(
        InventoryLedgerEntity.of(reservation.getSkuId(), balance.getMerchantId(), "release", bizId, reservation.getQuantity(), -reservation.getQuantity())
    );
    refreshProjectionBySku(reservation.getSkuId());
    return reservationId;
}
```

```java
@Transactional
public Map<String, Object> refund(Long skuId, String bizId, int quantity, boolean restock, String reason, Long operatorId) {
    if (bizId == null || bizId.isBlank()) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "bizId is required");
    }
    var balance = inventoryBalanceRepository.findById(skuId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
    assertMerchantScope(balance.getMerchantId());
    balance.refund(quantity, restock);
    inventoryLedgerRepository.save(
        InventoryLedgerEntity.of(
            skuId,
            balance.getMerchantId(),
            restock ? "refund_restock" : "refund_no_restock",
            bizId,
            restock ? quantity : 0,
            0
        )
    );
    refreshProjectionBySku(skuId);
    return Map.of(
        "skuId", skuId,
        "totalQty", balance.getTotalQty(),
        "availableQty", balance.getAvailableQty(),
        "reservedQty", balance.getReservedQty(),
        "soldQty", balance.getSoldQty(),
        "bizId", bizId,
        "restock", restock,
        "reason", reason == null ? "" : reason,
        "operatorId", operatorId == null ? 0L : operatorId
    );
}
```

- [ ] **Step 4: Re-run the focused reverse-flow tests**

Run the same command from Step 2.

Expected: FAIL only because the controller endpoints and request DTOs are not yet wired.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java
git commit -m "feat: add inventory release and refund service flows"
```

## Task 3: Expose release and refund APIs

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryReleaseRequest.java`
- Create: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryRefundRequest.java`
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java`
- Test: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`

- [ ] **Step 1: Write failing API-level tests for auth and request wiring**

Add tests like:

```java
@Test
void anonymous_cannot_release_inventory() throws Exception {
    mockMvc.perform(post("/inventory/reservations/{reservationId}/release", "missing-auth")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"bizId":"ORDER-REL-3"}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
}
```

```java
@Test
void merchant_admin_cannot_refund_other_merchant_inventory() throws Exception {
    confirmReservation(reserveInventory(foreignSkuId, "ORDER-REFUND-3", 1), "ORDER-REFUND-3");

    mockMvc.perform(withBearer(post("/admin/inventory/refunds"), merchantAdminToken(9002L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "bizId":"ORDER-REFUND-3",
                  "skuId": %d,
                  "quantity": 1,
                  "restock": true,
                  "reason": "cross-merchant refund",
                  "operatorId": 9002
                }
                """.formatted(foreignSkuId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
}
```

- [ ] **Step 2: Run the focused API tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest.anonymous_cannot_release_inventory' --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest.merchant_admin_cannot_refund_other_merchant_inventory' --no-daemon
```

Expected: FAIL because the endpoints and request DTOs do not yet exist.

- [ ] **Step 3: Add request DTOs and controller endpoints**

Create `InventoryReleaseRequest.java`:

```java
package com.example.ecommerce.inventory.api;

import jakarta.validation.constraints.NotBlank;

public record InventoryReleaseRequest(@NotBlank(message = "bizId is required") String bizId) {
}
```

Create `InventoryRefundRequest.java`:

```java
package com.example.ecommerce.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventoryRefundRequest(
    @NotBlank(message = "bizId is required") String bizId,
    @NotNull(message = "skuId is required") Long skuId,
    @Min(value = 1, message = "quantity must be greater than 0") int quantity,
    @NotNull(message = "restock is required") Boolean restock,
    String reason,
    Long operatorId
) {
}
```

Update `InventoryController.java` with:

```java
@PostMapping("/inventory/reservations/{reservationId}/release")
public ApiResponse<Map<String, Object>> release(@PathVariable String reservationId, @Valid @RequestBody InventoryReleaseRequest request) {
    String releasedReservationId = inventoryService.release(reservationId, request.bizId());
    return ApiResponse.success(Map.of("reservationId", releasedReservationId, "status", "released"));
}

@PostMapping("/admin/inventory/refunds")
public ApiResponse<Map<String, Object>> refund(@Valid @RequestBody InventoryRefundRequest request) {
    return ApiResponse.success(
        inventoryService.refund(
            request.skuId(),
            request.bizId(),
            request.quantity(),
            request.restock(),
            request.reason(),
            request.operatorId()
        )
    );
}
```

- [ ] **Step 4: Re-run the focused API tests**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/api/InventoryReleaseRequest.java backend/src/main/java/com/example/ecommerce/inventory/api/InventoryRefundRequest.java backend/src/main/java/com/example/ecommerce/inventory/api/InventoryController.java backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java
git commit -m "feat: add inventory release and refund endpoints"
```

## Task 4: Add ledger, projection, and history regression coverage

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`

- [ ] **Step 1: Write failing regression tests for ledger and storefront effects**

Add tests like:

```java
@Test
void release_creates_inventory_ledger_entry() throws Exception {
    String reservationId = reserveInventory(ownSkuId, "ORDER-REL-4", 2);

    mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/release", reservationId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"bizId":"ORDER-REL-4"}
                """))
        .andExpect(status().isOk());

    assertThat(inventoryLedgerRepository.findAll())
        .anySatisfy(ledger -> assertThat(ledger.getBizType()).isEqualTo("release"));
}
```

```java
@Test
void refund_restock_updates_storefront_stock_status() throws Exception {
    // prepare sold inventory down to out_of_stock, refund with restock=true, then assert projection becomes in_stock
}
```

- [ ] **Step 2: Run regression tests to verify gaps**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.inventory.api.InventoryControllerTest' --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --no-daemon
```

Expected: FAIL if any ledger business type or projection refresh path is missing.

- [ ] **Step 3: Tighten assertions and helpers as needed**

Ensure the tests explicitly verify:

- `release`
- `refund_restock`
- `refund_no_restock`

appear in ledger history with correct deltas, and storefront stock status changes when reverse flows affect available stock.

- [ ] **Step 4: Re-run the regression batch**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/inventory/api/InventoryControllerTest.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java
git commit -m "test: cover inventory reverse flow regressions"
```

## Task 5: Document and verify reverse inventory flows

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README with release and refund usage**

Add a section like:

```md
## Reverse Inventory Flows

The API supports two reverse inventory operations:

- `POST /inventory/reservations/{reservationId}/release`
- `POST /admin/inventory/refunds`

Use reservation release when an unconfirmed order attempt is canceled.
Use refunds after sold inventory must be reversed.

Refund requests must explicitly set `restock`:

- `true`: move refunded quantity back to `availableQty`
- `false`: decrease `soldQty` without adding sellable stock
```

- [ ] **Step 2: Run full verification**

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

Expected: only the planned inventory reverse-flow files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: add reverse inventory flow guide"
```

## Spec Coverage Check

- Release reservation flow: covered by Tasks 1, 2, and 3.
- Refund with explicit restock choice: covered by Tasks 1, 2, and 3.
- Ledger coverage for reverse flows: covered by Task 4.
- Storefront projection refresh after reverse flows: covered by Tasks 2 and 4.
- Security and merchant scope behavior: covered by Task 3.
- Documentation and verification: covered by Task 5.

No spec requirements are left without a matching implementation task.
