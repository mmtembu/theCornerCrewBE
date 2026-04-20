# CornerCrew

**Crowdfunded Traffic Control for Congested Intersections**

[![CI](https://github.com/mmtembu/theCornerCrewBE/actions/workflows/ci.yml/badge.svg)](https://github.com/mmtembu/theCornerCrewBE/actions/workflows/ci.yml)

---

## Table of Contents

- [The Problem](#the-problem)
- [The Solution](#the-solution)
- [How It Works](#how-it-works)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Data Model](#data-model)
- [Business Rules](#business-rules)
- [Security](#security)
- [External Integrations](#external-integrations)
- [Testing](#testing)
- [Deployment](#deployment)
- [Error Handling](#error-handling)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

---

## The Problem

In many South African cities, particularly Johannesburg, traffic congestion at intersections is a daily reality. Broken traffic lights, poor road infrastructure, and high traffic volumes create dangerous bottlenecks. Informal traffic controllers — often community members — step in to direct traffic at these intersections, but they operate without coordination, compensation, or accountability.

There is no system to:
- **Identify** which intersections are most congested and need help
- **Fund** traffic control efforts at those intersections
- **Coordinate** controller assignments and shifts
- **Compensate** controllers fairly based on performance
- **Hold controllers accountable** through community feedback

---

## The Solution

CornerCrew is a platform that connects three groups of people:

| Role | What They Do |
|------|-------------|
| **Drivers (Contributors)** | Fund campaigns for intersections they use daily and review controller performance |
| **Controllers** | Apply for and work shifts directing traffic at funded intersections |
| **Admins** | Manage campaigns, approve assignments, and process payouts |

The platform uses real-time traffic data to automatically detect congested intersections, crowdfunding to finance traffic control, and a review system to ensure quality.

---

## How It Works

### 1. Congestion Detection
The system continuously monitors traffic conditions using the TomTom Traffic Flow API. When an intersection's congestion score exceeds a configurable threshold (default: 0.7), it is automatically flagged as a candidate for traffic control.

### 2. Campaign Creation
Admins review flagged intersections and confirm them. Confirmation auto-creates a crowdfunding campaign with a target amount and funding window. Drivers who use that intersection can contribute funds.

### 3. Funding
Drivers contribute to campaigns for intersections they care about. Contributions are tracked with configurable periods (daily, weekly, monthly). When a campaign reaches its target amount, it is automatically locked as fully funded.

### 4. Controller Assignment
Once a campaign is funded, traffic controllers apply to work shifts at the intersection. Admins review applications, accept qualified controllers, and assign them to specific shift slots (morning: 07:00–09:00, evening: 16:30–18:30).

### 5. Reviews & Payouts
After a controller completes their assignment, drivers review their performance (1–5 rating). If the controller's average rating meets the minimum threshold (default: 3.0), the admin processes their payout from the campaign funds.

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐     ┌──────────────┐
│  Congestion  │────▶│   Campaign   │────▶│  Assignment   │────▶│   Review &   │
│  Detected    │     │   Funded     │     │  Completed    │     │   Payout     │
└─────────────┘     └──────────────┘     └───────────────┘     └──────────────┘
   TomTom API         Drivers fund        Controllers work      Drivers review
   auto-flags         the campaign        assigned shifts       Admin pays out
```

---

## Architecture

CornerCrew is a monolithic Spring Boot application following a domain-driven modular structure. Each domain module (campaigns, assignments, intersections, etc.) contains its own entities, repositories, services, and controllers.

```
┌─────────────────────────────────────────────────────┐
│                   REST API Layer                     │
│  Auth │ Campaigns │ Funding │ Assignments │ Traffic  │
├─────────────────────────────────────────────────────┤
│                  Service Layer                       │
│  JWT Auth │ Campaign Mgmt │ Payout │ Traffic Monitor │
├─────────────────────────────────────────────────────┤
│                  Data Layer                          │
│  Spring Data JPA │ Hibernate Spatial │ Flyway        │
├─────────────────────────────────────────────────────┤
│              PostgreSQL + PostGIS                    │
└─────────────────────────────────────────────────────┘
         │                              │
    ┌────┴────┐                   ┌─────┴─────┐
    │ TomTom  │                   │  Mapbox   │
    │ Traffic │                   │ Geocoding │
    └─────────┘                   └───────────┘
```

Key architectural decisions:
- **Stateless JWT authentication** — no server-side sessions
- **Pessimistic locking** on campaign funding to prevent race conditions
- **Caffeine caching** for geolocation data (24h TTL, 100 entries)
- **Scheduled polling** for traffic congestion monitoring (configurable interval)
- **Flyway migrations** for schema versioning
- **PostGIS** for geographic/spatial data support

---

## Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.3.4 |
| Language Support | Kotlin | 1.9.21 |
| Build | Gradle (Kotlin DSL) | 8.13 |
| Database | PostgreSQL + PostGIS | 16 |
| ORM | Hibernate + Hibernate Spatial | 6.5.2 |
| Migrations | Flyway | — |
| Auth | JJWT | 0.12.5 |
| Caching | Caffeine | 3.1.8 |
| API Docs | SpringDoc OpenAPI | 2.6.0 |
| Monitoring | Spring Actuator | — |
| Testing | JUnit 5, Testcontainers, jqwik | 1.21.4 / 1.9.1 |

---

## Getting Started

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Git

### Setup

```bash
# Clone the repository
git clone git@github.com:mmtembu/theCornerCrewBE.git
cd theCornerCrewBE

# Install Git hooks (pre-push compilation check)
./setup-hooks.sh

# Copy environment template and configure
cp .env.example .env
# Edit .env with your database credentials and API keys

# Start PostgreSQL with PostGIS
docker compose up -d db

# Run the application
./gradlew bootRun
```

The API will be available at `http://localhost:8080`.

### Verify

- Health check: `GET http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui`

---

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `cornercrew` |
| `DB_USER` | Database user | `cornercrew` |
| `DB_PASSWORD` | Database password | `cornercrew` |
| `JWT_SECRET` | HMAC-SHA signing key for JWT tokens | — (required) |
| `TRAFFIC_API_KEY` | TomTom Traffic Flow API key | — (required) |
| `GEOLOCATION_API_KEY` | Mapbox Geocoding API key | — (required) |
| `PORT` | Application port | `8080` |

### Application Properties

| Property | Description | Default |
|----------|-------------|---------|
| `security.jwt.access-ttl-ms` | Access token lifetime | `900000` (15 min) |
| `security.jwt.refresh-ttl-ms` | Refresh token lifetime | `1209600000` (14 days) |
| `app.traffic.polling-interval-ms` | Traffic polling frequency | `60000` (60s) |
| `app.traffic.congestion-threshold` | Score to auto-flag intersections | `0.7` |
| `app.geolocation.intersection-cache-ttl-hours` | Geolocation cache duration | `24` |
| `app.payout.rating-threshold` | Minimum avg rating for payout | `3.0` |

---

## API Reference

All protected endpoints require the `Authorization: Bearer <accessToken>` header.

### Authentication

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/auth/register` | Public | Register a new user |
| `POST` | `/auth/login` | Public | Login and receive tokens |

**Register Request:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "name": "John Doe",
  "role": "DRIVER"
}
```

**Token Response:**
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi..."
}
```

### Campaigns

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/campaigns` | ADMIN | Create a new campaign |
| `GET` | `/campaigns` | Any | List campaigns (paginated, filterable by status) |
| `GET` | `/campaigns/{id}` | Any | Get campaign details |
| `POST` | `/campaigns/{id}/approve` | ADMIN | Approve a draft campaign (DRAFT → OPEN) |

### Funding

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/campaigns/{id}/contributions` | DRIVER | Contribute to a campaign |
| `GET` | `/campaigns/{id}/contributions/summary` | Any | Get funding summary (current total, remaining capacity) |

### Applications

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/campaigns/{id}/applications` | CONTROLLER | Apply to work a campaign |
| `GET` | `/campaigns/{id}/applications` | ADMIN | List applications for a campaign |
| `PUT` | `/campaigns/{id}/applications/{appId}/status` | ADMIN | Accept or reject an application |

### Assignments

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/campaigns/{id}/assignments` | ADMIN | Assign a controller to an intersection |
| `GET` | `/campaigns/{id}/assignments` | Any | List assignments for a campaign |
| `GET` | `/assignments/{id}` | Any | Get assignment details |

### Reviews

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/assignments/{id}/reviews` | DRIVER | Submit a performance review |
| `GET` | `/assignments/{id}/reviews/summary` | Any | Get review summary (avg rating, count) |

### Payouts

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/assignments/{id}/payout` | ADMIN | Process payout for a completed assignment |

### Intersections

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/intersections/candidates` | Any | List intersection candidates (paginated, filterable by status) |
| `POST` | `/intersections/candidates/{id}/confirm` | ADMIN | Confirm a flagged intersection |
| `POST` | `/intersections/candidates/{id}/dismiss` | ADMIN | Dismiss a flagged intersection |
| `POST` | `/intersections/nearby/scan` | Any | Scan for nearby intersections by coordinates |

### Infrastructure

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/info` | Application info |
| `GET` | `/actuator/metrics` | Application metrics |
| `GET` | `/swagger-ui` | Interactive API documentation |

---

## Data Model

```
┌──────────┐       ┌──────────────┐       ┌───────────────┐
│  users   │──────▶│  campaigns   │──────▶│ contributions │
│          │       │              │       │               │
│ id       │       │ id           │       │ id            │
│ email    │       │ title        │       │ campaign_id   │
│ password │       │ description  │       │ driver_id     │
│ name     │       │ target_amount│       │ amount        │
│ role     │       │ current_amt  │       │ period        │
└──────────┘       │ status       │       └───────────────┘
     │             │ window_start │
     │             │ window_end   │
     │             └──────┬───────┘
     │                    │
     │    ┌───────────────┼───────────────┐
     │    │               │               │
     ▼    ▼               ▼               ▼
┌─────────────┐   ┌─────────────┐   ┌──────────────┐
│ controller  │   │ assignments │   │ intersections│
│ applications│   │             │   │              │
│             │   │ id          │   │ id           │
│ id          │   │ campaign_id │   │ label        │
│ campaign_id │   │ controller  │   │ latitude     │
│ controller  │   │ intersection│   │ longitude    │
│ status      │   │ status      │   │ type         │
│ note        │   │ agreed_pay  │   │ status       │
└─────────────┘   └──────┬──────┘   │ congestion   │
                         │          └──────┬───────┘
                    ┌────┴────┐            │
                    │         │            ▼
              ┌─────┴───┐ ┌──┴──────┐ ┌───────────┐
              │ shift   │ │ reviews │ │ congestion│
              │ slots   │ │         │ │ snapshots │
              │         │ │ id      │ │           │
              │ id      │ │ assign  │ │ id        │
              │ assign  │ │ driver  │ │ intersect │
              │ date    │ │ rating  │ │ score     │
              │ shift   │ │ comment │ │ provider  │
              │ times   │ └─────────┘ │ measured  │
              └─────────┘             └───────────┘
```

### Roles

| Role | Description |
|------|-------------|
| `ADMIN` | Platform administrator — manages campaigns, assignments, payouts, and intersections |
| `DRIVER` | Road user — contributes funds to campaigns and reviews controller performance |
| `CONTROLLER` | Traffic controller — applies for and works shifts at intersections |

### Campaign Statuses

| Status | Description |
|--------|-------------|
| `DRAFT` | Created but not yet open for funding |
| `OPEN` | Accepting contributions from drivers |
| `FUNDED` | Target amount reached, locked for assignments |
| `CLOSED` | Campaign completed |
| `CANCELLED` | Campaign cancelled |

### Intersection Statuses

| Status | Description |
|--------|-------------|
| `CANDIDATE` | Newly discovered intersection |
| `FLAGGED` | Congestion score exceeded threshold |
| `CONFIRMED` | Admin confirmed, campaign auto-created |
| `DISMISSED` | Admin dismissed, no action needed |

### Assignment Statuses

| Status | Description |
|--------|-------------|
| `ASSIGNED` | Controller assigned to intersection |
| `ACTIVE` | Controller currently working shifts |
| `COMPLETED` | All shifts completed, awaiting review |
| `PAID` | Payout processed |

---

## Business Rules

### Funding
- Only drivers can contribute to campaigns
- Campaigns must be in `OPEN` status to accept contributions
- A single contribution cannot exceed the remaining capacity (`targetAmount - currentAmount`)
- When a campaign reaches its target, it automatically transitions to `FUNDED` and is locked
- Pessimistic database locking prevents race conditions on concurrent contributions

### Assignments
- Only funded campaigns can have controller assignments
- Controllers must have an accepted application before being assigned
- Each intersection can only have one controller per shift slot (date + shift type)
- Two shift slots are created per assigned date: morning (07:00–09:00) and evening (16:30–18:30)

### Payouts
- Assignments must be in `COMPLETED` status
- At least one review must exist
- Average rating must meet the minimum threshold (default: 3.0)
- Payouts are idempotent — an assignment can only be paid once
- Below-threshold ratings block payout

### Traffic Monitoring
- Polling runs every 60 seconds (configurable)
- Intersections within the configured bounding box are resolved via Mapbox
- Congestion scores are fetched from TomTom and clamped to [0.0, 1.0]
- Scores above the threshold (default: 0.7) auto-flag intersections
- Flagging is idempotent — already flagged/confirmed/dismissed intersections are skipped
- Confirming a flagged intersection auto-creates a draft campaign (R5,000 target, 30-day window)

---

## Security

### Authentication Flow
1. User registers or logs in via `/auth/register` or `/auth/login`
2. Server returns an access token (15 min TTL) and refresh token (14 day TTL)
3. Client includes `Authorization: Bearer <accessToken>` on all subsequent requests
4. JWT filter validates the token, extracts the user, and sets the security context

### Authorization
- Method-level security via `@PreAuthorize` annotations
- Role-based access control (ADMIN, DRIVER, CONTROLLER)
- Public endpoints: auth, health check, API docs, Swagger UI

### Password Security
- Passwords are hashed with BCrypt before storage
- Raw passwords are never stored or logged

---

## External Integrations

### TomTom Traffic Flow API
Used for real-time congestion monitoring. The system polls TomTom at a configurable interval to fetch congestion scores for intersections within the monitored area.

- **Documentation**: https://developer.tomtom.com/traffic-api
- **Configuration**: Set `TRAFFIC_API_KEY` in your `.env` file

### Mapbox Geocoding API
Used to resolve intersection coordinates within a geographic bounding box. Results are cached for 24 hours to minimize API calls.

- **Documentation**: https://docs.mapbox.com/api/search/geocoding/
- **Configuration**: Set `GEOLOCATION_API_KEY` in your `.env` file

---

## Testing

The project has 37 test files covering unit tests, integration tests, and property-based tests.

### Running Tests

```bash
# Run all tests
./gradlew test

# Run with test report
./gradlew test --info
# Report at: build/reports/tests/test/index.html
```

### Test Categories

**Unit Tests** — Service and controller tests using Mockito for mocking dependencies.

**Integration Tests** — Full Spring context tests using Testcontainers with a real PostgreSQL database:
- `CampaignLifecycleIntegrationTest` — End-to-end campaign workflow
- `ConcurrentContributionsIntegrationTest` — Race condition testing on funding
- `SecurityRbacIntegrationTest` — Role-based access enforcement
- `TrafficPollingIntegrationTest` — Traffic monitoring with real database

**Property-Based Tests** (jqwik) — Invariant verification across randomized inputs:
- Funding cap can never be exceeded
- Campaign locks are immutable once set
- No duplicate applications per controller per campaign
- No shift conflicts at the same intersection
- Payouts are idempotent (pay-once guarantee)
- Payout blocked when rating below threshold
- Congestion scores always within [0.0, 1.0]
- Intersection status transitions follow the state machine
- Review uniqueness per driver per assignment
- Geolocation cache hits on repeated queries

---

## Deployment

### Docker Compose (Development)

```bash
# Start everything (database + application)
docker compose up -d

# Start only the database
docker compose up -d db

# View logs
docker compose logs -f app

# Stop everything
docker compose down
```

### Docker (Production)

The multi-stage Dockerfile produces a minimal JRE-based image:

```bash
# Build the image
docker build -t cornercrew-backend .

# Run the container
docker run -p 8080:8080 \
  -e DB_HOST=your-db-host \
  -e DB_PORT=5432 \
  -e DB_NAME=cornercrew \
  -e DB_USER=cornercrew \
  -e DB_PASSWORD=your-password \
  -e JWT_SECRET=your-secret-key \
  -e TRAFFIC_API_KEY=your-tomtom-key \
  -e GEOLOCATION_API_KEY=your-mapbox-key \
  cornercrew-backend
```

### CI/CD

GitHub Actions runs on every push and pull request to `main`:
1. Sets up JDK 21 and a PostGIS service container
2. Builds the project
3. Runs the full test suite
4. Uploads test results as artifacts

---

## Error Handling

All errors return a consistent JSON structure:

```json
{
  "errorCode": "CAMPAIGN_NOT_OPEN",
  "message": "Campaign is not open for contributions",
  "details": {}
}
```

| Error Code | HTTP Status | Trigger |
|------------|-------------|---------|
| `CAMPAIGN_NOT_OPEN` | 409 | Contributing to a non-open campaign |
| `CONTRIBUTION_EXCEEDS_CAP` | 409 | Contribution exceeds remaining capacity |
| `DUPLICATE_APPLICATION` | 409 | Controller applying twice to same campaign |
| `DUPLICATE_REVIEW` | 409 | Driver reviewing same assignment twice |
| `SHIFT_CONFLICT` | 409 | Overlapping shift assignment |
| `ASSIGNMENT_ALREADY_PAID` | 409 | Processing payout on already-paid assignment |
| `INVALID_STATUS_TRANSITION` | 409 | Invalid state machine transition |
| `RATING_BELOW_THRESHOLD` | 422 | Average rating below payout threshold |
| `NO_REVIEWS` | 422 | Processing payout with no reviews |
| `TRAFFIC_API_UNAVAILABLE` | 503 | TomTom API unreachable |
| `GEOLOCATION_API_UNAVAILABLE` | 503 | Mapbox API unreachable |
| `VALIDATION_ERROR` | 400 | Request body validation failure |
| `AUTHENTICATION_FAILED` | 401 | Invalid or expired token |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## Project Structure

```
cornercrew-backend/
├── .github/workflows/ci.yml          # GitHub Actions CI pipeline
├── hooks/pre-push                     # Pre-push compilation check
├── setup-hooks.sh                     # Git hooks installer
├── Dockerfile                         # Multi-stage Docker build
├── docker-compose.yml                 # PostgreSQL + app services
├── build.gradle.kts                   # Gradle build configuration
├── .env.example                       # Environment variable template
│
└── src/
    ├── main/
    │   ├── java/com/cornercrew/app/
    │   │   ├── CornerCrewApplication.java
    │   │   ├── auth/                  # JWT authentication & registration
    │   │   ├── user/                  # User entity, roles, UserDetailsService
    │   │   ├── campaign/              # Campaigns, funding, contributions
    │   │   ├── assignment/            # Applications, assignments, shifts, reviews, payouts
    │   │   ├── intersection/          # Intersections, congestion monitoring, candidates
    │   │   ├── traffic/               # TomTom traffic API adapter
    │   │   ├── geolocation/           # Mapbox geocoding API adapter
    │   │   ├── config/                # Security, cache, properties
    │   │   └── common/                # Exceptions, global error handler
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/          # Flyway SQL migrations
    │
    └── test/java/com/cornercrew/app/  # Unit, integration & property tests
```

---

## Contributing

1. Clone the repo and run `./setup-hooks.sh` to install Git hooks
2. Copy `.env.example` to `.env` and fill in your API keys
3. Start the database with `docker compose up -d db`
4. Run `./gradlew test` to verify everything passes
5. Create a feature branch and submit a pull request

The pre-push hook will compile your code before allowing a push. CI will run the full test suite on your pull request.

---

## License

This project is proprietary. All rights reserved.
