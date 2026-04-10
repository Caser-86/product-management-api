# Changelog

All notable changes to this project will be documented in this file.

## 0.1.0 - 2026-04-10

### Added

- Product CRUD, storefront search, inventory management, and price history APIs
- Header-based admin auth with merchant-scope enforcement
- Inventory ledger persistence and history endpoint
- Manual and scheduled price execution with in-process scheduler
- MySQL-backed storefront search projection for `GET /products`
- Swagger/OpenAPI docs, Docker packaging assets, and backend CI workflow

### Changed

- Storefront search now reads from a dedicated projection instead of assembling
  product, price, and inventory data at request time
- Product, inventory, and pricing writes now refresh storefront search data

### Known Limitations

- Storefront publish/audit workflow is not fully implemented yet
- Auth is request-header based rather than JWT-based
- Scheduled pricing is designed for single-instance deployment
