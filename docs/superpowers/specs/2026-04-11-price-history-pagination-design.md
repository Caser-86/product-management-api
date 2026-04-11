# Price History Pagination Design

- Date: 2026-04-11
- Status: Draft
- Scope: Upgrade admin SKU price-history reads with typed response DTOs and pagination

## 1. Goal

The project already records price history for manual and scheduled price changes,
but the current read endpoint still returns a loosely typed `Map` payload with
embedded JSON strings for old and new prices.

The goal of this phase is to:

- keep the existing endpoint path stable
- add pagination so history reads scale beyond small datasets
- replace JSON-string price payloads with typed response objects
- preserve existing merchant-scope and platform-admin authorization rules

## 2. Recommended Approach

Keep the existing endpoint:

- `GET /admin/skus/{skuId}/price-history`

Add:

- `page`
- `pageSize`

Return a typed response DTO instead of `Map<String, Object>`.

Why this approach:

- it improves API quality without changing clients to a new route
- it keeps this iteration focused on the most valuable read-path upgrade
- it avoids introducing filters that can be added later without breaking shape

## 3. Alternatives Considered

### 3.1 Recommended: typed DTO plus pagination

Pros:

- clear Swagger contract
- predictable payload shape
- bounded response size
- straightforward test coverage

Cons:

- still limited to `skuId` lookups

### 3.2 DTO plus pagination plus filters

Pros:

- more powerful admin querying

Cons:

- broader query logic
- larger validation surface
- not required for current project stage

### 3.3 DTO only, no pagination

Pros:

- smallest code change

Cons:

- unbounded response size
- likely future rework once history grows

## 4. Scope Boundaries

Included:

- typed response model for price-history reads
- `page` and `pageSize` query parameters
- response metadata: `page`, `pageSize`, `total`
- page-size clamping
- sorting newest first
- end-to-end controller coverage

Excluded:

- `changeType` filtering
- time-range filtering
- cross-SKU history queries
- schema changes to `price_history`

## 5. API Design

Endpoint remains:

- `GET /admin/skus/{skuId}/price-history`

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

- `changeType`
- `oldPrice`
- `newPrice`
- `reason`
- `operatorId`
- `createdAt`

Price objects should include:

- `listPrice`
- `salePrice`

## 6. Authorization Model

Platform admin:

- may query any SKU price history

Merchant admin:

- may query only SKUs belonging to the same merchant

Permission failure behavior:

- cross-merchant access remains `AUTH_MERCHANT_SCOPE_DENIED`
- missing SKU remains `PRODUCT_NOT_FOUND`

This matches current pricing write-path behavior.

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

The current pricing module can be upgraded without schema changes.

Recommended structure:

- add typed API DTOs in the `pricing.api` package
- extend `PriceHistoryRepository` with a pageable query ordered by newest first
- update `PricingService.history(...)` to accept page inputs and map entities into
  typed response items
- update `PricingController.history(...)` to parse request params and return the
  new DTO

The old and new price JSON should be parsed once in the service layer and exposed
as typed price objects to API consumers.

## 9. Response Model

Suggested DTO shape:

- `PriceHistoryResponse`
- nested `Item`
- nested `PriceSnapshot`

This keeps the response self-documenting and easier to evolve than nested maps.

## 10. Testing Strategy

### 10.1 Happy-path coverage

- manual price changes appear in history with typed `oldPrice` and `newPrice`
- scheduled price changes still appear with `changeType = scheduled`
- empty history returns `items: []`

### 10.2 Pagination coverage

- `page` and `pageSize` affect returned slice
- `pageSize` is clamped at `100`
- newest entries appear on earlier pages

### 10.3 Permission coverage

- merchant admin cannot query another merchant's SKU history
- missing SKU returns `PRODUCT_NOT_FOUND`

## 11. Documentation

Update backend documentation to mention:

- the new `page` and `pageSize` params
- the typed price-history response
- the maximum page size

## 12. Rollout Plan

Suggested implementation order:

1. add failing pricing controller tests for typed payloads and pagination
2. add response DTOs and pageable repository support
3. update service and controller to return the new shape
4. update README and run full verification

## 13. Success Criteria

This phase is complete when:

- `GET /admin/skus/{skuId}/price-history` returns typed price objects
- pagination metadata is included in the response
- result size is bounded by page-size clamping
- authorization behavior remains consistent
- full backend tests pass
