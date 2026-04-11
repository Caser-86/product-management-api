# Inventory Response DTO Unification Design

- Date: 2026-04-11
- Status: Draft
- Scope: Replace remaining map-based inventory API responses with typed DTOs

## 1. Goal

The inventory module still exposes several command and snapshot endpoints that
return `Map<String, Object>` payloads even though newer admin read APIs now use
typed DTOs.

The goal of this phase is to:

- keep existing inventory endpoint paths stable
- replace map-based response payloads with typed DTOs
- preserve existing inventory business behavior, permissions, and error codes
- make the inventory API contract consistent with the rest of the project

## 2. Recommended Approach

Upgrade the following endpoints in place:

- `POST /inventory/reservations`
- `POST /inventory/reservations/{reservationId}/release`
- `GET /admin/skus/{skuId}/inventory`
- `POST /admin/skus/{skuId}/inventory/adjustments`
- `POST /admin/inventory/refunds`

Return typed DTOs from both the controller and service layers instead of maps.

Why this approach:

- it gives the biggest consistency win for the smallest behavioral change
- it improves Swagger output and compile-time safety
- it avoids expanding scope into new inventory features or endpoint redesign

## 3. Alternatives Considered

### 3.1 Recommended: in-place DTO replacement

Pros:

- stable routes
- low product risk
- immediate API consistency improvement

Cons:

- does not reduce the total number of response types in the inventory module

### 3.2 DTO replacement plus domain-wide snapshot abstraction

Pros:

- more theoretically elegant response modeling

Cons:

- broader refactor
- less valuable than simply making the contracts explicit now

### 3.3 Partial upgrade for admin endpoints only

Pros:

- fewer files touched

Cons:

- leaves public reservation responses inconsistent
- guarantees another cleanup pass later

## 4. Scope Boundaries

Included:

- typed DTOs for reservation, release, snapshot, adjustment, and refund
- controller signatures updated to typed `ApiResponse<T>`
- service signatures updated to typed DTOs
- end-to-end controller coverage updates

Excluded:

- path changes
- new inventory features
- schema changes
- changing current permissions or error codes

## 5. Endpoint-to-DTO Mapping

### 5.1 Reservation endpoints

Use:

- `InventoryReservationResponse`

Fields:

- `reservationId`
- `status`

Used by:

- `POST /inventory/reservations`
- `POST /inventory/reservations/{reservationId}/release`

### 5.2 Inventory snapshot endpoint

Use:

- `InventorySnapshotResponse`

Fields:

- `skuId`
- `totalQty`
- `availableQty`
- `reservedQty`
- `soldQty`

Used by:

- `GET /admin/skus/{skuId}/inventory`

### 5.3 Inventory adjustment endpoint

Use:

- `InventoryAdjustmentResponse`

Fields:

- `skuId`
- `totalQty`
- `availableQty`
- `reservedQty`
- `soldQty`
- `reason`
- `operatorId`

Used by:

- `POST /admin/skus/{skuId}/inventory/adjustments`

### 5.4 Inventory refund endpoint

Use:

- `InventoryRefundResponse`

Fields:

- `skuId`
- `totalQty`
- `availableQty`
- `reservedQty`
- `soldQty`
- `bizId`
- `restock`
- `reason`
- `operatorId`

Used by:

- `POST /admin/inventory/refunds`

## 6. Authorization and Error Behavior

This phase should preserve the current semantics exactly:

- authenticated requirement for protected endpoints stays the same
- merchant scope checks stay the same
- missing inventory continues to use existing inventory-module error behavior
- release and refund validation behavior stays unchanged

No new error codes should be introduced.

## 7. Application Design

Recommended structure:

- add DTOs in `inventory.api`
- update `InventoryService` methods that currently return `Map<String, Object>`
  to return specific DTOs
- update `InventoryController` signatures to use typed `ApiResponse<T>`

The business logic should not move. This is a response-contract upgrade, not a
domain redesign.

## 8. Testing Strategy

### 8.1 Happy-path coverage

- reservation returns typed `reservationId` and `status`
- release returns typed `reservationId` and `status`
- inventory snapshot returns typed quantity fields
- adjustment returns typed quantity plus reason/operator fields
- refund returns typed quantity plus refund metadata fields

### 8.2 Regression coverage

- current authorization behavior remains unchanged
- current business behaviors remain unchanged
- storefront-related side effects still behave as before

## 9. Documentation

Update backend documentation to mention:

- the typed inventory response objects
- which endpoints now use which DTO shape

## 10. Rollout Plan

Suggested implementation order:

1. add failing controller tests that assert typed response fields
2. add the new DTO classes
3. update `InventoryService` and `InventoryController`
4. update README and run full verification

## 11. Success Criteria

This phase is complete when:

- the remaining map-based inventory responses are replaced by typed DTOs
- controller signatures are typed end to end
- existing inventory tests still pass
- full backend tests pass
