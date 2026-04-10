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

## Documents

- Design spec: `docs/superpowers/specs/2026-04-10-product-management-api-design.md`
- Implementation plan: `docs/superpowers/plans/2026-04-10-product-management-api-implementation-plan.md`
