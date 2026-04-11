# Storefront Search Rebuild Design

- Date: 2026-04-11
- Status: Draft
- Scope: Admin rebuild and recovery path for the MySQL-backed storefront search
  projection

## 1. Goal

This design adds an explicit recovery path for the `storefront_product_search`
projection.

The project already refreshes storefront search rows synchronously during
product, inventory, pricing, and workflow writes. That keeps normal request
paths consistent, but there is currently no operator-facing way to recover from
projection corruption, schema backfill needs, or missed refreshes.

The goal of this phase is to provide:

- a platform-admin-only single-product refresh endpoint
- a platform-admin-only synchronous full rebuild endpoint
- deterministic rebuild behavior that reuses the existing projection logic
- clear rebuild results with per-product failure reporting

This phase is intentionally operational and corrective. It does not change the
public storefront API contract.

## 2. Recommended Approach

Add admin-only synchronous rebuild endpoints that orchestrate projection refresh
through the existing `ProductSearchProjector`.

Why this approach:

- it solves the current recovery gap with low complexity
- it stays inside the existing MySQL and application architecture
- it gives operators a deterministic repair path without introducing background
  jobs yet

## 3. Alternatives Considered

### 3.1 Recommended: synchronous admin-triggered rebuild

- add admin endpoints for single-product refresh and full rebuild
- page through products and call projector refresh per product
- return rebuild counts and failure details in the response

Pros:

- simplest real recovery path
- easy to test end-to-end
- no extra infrastructure or scheduling model

Cons:

- full rebuild request time grows with product count
- not ideal for very large catalogs

### 3.2 Automatic startup rebuild

Pros:

- hides some operational work

Cons:

- makes startup time unpredictable
- risks unnecessary rebuilds
- harder to control in production

### 3.3 Asynchronous rebuild job model

Pros:

- better fit for large catalogs and long-running jobs

Cons:

- adds significant orchestration complexity
- broader than the current project stage requires

## 4. Scope Boundaries

Included:

- single-product storefront projection refresh
- full synchronous storefront projection rebuild
- rebuild result reporting
- platform-admin authorization for rebuild operations

Excluded:

- asynchronous job tracking
- scheduled automatic rebuilds
- projection row inspection APIs
- distributed locking or multi-node rebuild coordination
- any change to storefront search response shape

## 5. API Endpoints

Add two admin endpoints:

- `POST /admin/search/storefront/products/{productId}/refresh`
- `POST /admin/search/storefront/rebuild`

Authorization:

- both endpoints require authentication
- both endpoints require `PLATFORM_ADMIN`
- `MERCHANT_ADMIN` must receive forbidden access

### 5.1 Single-product refresh response

Return a lightweight success payload containing at least:

- `productId`
- `status`

Suggested statuses:

- `refreshed`
- `deleted`

`deleted` covers cases where the projector intentionally removes the row because
the product has no remaining SKU projection basis.

### 5.2 Full rebuild response

Return a summary object containing:

- `processedCount`
- `successCount`
- `failureCount`
- `durationMs`
- `failures`

Each failure entry should include:

- `productId`
- `errorCode`
- `message`

The endpoint should still return HTTP 200 when some products fail, because the
operator needs the aggregate result rather than an all-or-nothing response.

## 6. Service Design

Introduce an admin-side orchestration service in the search module. Suggested
name: `StorefrontSearchAdminService`.

Responsibilities:

- refresh one product projection row
- rebuild all product projection rows in batches
- capture per-product failures during full rebuild
- report totals and timing

Suggested methods:

- `refreshProduct(Long productId)`
- `rebuildAll()`

This service should orchestrate rebuild work. It should not own projection
computation details.

## 7. Projector Responsibilities

Keep `ProductSearchProjector` as the single-product projection writer.

Responsibilities that stay in the projector:

- load one product with SKUs
- aggregate price and inventory state
- upsert or delete the storefront projection row

Responsibilities that should not move into the projector:

- scanning all products
- batch pagination
- rebuild summary aggregation
- failure reporting

This keeps the projector focused and lets recovery orchestration evolve
independently later.

## 8. Full Rebuild Flow

Recommended rebuild flow:

1. page through product IDs from `product_spu`
2. for each product ID, call `ProductSearchProjector.refresh(productId)`
3. capture any exception, record a failure entry, and continue
4. return a rebuild summary after all pages complete

Batching guidance:

- use a fixed page size such as 100
- operate on stable ordered paging by product ID
- avoid loading the whole catalog into memory

This phase prefers resilience over transaction-wide atomicity. A single broken
product must not block recovery for the rest of the catalog.

## 9. Data and Visibility Semantics

Rebuild must preserve the same storefront visibility rules already enforced by
the search read path:

- `product_status != deleted`
- `publish_status = published`
- `audit_status = approved`

Important behavior:

- rebuild should refresh all products, not only currently visible ones
- invisible products may remain in the projection table if the projector keeps
  rows and storefront search filters them out
- if a product has no SKU basis, the projector may delete the row

This keeps rebuild semantics aligned with current projection behavior rather
than introducing a second set of rules.

## 10. Security Model

Rebuild endpoints are operational controls and should be restricted to
`PLATFORM_ADMIN`.

Rules:

- `PLATFORM_ADMIN` can refresh any product and run full rebuild
- `MERCHANT_ADMIN` cannot invoke either rebuild endpoint
- anonymous callers receive unauthenticated responses

The implementation should reuse the existing JWT-authenticated security and
service-layer role checks rather than adding special-case header handling.

## 11. Error Handling

### 11.1 Single-product refresh

- if the product does not exist, return `PRODUCT_NOT_FOUND`
- if projection refresh succeeds, return success immediately
- if refresh fails for another reason, surface the normal error response

### 11.2 Full rebuild

- continue after individual product failures
- include failures in the response
- return success envelope even when `failureCount > 0`

This is intentional because rebuild is a maintenance operation, and the
operator needs the full result set to decide on follow-up action.

## 12. Testing Strategy

### 12.1 Authorization tests

- anonymous callers cannot hit rebuild endpoints
- merchant admins cannot hit rebuild endpoints
- platform admins can hit rebuild endpoints

### 12.2 Single-product refresh tests

- deleting or corrupting one projection row can be repaired through refresh
- refreshed product becomes searchable again when storefront-visible
- non-existent product returns `PRODUCT_NOT_FOUND`

### 12.3 Full rebuild tests

- missing projection rows are restored after rebuild
- rebuild continues when one product refresh fails
- failure summary includes the failed product ID and error information
- rebuild output counts match processed outcomes

### 12.4 Visibility regression tests

- rebuild does not make non-approved or unpublished products visible
- storefront search after rebuild still returns only `approved + published`
  products

## 13. Documentation and Operations

Update backend documentation with:

- the new rebuild endpoints
- the platform-admin-only restriction
- when to use single refresh versus full rebuild
- the shape of rebuild summary responses

This phase does not require CLI tooling or a scheduler. The API itself is the
operator entry point.

## 14. Rollout Plan

Suggested implementation order:

1. add admin rebuild service and response models
2. add admin controller endpoints
3. add platform-admin authorization checks
4. add rebuild tests and regression tests
5. document the maintenance workflow

No data migration is required for this phase because the projection table
already exists.

## 15. Success Criteria

This phase is complete when:

- platform admins can refresh one product projection on demand
- platform admins can run a full synchronous projection rebuild
- rebuild returns actionable counts and failure details
- storefront visibility rules remain unchanged after rebuild
- automated tests cover authorization, rebuild correctness, and failure
  handling
