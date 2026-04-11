# Inventory History Pagination Design

- Date: 2026-04-11
- Status: Draft
- Scope: Upgrade admin SKU inventory-history reads with typed response DTOs and pagination

## 1. Goal

The inventory module already records immutable ledger entries for reserve,
confirm, release, adjust, and refund flows, but the current read endpoint still
returns a loosely typed `Map` payload with an unbounded `items` list.

The goal of this phase is to:

- keep the existing inventory-history endpoint path stable
- add pagination so ledger reads remain bounded
- replace the map-based payload with typed response DTOs
- preserve the current inventory permission and error-code behavior

## 2. Recommended Approach

Keep the existing endpoint:

- `GET /admin/skus/{skuId}/inventory/history`

Add:

- `page`
- `pageSize`

Return a typed response DTO instead of `Map<String, Object>`.

Why this approach:

- it aligns inventory history with the newly upgraded price-history API
- it improves Swagger and client readability without changing the route
- it avoids broadening scope into derived balance snapshots or ledger schema work

## 3. Alternatives Considered

### 3.1 Recommended: typed DTO plus pagination

Pros:

- consistent with price-history reads
- bounded response size
- clear contract for admin and audit tooling

Cons:

- does not yet show post-transaction inventory balances per row

### 3.2 DTO plus pagination plus resulting balance fields

Pros:

- richer operational audit output

Cons:

- current ledger table does not store resulting counters
- would require extra derivation logic or schema changes
- too broad for this iteration

### 3.3 DTO only, no pagination

Pros:

- smallest change

Cons:

- keeps an unbounded response
- leaves inventory history inconsistent with price history

## 4. Scope Boundaries

Included:

- typed response model for inventory-history reads
- `page` and `pageSize` query parameters
- response metadata: `page`, `pageSize`, `total`
- page-size clamping
- newest-first sorting
- end-to-end controller coverage

Excluded:

- resulting inventory snapshot fields per ledger row
- filtering by `bizType`
- time-range filtering
- schema changes to `inventory_ledger`

## 5. API Design

Endpoint remains:

- `GET /admin/skus/{skuId}/inventory/history`

New query parameters:

- `page` default `1`
- `pageSize` default `20`

Recommended maximum:

- `pageSize <= 100`

Response shape:

- `items`
- `page`
- `pageSize`
- `total`

Each item should include:

- `bizType`
- `bizId`
- `deltaAvailable`
- `deltaReserved`
- `createdAt`

## 6. Authorization and Error Model

Platform admin:

- may query any SKU inventory history

Merchant admin:

- may query only SKUs belonging to the same merchant

Error behavior should remain unchanged:

- missing SKU continues to return `COMMON_VALIDATION_FAILED`
- cross-merchant access continues to return `AUTH_MERCHANT_SCOPE_DENIED`

This preserves existing inventory-module semantics.

## 7. Query Behavior

Ordering should remain newest first:

- `createdAt desc`
- `id desc`

Parameter safety:

- `page < 1` becomes `1`
- `pageSize < 1` becomes `1`
- `pageSize > 100` becomes `100`

Empty history:

- return `items: []`
- still include pagination metadata

## 8. Application Design

No schema change is required.

Recommended structure:

- add typed API DTOs in the `inventory.api` package
- extend `InventoryLedgerRepository` with pageable lookup by `skuId`
- update `InventoryService.history(...)` to accept page inputs and return the
  typed response
- update `InventoryController.history(...)` to parse paging params and expose
  the new DTO

The endpoint should continue to validate the SKU through the current inventory
balance lookup so the module keeps its existing error-code behavior.

## 9. Response Model

Suggested DTO shape:

- `InventoryHistoryResponse`
- nested `Item`

This keeps the payload self-documenting while staying narrower than the pricing
history model because inventory ledger rows do not need nested value objects.

## 10. Testing Strategy

### 10.1 Happy-path coverage

- ledger entries are returned with typed fields
- newest entries appear first
- empty history returns `items: []`

### 10.2 Pagination coverage

- `page` and `pageSize` affect the returned slice
- `pageSize` is clamped to `100`
- total row count is returned

### 10.3 Permission coverage

- merchant admin cannot read another merchant's SKU history
- missing SKU behavior remains unchanged

## 11. Documentation

Update backend documentation to mention:

- `page` and `pageSize` for inventory-history reads
- the typed item fields
- the maximum page size

## 12. Rollout Plan

Suggested implementation order:

1. add failing inventory controller tests for typed payloads and pagination
2. add response DTOs and pageable repository support
3. update service and controller to return the new shape
4. update README and run full verification

## 13. Success Criteria

This phase is complete when:

- `GET /admin/skus/{skuId}/inventory/history` returns typed items
- pagination metadata is included in the response
- result size is bounded by page-size clamping
- inventory authorization behavior remains unchanged
- full backend tests pass
