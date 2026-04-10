# Delivery Packaging Design

## Goal

Add practical delivery assets so the existing product management API can be explored and started quickly by another developer.

This delivery scope covers:

- OpenAPI/Swagger UI for the current HTTP API
- containerized application packaging
- local `app + mysql` orchestration with Docker Compose
- README instructions for local and container-based startup

This scope does not include:

- production-grade orchestration such as Kubernetes
- CI/CD pipelines
- authentication hardening
- observability stacks beyond basic startup and health verification

## Approach

### API Documentation

Integrate Springdoc with Spring Boot so the application exposes generated OpenAPI JSON and Swagger UI from the running service.

Use the generated docs approach instead of a hand-written OpenAPI file because the codebase is still changing quickly and the generated contract will stay closer to the controllers already implemented.

### Container Packaging

Add a single `backend/Dockerfile` that builds the Spring Boot jar with Gradle and runs it in a lightweight JRE image.

Use a multi-stage build so the final image is smaller and does not require Gradle or source code at runtime.

### Local Orchestration

Add a repository-root `docker-compose.yml` with:

- `mysql`
- `product-management-api`

The application container will read database settings from environment variables and wait on the MySQL service definition through Compose dependency ordering.

### Documentation

Expand `backend/README.md` so it becomes an execution guide instead of a stub.

The README should include:

- required Java version
- local test command
- local app startup command
- Docker Compose startup command
- Swagger UI URL
- main environment variables

## Files

- Update: `backend/build.gradle.kts`
- Update: `backend/src/main/resources/application.yml`
- Add: `backend/Dockerfile`
- Add: `docker-compose.yml`
- Update: `backend/README.md`

## Verification

Minimum verification for this scope:

- `.\gradlew.bat clean test --no-daemon`
- application starts locally with the documented command
- Swagger endpoint is exposed by configuration
- Docker assets are syntactically valid and consistent with the documented environment variables

## Risks And Constraints

- The current project already uses MySQL-oriented Flyway migrations, so the container path should target MySQL rather than H2.
- Docker Compose should favor developer convenience over production hardening.
- Generated OpenAPI will only be as descriptive as the controller and DTO annotations we provide in this pass.
