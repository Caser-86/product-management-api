# Storefront Search Projection Design

- Date: 2026-04-10
- Status: Draft
- Scope: Lightweight MySQL-backed storefront search projection for `GET /products`

## 1. Goal

This design introduces a dedicated storefront search projection so that
`GET /products` no longer reads the transactional product, inventory, and
pricing tables on every request.

The goal is to create a minimal but real read model that:

- supports the current storefront search response shape
- keeps deployment simple by staying inside MySQL
- improves query structure without introducing Elasticsearch yet

This phase only covers the storefront list/search endpoint. It does not attempt
to build a complete storefront detail read model.

## 2. Recommended Approach

Use a single MySQL projection table dedicated to storefront search and update it
from application code whenever product, price, or inventory data changes.

Why this approach:

- it solves the current N+1 style search reads with low operational overhead
- it keeps the current project architecture simple
- it leaves a clean upgrade path to asynchronous projection or a search engine
  later

## 3. Alternatives Considered

### 3.1 Recommended: MySQL projection table for storefront search only

- add one projection table
- query that table directly from `StorefrontSearchService`
- refresh projection rows from existing services

Pros:

- clear performance win over current runtime aggregation
- small scope and low deployment complexity
- easy to test end-to-end

Cons:

- projection updates remain synchronous for now
- search capabilities stay limited compared with a search engine

### 3.2 Bigger scope: projection table plus storefront detail pre-aggregation

Pros:

- more future-facing storefront read model

Cons:

- broadens this phase considerably
- requires extra field modeling and more synchronization logic

### 3.3 Smaller scope: optimize current database query only

Pros:

- minimal changes

Cons:

- leaves the structural problem in place
- likely requires another redesign soon

## 4. Scope Boundaries

Included:

- storefront `GET /products` reads from the projection
- product, price, and inventory changes refresh projection rows
- projection visibility rules for storefront search

Excluded:

- storefront detail projection
- Elasticsearch or OpenSearch integration
- asynchronous message bus consumers
- admin-side search projection reuse

## 5. Projection Model

Add a new table named `storefront_product_search`.

Suggested columns:

- `product_id`
- `merchant_id`
- `category_id`
- `title`
- `primary_sku_id`
- `min_price`
- `max_price`
- `available_qty`
- `stock_status`
- `product_status`
- `publish_status`
- `audit_status`
- `updated_at`

Design notes:

- one row per storefront-searchable product
- `primary_sku_id` anchors price and inventory lookups when the product has at
  least one SKU
- `min_price` and `max_price` are stored denormalized for cheap reads
- `available_qty` and `stock_status` are stored denormalized for cheap reads

## 6. Visibility Rules

The projection should only be returned by storefront search when the product is
storefront-visible.

This phase defines storefront-visible as:

- `product_status != deleted`
- `publish_status = published`
- `audit_status = approved`

If a product falls out of storefront visibility, the projection row may either:

- remain in the table but be filtered out by query, or
- be deleted from the table

For this phase, keeping the row and filtering by status is preferred because it
keeps projection refresh logic simpler and more explicit.

## 7. Projection Update Strategy

This phase uses synchronous in-application projection refresh rather than a
fully asynchronous outbox consumer.

Refresh triggers:

- product create/update/delete
- inventory adjust/reserve/confirm
- price update
- scheduled price application

Implementation approach:

- introduce a projection writer/coordinator in the search module
- refresh the affected product row after each successful business transaction
- reuse the existing `ProductSearchProjector` as the projection entry point

The current `OutboxEvent` type may remain for future evolution, but this phase
does not require a background event pipeline.

## 8. Read Path Changes

`StorefrontSearchService` should stop joining across product, SKU, price, and
inventory tables at request time.

Instead it should:

- query `storefront_product_search`
- filter by keyword and category
- paginate directly on the projection
- map rows into the existing `StorefrontSearchResponse`

This keeps the storefront API contract stable while changing the backing read
strategy.

## 9. Write Path Changes

### 9.1 Product changes

When a product is created or updated:

- compute the current projection values
- upsert the row for that product

When a product is deleted:

- refresh the row so storefront visibility rules exclude it

### 9.2 Inventory changes

After inventory adjustments, reservations, or confirmations:

- recompute `available_qty`
- recompute `stock_status`
- refresh the affected product row

### 9.3 Pricing changes

After manual price update or scheduled price application:

- recompute `min_price`
- recompute `max_price`
- refresh the affected product row

## 10. Architecture

### 10.1 New code units

- projection entity for `storefront_product_search`
- projection repository
- projection refresh service or projector helper

### 10.2 Existing code updates

- `StorefrontSearchService` reads projection rows
- product command service triggers projection refresh
- inventory service triggers projection refresh
- pricing service triggers projection refresh
- `ProductSearchProjector` becomes a real coordinator instead of a stub

## 11. Error Handling

Projection refresh should fail the surrounding request in this phase.

Reason:

- synchronous updates mean we prefer strong consistency between writes and
  storefront reads
- hidden partial success would make debugging harder at the current project
  stage

If projection refresh fails:

- the transaction should roll back
- the caller sees the normal business or server error response

## 12. Testing Strategy

### 12.1 Projection write tests

- product create writes a projection row
- product update refreshes projection title/category values
- price update refreshes projected prices
- inventory change refreshes projected quantity and stock status

### 12.2 Storefront read tests

- `GET /products` returns results from the projection
- keyword and category filters work against projection data
- non-visible products are excluded from storefront results

### 12.3 Regression tests

- deleting a product removes it from storefront-visible results
- scheduled price application updates projection price values

## 13. Migration and Rollout

Rollout steps:

1. add migration for `storefront_product_search`
2. add projection entity, repository, and refresh service
3. switch storefront search reads to the projection
4. connect product, inventory, and pricing writes to projection refresh
5. add tests and documentation

No data backfill job is required for this phase if tests create their own data,
but a simple rebuild path should be easy to add later.

## 14. Success Criteria

This phase is complete when:

- storefront search reads from the projection table
- product, inventory, and pricing changes refresh projection rows
- storefront results no longer require runtime multi-table aggregation
- automated tests verify projection freshness and storefront visibility rules
