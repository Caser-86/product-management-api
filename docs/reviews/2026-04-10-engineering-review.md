# Engineering Review - 2026-04-10

## Current Assessment

The project now has:

- product CRUD and admin APIs
- inventory reservation, confirmation, adjustments, and ledger history
- manual and scheduled pricing
- header-based admin auth and merchant scope enforcement
- a storefront search projection backed by MySQL
- CI for backend tests and local Docker packaging assets

The recent storefront search projection work closes the biggest read-path
structural issue for `GET /products`.

## Review Outcome

No new release-blocking correctness issues remain after fixing multi-SKU
projection aggregation during this review cycle.

The next improvements are mostly about product maturity, operational safety, and
feature completeness rather than immediate breakage.

## Highest-Value Next Items

### 1. Add a real storefront visibility workflow

Current storefront search still effectively treats non-deleted products as
visible. The data model already has `status`, `publish_status`, and
`audit_status`, but the project does not yet have a full publish/audit workflow
that search can rely on.

Why it matters:

- storefront visibility rules stay implicit
- draft products can appear in storefront search
- the current projection stores publish/audit state but does not use it

Recommended next step:

- add admin publish/unpublish endpoints
- define approved/published storefront-visible rules
- update storefront projection queries accordingly

### 2. Replace header-only auth with a real identity layer

The current `X-User-Id` / `X-Role` / `X-Merchant-Id` model was the right
intermediate hardening step, but it is still a transport-level shortcut rather
than a real authentication system.

Why it matters:

- no token issuance or signature validation
- no user persistence or role administration
- no clean bridge to clients beyond testing and internal use

Recommended next step:

- move to JWT-based auth
- persist users, merchants, and role assignments
- keep merchant scope enforcement in services

### 3. Add projection rebuild and recovery tooling

The storefront search projection refreshes synchronously and stays consistent
during normal writes, but there is no rebuild path for recovery or backfill.

Why it matters:

- hard to recover from projection corruption
- difficult to backfill after future schema changes
- no operator path for rebuilding search data

Recommended next step:

- add an admin-only rebuild command or job
- support full rebuild and single-product refresh
- keep it idempotent and observable

### 4. Harden scheduling for multi-instance deployment

Price scheduling currently runs in-process with `@Scheduled`, which is fine for
single-instance use but not safe enough for multi-node production deployment.

Why it matters:

- duplicate runners may scan the same pending schedules
- no distributed lock or scheduler coordination
- scaling the app horizontally will need scheduler rules

Recommended next step:

- add locking or leader-election semantics
- or move schedule execution to a dedicated worker path

### 5. Expand CI and delivery validation

Current CI runs backend tests, which is valuable, but it does not yet validate
container packaging or broader release quality gates.

Why it matters:

- Docker packaging can drift without automated checks
- no changelog/release gate in CI
- no smoke verification for startup paths

Recommended next step:

- add `bootJar` validation to CI
- add Docker build validation when the environment allows it
- consider release tagging or a lightweight release workflow

## Medium-Priority Follow-Ups

- Add search result sorting and price-range filters.
- Add inventory release/refund flows to complete the stock lifecycle.
- Add admin APIs for managing publish/audit state explicitly.
- Introduce a consistent domain event or outbox path for future async projection.
- Separate dev and prod configuration more clearly.

## Summary

The project is in a solid “working MVP with real engineering structure” state.
The next batch should focus less on raw CRUD breadth and more on:

1. storefront visibility correctness
2. real authentication
3. operational recovery and deployment safety
