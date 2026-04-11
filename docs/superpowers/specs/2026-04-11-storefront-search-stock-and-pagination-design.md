# Storefront Search Stock and Pagination Design

- Date: 2026-04-11
- Status: Draft
- Scope: Add in-stock-only storefront filtering and stronger pagination guards

## 1. Goal

This design improves storefront browse behavior in two practical ways:

- shoppers can choose to see only products that are currently in stock
- the search API enforces clearer pagination bounds for more stable behavior

The project already supports:

- keyword filtering
- category filtering
- price-range filtering
- explicit sorting
- visibility rules backed by the storefront projection

What remains thin is:

- no shopper-facing way to hide out-of-stock items
- no upper bound on `pageSize`

The goal of this phase is to close those gaps without changing the response
shape or projection schema.

## 2. Recommended Approach

Add an optional `inStockOnly` query parameter to `GET /products`, and clamp
pagination inputs to a documented range in the storefront service.

Why this approach:

- the projection already stores `availableQty` and `stockStatus`
- no data migration is needed
- it improves usability and API safety with a very small surface-area change

## 3. Alternatives Considered

### 3.1 Recommended: in-stock filter plus bounded pagination

- add `inStockOnly=true|false`
- apply it against projection inventory fields
- cap `pageSize` to a documented maximum such as `100`

Pros:

- high user value with low implementation risk
- protects the search API from oversized page requests
- stays fully inside the current storefront search architecture

Cons:

- does not yet add richer availability facets like preorder or low-stock

### 3.2 Lighter: in-stock filter only

Pros:

- even smaller change

Cons:

- oversized pages remain possible

### 3.3 Heavier: full availability facet model

- add `in_stock`, `out_of_stock`, and future availability states
- add low-stock sorting or stock-priority rules

Pros:

- closer to a full marketplace browse experience

Cons:

- broader than the project currently needs

## 4. Scope Boundaries

Included:

- optional in-stock-only filtering
- documented storefront pagination bounds
- stable-order regression coverage when sort keys tie
- controller and README updates

Excluded:

- projection schema changes
- new response fields
- low-stock thresholds
- inventory reservation visibility nuance
- facet counts or aggregations

## 5. API Design

Keep the existing endpoint:

- `GET /products`

Add one new optional parameter:

- `inStockOnly`

Resulting storefront query surface:

- `keyword`
- `categoryId`
- `minPrice`
- `maxPrice`
- `sort`
- `inStockOnly`
- `page`
- `pageSize`

### 5.1 In-stock semantics

When `inStockOnly=true`, the storefront should return only rows where:

- `availableQty > 0`

This is more robust than checking only `stockStatus`, because it anchors the
filter to the stored numeric inventory value.

When `inStockOnly` is absent or `false`, no additional availability filter is
applied.

## 6. Pagination Rules

Current behavior clamps `page` and `pageSize` to at least 1.

This phase should add an upper bound:

- `pageSize` maximum: `100`

Recommended rules:

- `page < 1` becomes `1`
- `pageSize < 1` becomes `1`
- `pageSize > 100` becomes `100`

This keeps the current forgiving API style rather than turning pagination into
hard validation errors.

## 7. Sorting Stability

The storefront search already has explicit sorts. This phase should preserve
stable paging by ensuring all sorts still include a deterministic secondary key:

- `productId` descending

Regression tests should cover tied-price cases so paging order stays stable
even when multiple rows share the same primary sort field.

## 8. Query Design

The custom storefront repository should be extended, not replaced.

Add one optional filter input:

- `Boolean inStockOnly`

Behavior:

- if `inStockOnly == Boolean.TRUE`, add `availableQty > 0`
- otherwise skip the filter

All existing predicates remain intact:

- not deleted
- published
- approved
- optional keyword
- optional category
- optional price range

## 9. Testing Strategy

### 9.1 Controller coverage

- `inStockOnly=true` excludes out-of-stock rows
- `inStockOnly=false` preserves mixed availability results
- page size is capped at the documented max

### 9.2 Sorting stability

- tied `price_asc` rows still return in deterministic order
- tied `newest` rows still return in deterministic order

### 9.3 Regression coverage

- existing keyword, category, and price filters still work
- visibility rules still exclude unpublished or unapproved rows

## 10. Documentation

Update backend documentation to mention:

- `inStockOnly`
- storefront page-size cap
- supported behavior when shoppers want only available products

## 11. Rollout Plan

Suggested implementation order:

1. extend storefront tests with in-stock and pagination-bound cases
2. add the optional filter to the custom repository path
3. clamp pagination in the storefront service
4. update the controller and README
5. run full backend verification

## 12. Success Criteria

This phase is complete when:

- shoppers can filter storefront results to only in-stock products
- storefront page size is bounded consistently
- sort order remains deterministic under ties
- full backend tests pass after the change
