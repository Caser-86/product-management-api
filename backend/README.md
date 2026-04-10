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

## API Documentation

After the application starts, interactive API docs are available at:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Documents

- Design spec: `docs/superpowers/specs/2026-04-10-product-management-api-design.md`
- Implementation plan: `docs/superpowers/plans/2026-04-10-product-management-api-implementation-plan.md`
- Delivery packaging spec: `docs/superpowers/specs/2026-04-10-delivery-packaging-design.md`
- Delivery packaging plan: `docs/superpowers/plans/2026-04-10-delivery-packaging-implementation-plan.md`
