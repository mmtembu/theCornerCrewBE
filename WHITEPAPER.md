# CornerCrew — Crowdfunded Traffic Control Platform

## Whitepaper

**Version 2.0 — April 2026**

---

## Abstract

CornerCrew is a platform that addresses traffic congestion at dangerous intersections through community-driven crowdfunding and coordinated traffic controller deployment. The system uses real-time traffic data to automatically detect congested intersections, enables drivers to pool funds for traffic control campaigns, and manages the full lifecycle of controller assignments — from application through shift completion, review, and payout.

Phase 2 extends the platform with proactive capabilities: predictive campaign drafting from historical congestion patterns, commute-aware driver notifications, controller job alerts with inline accept/decline, geographic campaign visualization, and a real-time traffic incident map with fallback to historical data.

This document describes the platform's motivation, architecture, core workflows, technical implementation, and Phase 2 enhancements.

---

## 1. Problem Statement

In many South African cities, particularly in the Gauteng region, traffic congestion at intersections is a persistent daily problem. Contributing factors include broken traffic lights, inadequate road infrastructure, high traffic volumes, and rapid urbanization.

Informal traffic controllers — often community members — already step in to direct traffic at these intersections. However, they operate without:

- **Systematic identification** of which intersections need help most urgently
- **Sustainable funding** to compensate controllers fairly
- **Coordination** to prevent gaps or overlaps in coverage
- **Accountability** through structured performance feedback
- **Data-driven decision making** about where to deploy resources

The result is an ad-hoc system where the most dangerous intersections may go unattended while controllers at less critical locations work without compensation or recognition.

---

## 2. Solution Overview

CornerCrew connects three groups of stakeholders through a unified platform:

| Stakeholder | Role |
|-------------|------|
| **Drivers** | Fund campaigns for intersections on their commute routes and review controller performance |
| **Controllers** | Apply for shifts at funded intersections and receive performance-based payouts |
| **Admins** | Oversee the platform — approve campaigns, manage assignments, and process payouts |

The platform automates congestion detection using real-time traffic APIs, replaces manual intersection identification with data-driven discovery, and introduces a crowdfunding model that aligns incentives: drivers fund the intersections they use, controllers are paid based on community feedback, and admins focus on oversight rather than data entry.

---

## 3. Core Workflows

### 3.1 Automated Congestion Detection

A scheduled background job polls the TomTom Traffic Flow API at configurable intervals (default: 60 seconds). For each intersection within the monitored area, the system:

1. Fetches the current traffic speed and free-flow speed
2. Computes a normalized congestion score: `score = 1.0 - (currentSpeed / freeFlowSpeed)`
3. Persists an immutable congestion snapshot for historical analysis
4. Flags intersections whose score exceeds the configurable threshold (default: 0.7)

Users can also trigger on-demand scans for their current location, enabling the platform to discover congestion anywhere — not just within the pre-configured monitoring area.

### 3.2 Campaign Lifecycle

```
DRAFT → OPEN → FUNDED → CLOSED
                 ↓
             CANCELLED
```

1. **Draft**: Auto-created when an admin confirms a flagged intersection, or manually created by an admin
2. **Open**: Accepting driver contributions toward the funding target
3. **Funded**: Target reached — funds are locked, controller assignments can begin
4. **Closed/Cancelled**: Campaign completed or cancelled

### 3.3 Crowdfunded Contributions

Drivers contribute to campaigns for intersections they care about. The system enforces:

- Contributions only accepted while the campaign is OPEN
- No single contribution can exceed the remaining capacity
- Pessimistic database locking prevents concurrent over-funding
- Automatic transition to FUNDED when the target is reached

### 3.4 Controller Assignment

Once a campaign is funded:

1. Controllers apply to work shifts
2. Admins review and accept qualified applicants
3. Admins assign accepted controllers to specific intersections and dates
4. The system creates shift slots: morning (07:00–09:00) and evening (16:30–18:30) per date
5. Shift conflicts are prevented — one controller per intersection per shift

### 3.5 Review and Payout

After a controller completes their assignment:

1. Drivers submit ratings (1–5) and optional comments
2. The admin triggers a payout if the average rating meets the threshold (default: 3.0)
3. The assignment transitions to PAID and the payout is recorded
4. Payouts are idempotent — an assignment can only be paid once

---

## 4. Technical Architecture

### 4.1 Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21 on Eclipse Temurin (Alpine) |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL 16 (Alpine) |
| ORM | Hibernate 6.5.2 with Spring Data JPA |
| Migrations | Flyway 10.x |
| Authentication | JWT (JJWT 0.12.5) with BCrypt password hashing |
| Caching | Caffeine (24h TTL for geolocation data) |
| API Documentation | SpringDoc OpenAPI 3 (Swagger UI) |
| Testing | JUnit 5, Testcontainers 1.21.4, jqwik 1.9.1 |
| Containerization | Docker with multi-stage Alpine builds |

### 4.2 Module Structure

The application follows a domain-driven modular monolith pattern:

```
com.cornercrew.app/
├── auth/           JWT authentication, registration, login
├── user/           User entity, roles, location preferences
├── campaign/       Campaign lifecycle, funding, contributions, campaign-intersections
├── campaignmap/    Geographic campaign visualization
├── assignment/     Applications, assignments, shifts, reviews, payouts
├── intersection/   Intersection discovery, congestion monitoring, candidates
├── traffic/        TomTom traffic API adapter (flow + incidents)
├── geolocation/    Mapbox geocoding API adapter
├── notification/   In-app notifications, preferences, accept/decline
├── commuteprofile/ Driver commute profile management
├── predictive/     Recurrence pattern detection, campaign drafting, scheduler
├── incident/       Traffic incident service, enrichment, fallback
├── config/         Security, cache, configuration properties
└── common/         Shared exceptions, global error handler
```

### 4.3 External API Integration

**TomTom Traffic Flow API** — provides real-time speed data per road segment. The adapter normalizes TomTom's `currentSpeed / freeFlowSpeed` ratio into a 0.0–1.0 congestion score.

**TomTom Traffic Incidents API** — provides real-time traffic incident data within a geographic bounding box. The adapter parses incident locations, speeds, and delays into a provider-agnostic format. When unavailable, the system falls back to historical CongestionSnapshot data.

**Mapbox Geocoding API** — discovers intersection coordinates within a geographic bounding box using reverse geocoding on a grid of sample points. Results are cached for 24 hours.

All adapters are provider-agnostic interfaces, allowing future substitution with Google Maps, HERE, or other providers without changing domain logic.

### 4.4 Security Model

- Stateless JWT authentication with access tokens (15 min) and refresh tokens (14 days)
- Role-based access control: ADMIN, DRIVER, CONTROLLER
- Method-level authorization via `@PreAuthorize` annotations
- Proper HTTP status codes: 401 for unauthenticated, 403 for unauthorized
- API keys injected via environment variables, never logged or committed

---

## 5. Data Integrity Guarantees

The platform enforces several invariants that are verified through property-based testing:

| Invariant | Description |
|-----------|-------------|
| **Funding Cap** | `campaign.currentAmount <= campaign.targetAmount` at all times |
| **Lock Immutability** | Once FUNDED, a campaign's currentAmount cannot change |
| **No Double Application** | At most one application per controller per campaign |
| **No Shift Conflicts** | At most one active shift slot per intersection per date per shift type |
| **Review Uniqueness** | At most one review per driver per assignment |
| **Payout Gate** | Assignment transitions to PAID only if COMPLETED and avg rating >= threshold |
| **Pay-Once** | A PAID assignment cannot be paid again |
| **Congestion Bounds** | All congestion scores clamped to [0.0, 1.0] |
| **Status Machine** | Intersection transitions follow CANDIDATE → FLAGGED → CONFIRMED/DISMISSED |
| **Snapshot Immutability** | CongestionSnapshot records are append-only, never modified |
| **Notification Append-Only** | Once created, only `readAt` and `dismissedAt` may be updated on a notification |
| **Commute Profile Upsert** | Saving a commute profile replaces any existing profile for the same driver |
| **Notification Eligibility** | COMMUTE_IMPACT sent iff driver has profile AND notifications enabled AND intersection within proximity |
| **Job Notification Eligibility** | JOB_AVAILABLE sent iff controller has jobNotificationsEnabled = true |
| **Pattern Detection Threshold** | Recurrence pattern emitted iff high-congestion count ≥ minOccurrences |
| **Pattern Average Score** | averageCongestionScore computed from only high-congestion snapshots, always in [threshold, 1.0] |
| **Draft Idempotence** | No duplicate campaigns created for the same recurrence pattern and time window |
| **Pattern Determinism** | Same congestion snapshot input always produces identical recurrence patterns |
| **Incident Clamping** | Speed clamped to [0, 200] km/h, delay to [0, 1440] minutes |
| **Proximity Filter** | Campaign map returns only campaigns with intersections within the specified Haversine radius |
| **Funding Percentage** | Computed as `round((currentAmount / targetAmount) * 100, 1)`, always in [0.0, 100.0] |

---

## 6. Testing Strategy

The project employs three testing approaches:

**Unit Tests** — verify individual service and controller behavior using Mockito mocks.

**Integration Tests** — verify end-to-end workflows against a real PostgreSQL database via Testcontainers:
- Full campaign lifecycle (create → fund → assign → review → payout)
- Concurrent contribution safety (10 threads, pessimistic locking)
- Role-based access enforcement (401/403 for all endpoint/role combinations)
- Traffic polling and auto-flagging with mocked external APIs

**Property-Based Tests** (jqwik) — verify universal correctness properties across randomized inputs, providing stronger guarantees than example-based tests alone. Phase 2 adds 22 new correctness properties covering notification eligibility, pattern detection thresholds, campaign drafting idempotence, incident enrichment clamping, and more.

---

## 7. Deployment

### Development

```bash
cp .env.example .env
# Configure API keys and secrets in .env
docker compose up --build -d
```

The Docker Compose setup includes both PostgreSQL and the application. The multi-stage Dockerfile builds the JAR inside Docker, producing a minimal Alpine JRE image (~130 MB).

### Production

The application is stateless and horizontally scalable. Key considerations:

- Set `JWT_SECRET` to a strong, unique value (minimum 32 characters)
- Configure `TRAFFIC_API_KEY` and `GEOLOCATION_API_KEY` for external API access
- Use a managed PostgreSQL instance for durability
- The polling interval and congestion threshold are tunable per deployment

---

## 8. Phase 2 Enhancements (Implemented)

Phase 2 extends the platform from reactive traffic management to proactive, data-driven operations with five new capabilities.

### 8.1 Driver Commute Notifications
Drivers store their commute route — origin coordinates, destination coordinates, and typical departure window — via `PUT /users/me/commute-profile`. When a campaign transitions to OPEN and any of its intersections fall within a configurable proximity radius (default: 2 km) of a driver's commute route, the system creates a COMMUTE_IMPACT notification. The notification includes the intersection label and an estimated delay computed from the latest congestion score: `delayMinutes = round(congestionScore × 30)`.

Proximity is calculated using the Haversine cross-track distance formula, treating the commute as a straight line from origin to destination and checking the perpendicular distance from each campaign intersection to that line.

### 8.2 Controller Job Notifications
When a campaign transitions to OPEN, all controllers with `jobNotificationsEnabled = true` receive a JOB_AVAILABLE notification containing the campaign title, funding window, target amount, intersection labels, and an `actionUrl` pointing to the application endpoint. Controllers can accept (creating a PENDING application) or decline (dismissing the notification) directly from the notification.

### 8.3 Predictive Campaign Drafting
A scheduled job (default: daily at 2 AM, configurable via cron) analyzes historical CongestionSnapshot data to detect recurring congestion patterns. The algorithm:

1. Queries snapshots within the lookback window (default: 4 weeks)
2. Groups by (intersectionId, dayOfWeek, 2-hour time bucket)
3. Counts high-congestion snapshots (score ≥ threshold) per group
4. Emits a RecurrencePattern when the count meets the minimum (default: 3 occurrences)
5. Computes the average congestion score from only the high-congestion snapshots
6. Persists patterns with JSON audit data (snapshot IDs, scores, timestamps)

For each detected pattern, if no active campaign (DRAFT/OPEN/FUNDED) already covers the same intersection and time window, a DRAFT campaign is auto-created with a descriptive title, configurable target amount, and a window starting on the next occurrence of the pattern's day-of-week.

### 8.4 Geographic Campaign Visualization
The `GET /campaigns/map` endpoint returns active campaigns (default: OPEN and FUNDED) with their associated intersection coordinates, enabling frontend map rendering. When latitude, longitude, and radius parameters are provided, only campaigns with at least one intersection within the Haversine radius are returned. Each campaign includes a funding percentage computed as `round((currentAmount / targetAmount) × 100, 1)`. Campaigns with no geo-located intersections are excluded.

### 8.5 Traffic Incident Map
The `GET /traffic/incidents` endpoint returns real-time traffic incidents around a location. The system queries the TomTom Traffic Incidents API for incidents within a bounding box, then enriches each incident:

- **Label assignment**: If a known intersection exists within 200 meters, its label is used; otherwise the road name from the API is used
- **Delay computation**: Raw delay in seconds is converted to minutes (ceiling), clamped to [0, 1440]
- **Speed clamping**: Average speed clamped to [0, 200] km/h
- **Delay formatting**: "Xh Ym" for delays ≥ 60 minutes, "X min" otherwise

When the TomTom API is unavailable, the system falls back to recent CongestionSnapshot data, deriving speed from `freeFlowSpeedKmh × (1 - congestionScore)`. If no coordinates are provided, the user's stored home location is used; if neither exists, a 400 error is returned. Maximum query radius is 50 km (default: 5 km).

### 8.6 Supporting Infrastructure
- **User location preferences**: Any user can store a home location via `PUT /users/me/location` for personalized map views and incident queries
- **Notification preferences**: Drivers toggle commute notifications, controllers toggle job notifications via `PATCH /users/me/notification-preferences`
- **Campaign-intersection join table**: Links campaigns to intersections at creation time (before assignments exist), enabling map and notification features
- **Database migration V4**: Adds commute_profiles, notifications, recurrence_patterns, campaign_intersections tables and user extensions

---

## 9. Impact and Vision

CornerCrew transforms informal traffic control into a structured, funded, and accountable system. By combining real-time traffic data with community crowdfunding, the platform:

- **Directs resources** to the intersections that need them most, based on data rather than guesswork
- **Compensates controllers** fairly through a transparent, review-based payout system
- **Empowers drivers** to directly improve their daily commute by funding traffic control where it matters to them
- **Enables proactive management** through historical pattern analysis and predictive campaign drafting
- **Provides situational awareness** through real-time traffic incident maps and commute impact notifications
- **Keeps stakeholders informed** with targeted notifications — drivers know when campaigns affect their commute, controllers know when jobs are available
- **Scales naturally** — as more drivers contribute and more controllers participate, coverage expands to more intersections

The long-term vision is a self-sustaining ecosystem where congestion data drives campaign creation, community funding enables controller deployment, and performance feedback ensures quality — all coordinated through a single platform.
