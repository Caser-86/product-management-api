# Price Schedule List Design

- Date: 2026-04-11
- Status: Draft
- Scope: Add an admin SKU-scoped price schedule list endpoint with typed responses and pagination

## 1. Goal

The pricing module already supports:

- immediate price updates
- price-history reads
- scheduled future price creation
- manual and automatic schedule application

What it still lacks is a formal read path for viewing scheduled price changes for
an individual SKU. That leaves admin users able to create schedules without a
matching way to inspect them through the API.

The goal of this phase is to:

- add a stable read endpoint for SKU price schedules
- keep the scope limited to SKU-scoped reads
- return a typed paginated response
- preserve current merchant-scope authorization rules
- avoid introducing global schedule search before it is needed

## 2. Recommended Approach

Add a new endpoint:

- `GET /admin/skus/{skuId}/price-schedules`

The endpoint should:

- read schedules for one SKU
- support `page` and `pageSize`
- sort newest-effective schedules first
- return typed target-price objects instead of raw JSON payload strings

Why this approach:

- it closes the most obvious pricing workflow gap with minimal risk
- it fits the existing SKU-scoped pricing API structure
- it avoids prematurely designing a global scheduling console

## 3. Alternatives Considered

### 3.1 Recommended: SKU-scoped schedule list

Pros:

- smallest useful feature
- aligns with current route structure
- straightforward authorization model
- easy to test and document

Cons:

- does not provide cross-SKU schedule operations

### 3.2 Global admin schedule list

Pros:

- stronger operational visibility
- better for future operations dashboards

Cons:

- significantly broader query design
- more filtering and indexing work
- more complex permission surface

### 3.3 SKU list plus schedule detail endpoint

Pros:

- more complete schedule read surface

Cons:

- more DTOs and routes than needed for this phase
- unnecessary expansion for the current gap

## 4. Scope Boundaries

Included:

- `GET /admin/skus/{skuId}/price-schedules`
- typed response DTOs
- `page` and `pageSize`
- page-size clamping
- SKU-scoped permission enforcement
- OpenAPI and controller regression coverage

Excluded:

- global schedule list APIs
- schedule-detail APIs
- filtering by status or time window
- schedule cancellation or editing
- schema changes to `price_schedule`

## 5. API Design

New endpoint:

- `GET /admin/skus/{skuId}/price-schedules`

Query parameters:

- `page` default `1`
- `pageSize` default `20`

Recommended maximum:

- `pageSize <= 100`

Ordering:

- `effectiveAt desc`
- `id desc`

Response shape:

- `items`
- `page`
- `pageSize`
- `total`

Each item should include:

- `scheduleId`
- `status`
- `effectiveAt`
- `expireAt`
- `targetPrice`
- `createdAt`

`targetPrice` should include:

- `listPrice`
- `salePrice`

## 6. Authorization Model

Platform admin:

- may query price schedules for any SKU

Merchant admin:

- may query only SKUs belonging to the same merchant

Failure behavior should follow current pricing semantics:

- cross-merchant access remains `AUTH_MERCHANT_SCOPE_DENIED`
- missing SKU remains `PRODUCT_NOT_FOUND`

## 7. Query Behavior

Pagination safety:

- `page < 1` becomes `1`
- `pageSize < 1` becomes `1`
- `pageSize > 100` becomes `100`

Empty data behavior:

- return `items: []`
- still include `page`, `pageSize`, and `total`

The response should expose schedule payload data as typed price objects rather
than leaking stored JSON strings.

## 8. Application Design

Recommended structure:

- add schedule-list response DTOs in `pricing.api`
- extend `PriceScheduleRepository` with a pageable SKU-scoped query ordered by
  `effectiveAt desc, id desc`
- add a `PricingService.scheduleList(Long skuId, int page, int pageSize)` read
  method
- parse stored target-price JSON in the service layer
- update `PricingController` with the new GET endpoint

This keeps JSON parsing and pagination logic in the pricing module, not in the
controller.

## 9. Response Model

Suggested DTO shape:

- `PriceScheduleListResponse`
- nested `Item`
- nested `PriceSnapshot`

This matches the current typed-response direction already used by:

- price history
- inventory history
- inventory command responses

## 10. Testing Strategy

### 10.1 Happy-path coverage

- schedule list returns created schedules for the requested SKU
- items expose typed `targetPrice`
- pending and applied schedules both serialize correctly

### 10.2 Pagination coverage

- `page` and `pageSize` affect the returned slice
- `pageSize` is clamped at `100`
- ordering is `effectiveAt desc, id desc`

### 10.3 Permission coverage

- merchant admin cannot query another merchant's SKU schedules
- platform admin can query cross-merchant schedules
- missing SKU returns `PRODUCT_NOT_FOUND`

## 11. Documentation

Update backend documentation to mention:

- the new `GET /admin/skus/{skuId}/price-schedules` endpoint
- supported pagination params
- typed response fields

Swagger should also expose the response schema so admin consumers can inspect
the target price payload shape directly.

## 12. Rollout Plan

Suggested implementation order:

1. add failing controller and OpenAPI assertions
2. add list response DTOs
3. add repository query and service mapping
4. add controller endpoint
5. update README and run full backend verification

## 13. Success Criteria

This phase is complete when:

- admin users can list schedules for a SKU through the API
- the response is paginated and strongly typed
- merchant-scope rules are enforced
- Swagger documents the new endpoint
- full backend tests pass
