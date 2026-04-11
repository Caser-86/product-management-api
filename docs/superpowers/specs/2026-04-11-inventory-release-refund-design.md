# Inventory Release and Refund Design

- Date: 2026-04-11
- Status: Draft
- Scope: Complete the reverse inventory flows for reservation release and
  refund handling

## 1. Goal

This design adds the two missing reverse paths in the current inventory
lifecycle:

- release reserved inventory when an unconfirmed order attempt is canceled
- refund previously sold inventory with an explicit restock choice

The project already supports reservation, confirmation, manual adjustment, and
inventory ledger history. Without release and refund flows, stock can move
forward but cannot recover through normal order cancellation or after-sale
operations.

The goal of this phase is to:

- support releasing a still-reserved reservation
- support refunding sold inventory with explicit `restock = true | false`
- keep current inventory balance counters and ledger model
- refresh storefront search projection after reverse inventory changes

## 2. Recommended Approach

Extend the existing inventory service and reservation model with two explicit
compensation actions:

- `release`
- `refund`

This phase should reuse:

- `inventory_balance`
- `inventory_reservation`
- `inventory_ledger`

Why this approach:

- it fits the current service shape with minimal churn
- it preserves the current reservation and balance semantics
- it delivers complete stock lifecycle coverage without introducing a new order
  transaction model

## 3. Alternatives Considered

### 3.1 Recommended: extend the current inventory model

- add release endpoint for existing reservations
- add refund endpoint for sold inventory
- record ledger rows for both reverse actions

Pros:

- smallest coherent change set
- easy to test with existing inventory code
- keeps current API style and persistence model

Cons:

- refund idempotency remains caller-driven in this phase
- reservation and refund history are still spread across current tables rather
  than a unified inventory transaction aggregate

### 3.2 Heavier redesign: introduce an inventory transaction aggregate

Pros:

- stronger long-term audit structure
- easier future extension for return-to-vendor, damaged stock, and exchanges

Cons:

- too large for the current phase
- would force broad refactoring across already working flows

### 3.3 Minimal internal-only service methods

Pros:

- very small implementation

Cons:

- leaves the system without explicit APIs
- weakens documentation and operational testability

## 4. Scope Boundaries

Included:

- release of unconfirmed reservations
- refund of confirmed/sold inventory
- explicit refund restock choice
- inventory ledger coverage for reverse flows
- storefront projection refresh after reverse flows

Excluded:

- refund idempotency keys or refund order tables
- reservation expiration jobs
- return merchandise authorization workflows
- damaged stock, quarantine stock, or warehouse location modeling
- multi-item reservation redesign

## 5. Inventory State Model

The current counters remain the source of truth:

- `availableQty`
- `reservedQty`
- `soldQty`
- `totalQty`

This phase adds two new stock-changing behaviors.

### 5.1 Release reservation

Allowed only for reservations still in `reserved` state.

State effect:

- `availableQty += quantity`
- `reservedQty -= quantity`
- `soldQty` unchanged
- `totalQty` unchanged

### 5.2 Refund

Allowed only when the SKU has enough `soldQty` to refund the requested
quantity.

If `restock = true`:

- `soldQty -= quantity`
- `availableQty += quantity`
- `totalQty` unchanged

If `restock = false`:

- `soldQty -= quantity`
- `availableQty` unchanged
- `totalQty` unchanged

This makes refund handling explicit rather than assuming every refund returns to
sellable stock.

## 6. Reservation Model Changes

`inventory_reservation.status` currently supports:

- `reserved`
- `confirmed`

Add:

- `released`

Rules:

- `reserved -> confirmed` stays valid
- `reserved -> released` becomes valid
- `confirmed -> released` is invalid
- `released -> confirmed` is invalid

The release path is not treated as silent success on repeated calls. Releasing
an already released or confirmed reservation should return a validation error so
duplicate callers are visible.

## 7. Inventory Ledger Design

The existing immutable `inventory_ledger` remains the audit trail.

Add new `bizType` values:

- `release`
- `refund_restock`
- `refund_no_restock`

Ledger semantics:

- release writes `deltaAvailable = +quantity`, `deltaReserved = -quantity`
- refund with restock writes `deltaAvailable = +quantity`, `deltaReserved = 0`
- refund without restock writes `deltaAvailable = 0`, `deltaReserved = 0`

The ledger continues to represent only available/reserved changes explicitly.
Sold quantity changes remain observable through the inventory snapshot before
and after operations plus the business type recorded in ledger.

## 8. API Endpoints

Add two endpoints.

### 8.1 Release reservation

- `POST /inventory/reservations/{reservationId}/release`

Request body:

- `bizId`

Response:

- `reservationId`
- `status = released`

Behavior:

- validates reservation existence
- validates `bizId` match
- validates reservation is still releasable
- updates counters and writes ledger

### 8.2 Refund inventory

- `POST /admin/inventory/refunds`

Request body:

- `bizId`
- `skuId`
- `quantity`
- `restock`
- `reason`
- `operatorId`

Response:

- updated inventory snapshot fields:
  - `skuId`
  - `totalQty`
  - `availableQty`
  - `reservedQty`
  - `soldQty`
- refund metadata:
  - `bizId`
  - `restock`
  - `reason`
  - `operatorId`

## 9. Security Model

Security follows current inventory rules.

- `release` requires authenticated access
- `refund` requires authenticated admin-side access
- `PLATFORM_ADMIN` may operate across merchants
- `MERCHANT_ADMIN` may only operate on its own merchant inventory

Merchant scope validation must remain server-side using the authenticated
context, never request body trust.

## 10. Service Design

Extend `InventoryService` with:

- `release(String reservationId, String bizId)`
- `refund(Long skuId, String bizId, int quantity, boolean restock, String reason, Long operatorId)`

Domain behavior additions:

- `InventoryReservationEntity.release()`
- inventory balance release behavior
- inventory balance refund behavior

Validation rules:

### 10.1 Release validation

- reservation must exist
- `bizId` must match reservation
- status must be `reserved`
- SKU inventory must exist

### 10.2 Refund validation

- `bizId` is required
- `quantity > 0`
- SKU inventory must exist
- `soldQty >= quantity`
- merchant scope must be valid

## 11. Storefront Projection Impact

Reverse stock changes must refresh the storefront projection just like reserve,
confirm, and adjust already do.

Refresh after:

- reservation release
- refund with restock
- refund without restock

Even when `restock = false`, the projection should still refresh because stock
state and downstream storefront visibility may still depend on the changed
inventory snapshot.

## 12. Error Handling

Expected failure cases:

- release on missing reservation
- release on reservation with mismatched `bizId`
- release on reservation already confirmed or already released
- refund with invalid quantity
- refund quantity greater than current `soldQty`
- merchant scope denied

Status mapping should follow existing business error handling:

- `400` for validation failures
- `403` for merchant scope denial
- `404` only where the project already uses explicit not-found semantics

## 13. Testing Strategy

### 13.1 Release tests

- reserved inventory can be released successfully
- confirmed reservation cannot be released
- already released reservation cannot be released again
- release writes a ledger row
- release refreshes storefront projection

### 13.2 Refund tests

- refund with `restock = true` decreases sold and increases available
- refund with `restock = false` decreases sold only
- refund writes the correct ledger business type
- invalid refund quantity is rejected
- merchant admin cannot refund another merchant's SKU

### 13.3 Regression tests

- existing reserve and confirm behavior remains unchanged
- inventory history includes new reverse flow ledger entries
- storefront search stock status updates after reverse flows

## 14. Documentation and Rollout

Update backend documentation with:

- release endpoint
- refund endpoint
- refund `restock` semantics
- example operational use cases

Suggested rollout order:

1. extend reservation and balance domain behavior
2. add release and refund service methods
3. expose new controller endpoints
4. add tests
5. update README

## 15. Success Criteria

This phase is complete when:

- unconfirmed reservations can be released safely
- confirmed inventory can be refunded with explicit restock behavior
- reverse inventory actions write ledger rows
- reverse actions refresh storefront projection
- automated tests cover release, refund, permissions, and regressions
