# Product Management API

Spring Boot sample service for ecommerce product management.

## Modules

- `product`: product CRUD and admin queries
- `inventory`: inventory balance and reservation flow
- `pricing`: current price, change history, and scheduled price model
- `search`: storefront search API and projection skeleton
- `shared`: API envelope and shared error handling

## Local development

The project targets Java 21.

Run tests from `backend/`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Start the API locally from `backend/`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:DB_URL='jdbc:mysql://localhost:3306/ecommerce'
$env:DB_USERNAME='ecommerce'
$env:DB_PASSWORD='ecommerce'
.\gradlew.bat bootRun --no-daemon
```

## Docker Compose

Start MySQL and the API from the repository root:

```powershell
docker compose up --build
```

The Compose stack exposes:

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- MySQL: `localhost:3306`

Stop the stack:

```powershell
docker compose down
```

## Environment variables

Application defaults come from `application.yml` and can be overridden with:

- `SERVER_PORT` default: `8080`
- `DB_URL` default: `jdbc:mysql://localhost:3306/ecommerce`
- `DB_USERNAME` default: `ecommerce`
- `DB_PASSWORD` default: `ecommerce`
- `APP_AUTH_JWT_SECRET` default: `change-me-for-local-development-only`
- `APP_AUTH_JWT_ISSUER` default: `product-management-api`
- `APP_AUTH_JWT_ACCESS_TOKEN_TTL_MINUTES` default: `60`
- `pricing.schedule.fixed-delay-ms` default: `30000`

## Authentication

Protected endpoints now require a Bearer token in the `Authorization` header.
Use the local login endpoint first:

```powershell
$body = @{
  username = 'platform-admin'
  password = 'platform-secret'
} | ConvertTo-Json

$token = (Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/auth/login' `
  -ContentType 'application/json' `
  -Body $body).data.accessToken
```

Then call protected APIs with:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri 'http://localhost:8080/admin/products?merchantId=2001' `
  -Headers @{ Authorization = "Bearer $token" }
```

Default local accounts come from `application.yml`:

- `platform-admin / platform-secret`
- `merchant-admin / merchant-secret`

Supported roles:

- `PLATFORM_ADMIN`: can operate across merchants
- `MERCHANT_ADMIN`: restricted to its own merchant data

Anonymous access remains available for storefront read endpoints such as
`GET /products`, plus OpenAPI docs and `POST /auth/login`.

In Swagger UI:

1. Open `http://localhost:8080/swagger-ui.html`
2. Call `POST /auth/login` and copy `accessToken`
3. Click `Authorize`
4. Paste the token as `Bearer <accessToken>`

## Scheduling

Due price schedules are applied automatically by the in-process scheduler.
Manual application is still available through the admin API.

## Storefront Search

`GET /products` reads from the `storefront_product_search` projection table.
The projection is refreshed synchronously after product, inventory, and pricing
writes so storefront search no longer assembles product, price, and stock data
at request time. Storefront results only include products that are both
`approved` and `published`.

## Storefront Projection Maintenance

Platform admins can repair storefront projection data through:

- `POST /admin/search/storefront/products/{productId}/refresh`
- `POST /admin/search/storefront/rebuild`

Use single refresh when one product row is missing or stale. Use full rebuild
after manual projection cleanup, backfill work, or suspected refresh drift.

Both endpoints require a `PLATFORM_ADMIN` bearer token.

Full rebuild responses include:

- `processedCount`
- `successCount`
- `failureCount`
- `durationMs`
- `failures`

Each failure item includes:

- `productId`
- `errorCode`
- `message`

## Product Workflow

Products now follow an explicit admin workflow:

1. Merchant creates a draft product
2. Merchant submits it for review
3. Platform admin approves or rejects it
4. Platform admin publishes or unpublishes it

Workflow behavior:

- New products start as `draft` + `pending` + `unpublished`
- Storefront search only returns `approved` + `published` products
- Rejected products can be resubmitted
- Any approved product whose core content changes is forced back to
  `draft` + `pending` + `unpublished` and must be reviewed again
- Workflow actions are recorded in `product_workflow_history`

## API Documentation

After the application starts, interactive API docs are available at:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Login endpoint: `POST /auth/login`

## Documents

- Design spec: `docs/superpowers/specs/2026-04-10-product-management-api-design.md`
- Implementation plan: `docs/superpowers/plans/2026-04-10-product-management-api-implementation-plan.md`
- Delivery packaging spec: `docs/superpowers/specs/2026-04-10-delivery-packaging-design.md`
- Delivery packaging plan: `docs/superpowers/plans/2026-04-10-delivery-packaging-implementation-plan.md`
- Auth/ledger/scheduling spec: `docs/superpowers/specs/2026-04-10-auth-ledger-scheduling-design.md`
- Auth/ledger/scheduling plan: `docs/superpowers/plans/2026-04-10-auth-ledger-scheduling-implementation-plan.md`
- Product publish/review spec: `docs/superpowers/specs/2026-04-11-product-publish-review-workflow-design.md`
- Product publish/review plan: `docs/superpowers/plans/2026-04-11-product-publish-review-workflow-implementation-plan.md`
- JWT auth upgrade spec: `docs/superpowers/specs/2026-04-11-jwt-auth-upgrade-design.md`
- JWT auth upgrade plan: `docs/superpowers/plans/2026-04-11-jwt-auth-upgrade-implementation-plan.md`
