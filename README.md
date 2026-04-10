# Product Management API

Spring Boot based e-commerce product management API.

The backend implementation lives in [`backend/`](D:\Program Files\product-management-api\backend) and includes:

- admin product create, detail, list, update, and soft delete
- storefront product search with keyword and category filtering
- inventory reservation, confirmation, adjustment, and snapshots
- price update history and scheduled price application
- Swagger/OpenAPI documentation
- Dockerfile and Docker Compose assets for local packaging

## Documents

- `docs/superpowers/specs/2026-04-10-product-management-api-design.md`
- `docs/superpowers/plans/2026-04-10-product-management-api-implementation-plan.md`
- `docs/superpowers/specs/2026-04-10-delivery-packaging-design.md`
- `docs/superpowers/plans/2026-04-10-delivery-packaging-implementation-plan.md`

## Quick Start

Run tests from [`backend/`](D:\Program Files\product-management-api\backend):

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

See [`backend/README.md`](D:\Program Files\product-management-api\backend\README.md) for:

- local startup commands
- Docker Compose startup
- Swagger UI and OpenAPI URLs
- environment variable configuration
