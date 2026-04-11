# Storefront Search Filter and Sort Design

- Date: 2026-04-11
- Status: Draft
- Scope: Extend storefront product search with price-range filters and sorting

## 1. Goal

This design extends the storefront `GET /products` API so it behaves more like
a real ecommerce browse surface.

The project already supports:

- keyword matching on product title
- category filtering
- pagination
- visibility rules based on approved and published projection rows

What it does not yet support is:

- price-range filtering
- explicit sorting choices

The goal of this phase is to add those missing capabilities while preserving
the existing storefront projection model and API response shape.

## 2. Recommended Approach

Add optional `minPrice`, `maxPrice`, and `sort` query parameters to the current
`/products` endpoint and implement them directly against the existing
`storefront_product_search` projection table.

Why this approach:

- the projection already stores `min_price`, `max_price`, and `updated_at`
- no schema change is required
- the API grows naturally without introducing a second storefront search path

## 3. Alternatives Considered

### 3.1 Recommended: price range plus limited sorting on the existing projection

- add price-range query parameters
- add a small, explicit set of sort values
- keep the existing response DTO unchanged

Pros:

- strong user-facing improvement with low implementation risk
- no data migration or re-projection needed
- easy to test end-to-end

Cons:

- does not yet cover richer storefront facets such as stock-only or merchant
  filters

### 3.2 Lighter: price range only

Pros:

- minimal code changes

Cons:

- storefront browsing still lacks a basic ordering model

### 3.3 Heavier: richer facet and ranking model

- add stock-only filtering
- add multiple ranking modes
- add future relevance scoring rules

Pros:

- closer to a full marketplace search feature

Cons:

- noticeably broader scope than the current iteration needs

## 4. Scope Boundaries

Included:

- price-range filtering by optional minimum and maximum sale price
- explicit storefront sort choices
- controller parameter validation for search inputs
- repository and service updates against the search projection
- API and regression test coverage

Excluded:

- full-text relevance scoring
- stock-only filtering
- merchant filtering on storefront
- response shape changes
- schema changes to the projection table

## 5. API Design

Keep the existing endpoint:

- `GET /products`

Add optional query parameters:

- `minPrice`
- `maxPrice`
- `sort`

The endpoint will then support:

- `keyword`
- `categoryId`
- `minPrice`
- `maxPrice`
- `sort`
- `page`
- `pageSize`

### 5.1 Price filter semantics

- `minPrice` means product `maxPrice >= minPrice`
- `maxPrice` means product `minPrice <= maxPrice`

This allows product ranges such as multi-SKU items to remain searchable when
any sellable variant overlaps the requested shopper range.

### 5.2 Sort values

Recommended supported values:

- `newest`
- `price_asc`
- `price_desc`

Default:

- `newest`

Behavior:

- `newest` sorts by projection `updatedAt` descending, then `productId`
  descending for stable ordering
- `price_asc` sorts by `minPrice` ascending, then `productId` descending
- `price_desc` sorts by `maxPrice` descending, then `productId` descending

Reject unknown sort values with a validation error rather than silently falling
back.

## 6. Query and Data Model Semantics

No table change is needed because the existing projection already contains:

- `min_price`
- `max_price`
- `updated_at`

Filtering rules remain layered:

- projection row must not be deleted
- row must be `published`
- row must be `approved`
- keyword and category filters apply on top
- price-range filters apply on top
- sorting is applied last

## 7. Repository Design

The current derived-query repository methods will become unwieldy if every
filter and sort combination is added separately.

Recommended approach:

- move search querying to a specification-style or criteria-based repository
- keep visibility predicates centralized
- construct pageable sorting dynamically from the `sort` parameter

This keeps the query layer maintainable as storefront filters continue to grow.

## 8. Validation Rules

Recommended validation behavior:

- `page` and `pageSize` remain clamped to positive values as they are today
- `minPrice` and `maxPrice` must be non-negative
- if both are present, `minPrice` must not exceed `maxPrice`
- `sort` must be one of the supported enum values

Validation failures should use the existing API error envelope rather than
inventing a search-specific error format.

## 9. Testing Strategy

### 9.1 Controller coverage

- search by keyword and category still works
- price-range filters narrow results correctly
- sort values change storefront order correctly
- invalid sort returns a validation error
- invalid price range returns a validation error

### 9.2 Projection semantics regression

- price filters work with multi-SKU projected ranges
- visibility rules still exclude deleted, unpublished, or unapproved rows

### 9.3 Pagination stability

- pagination still reports total count correctly under filtered and sorted
  searches

## 10. Documentation

Update backend documentation to mention the new storefront query parameters and
their supported sort values.

## 11. Rollout Plan

Suggested implementation order:

1. extend controller and request parsing
2. add validated sort handling and price-range guards
3. move storefront query logic to a maintainable repository path
4. add regression tests for filter and sort behavior
5. update README and verify tests

## 12. Success Criteria

This phase is complete when:

- storefront shoppers can filter by price range
- storefront shoppers can choose supported sort orders
- invalid search parameters fail clearly
- existing visibility and pagination behavior remain correct
- fresh test verification passes
