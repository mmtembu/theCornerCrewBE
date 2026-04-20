# CornerCrew Backend (Spring Boot Starter)

Spring Boot 3 (Java 21) starter for the CornerCrew MVP with JWT auth, Flyway, Postgres/PostGIS, and OpenAPI.

## Quick Start

```bash
# 1) Start Postgres (PostGIS)
docker compose up -d

# 2) Build & run app
./gradlew bootRun
# or
./gradlew build
java -jar build/libs/cornercrew-backend-0.0.1-SNAPSHOT.jar
```

## Environment

Copy `.env.example` to `.env` and adjust if needed. The app reads standard env vars for DB and JWT.

## API

- Swagger UI: `http://localhost:8080/swagger-ui` (dev)
- Health: `GET /actuator/health`

## Auth

- `POST /auth/register` → returns `{accessToken, refreshToken}`
- `POST /auth/login` → returns `{accessToken, refreshToken}`

Use the `Authorization: Bearer <accessToken>` header for protected endpoints.

## Git Hooks

Run `./setup-hooks.sh` to install the pre-push hook (compiles before pushing).

## Notes

- Flyway manages schema versioning (see `src/main/resources/db/migration`).
- PostGIS dialect is configured for future geo features.
- This is a minimal baseline. Next steps: corners, shifts, pools, webhooks.
