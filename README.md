# Appointment Scheduler — Scenario A

**Domain:** Ownership · Unified Service Scheduler 

| Deliverable | Location |
|-------------|----------|
| 1. System Design | [`docs/SYSTEM_DESIGN.md`](docs/SYSTEM_DESIGN.md) |
| 2. Working Code | This repository |
| 3. Video (5–10 min) | Submitted separately |

**Requirements:** (1) book by vehicle / service type / dealership / time · (2) check ServiceBay + qualified Technician for full duration · (3) persist CONFIRMED appointment (customer, vehicle, tech, bay).

**Stack:** Java 17 · Spring Boot 3.3 · JPA · PostgreSQL 16 · Gradle · JUnit 5

---

## Prerequisites

- JDK 17+
- Docker & Docker Compose
- Optional: curl / Postman

## Build

```bash
./gradlew clean bootJar
```

## Run

**Full stack (recommended):**

```bash
docker compose up -d --build
```

API: [http://localhost:8080](http://localhost:8080)

```bash
docker compose down          # stop
docker compose down -v       # stop + wipe DB (re-seed on next start)
```

**Local app + Docker Postgres:**

```bash
docker compose up -d postgres
./gradlew bootRun
```

On first empty database, `DataSeeder` loads demo dealerships, service types, bays, technicians (with skills), customers, and vehicles.

**Get UUIDs for API calls (database client):**

Connect with any PostgreSQL client (DBeaver, pgAdmin, TablePlus, DataGrip, VS Code SQLTools, etc.):

| Setting  | Value        |
|----------|--------------|
| Host     | `localhost`  |
| Port     | `5432`       |
| Database | `scheduler`  |
| User     | `scheduler`  |
| Password | `scheduler`  |

Then run:

```sql
SELECT id, name FROM customers;
SELECT id, vin FROM vehicles;
SELECT id, name FROM service_types;
SELECT id, name FROM dealerships;
```

Copy the UUIDs into the API request body below.

## Test

```bash
./gradlew test
```

Uses in-memory H2 (does not touch Docker Postgres). Report: `build/reports/tests/test/index.html`

| Test | Validates |
|------|-----------|
| `SchedulerApplicationTests` | Context loads |
| `AppointmentServiceTest` | Book when free; reject sequential double-book; concurrent scarce skill → one success |

## API

Shared body:

```json
{
  "customerId": "<uuid>",
  "vehicleId": "<uuid>",
  "serviceTypeId": "<uuid>",
  "dealershipId": "<uuid>",
  "desiredStartTime": "2026-08-15T09:00:00Z"
}
```

**1. Availability** — `POST /api/appointments/availability`

```bash
curl -X POST http://localhost:8080/api/appointments/availability \
  -H "Content-Type: application/json" \
  -d @request.json
```

Returns `available`, `requestedTime`, `serviceType`, and `capacity` (`availableTechnicians`, `availableServiceBays`).

**2. Book** — `POST /api/appointments` → `201` or `409` if no free bay + qualified technician

```bash
curl -X POST http://localhost:8080/api/appointments \
  -H "Content-Type: application/json" \
  -d @request.json
```

Typical flow: call availability, then book when `available` is true. Design details: [`docs/SYSTEM_DESIGN.md`](docs/SYSTEM_DESIGN.md).

---

## AI Collaboration Narrative

This project was developed using GenAI as an engineering assistant rather than an autonomous developer. AI accelerated design exploration, boilerplate generation, and documentation, while all architectural decisions, business rules, and code quality remained my responsibility.

### AI-Assisted Development Strategy

AI was primarily used to:

- Summarize and analyze the assessment requirements.
- Generate initial project structure and boilerplate code.
- Explore alternative architecture and concurrency strategies.
- Draft REST API contracts and documentation.
- Suggest unit test scenarios and refactoring opportunities.

Rather than accepting AI suggestions directly, every proposal was evaluated against the business requirements of Scenario A before being adopted.

Examples of engineering decisions include:

- Choosing a **modular monolith** instead of microservices for the assessment.
- Using **pessimistic locking** to prevent double booking under concurrent requests.
- Keeping only two public APIs (`Availability` and `Create Appointment`) to align with the required acceptance criteria.
- Introducing **Outbox Pattern**, Redis, Read Replicas, and Database Sharding only as future scalability improvements rather than unnecessary complexity in the initial implementation.

---

### Verification and Refinement Process

Every AI-generated suggestion was manually reviewed and validated before becoming part of the solution.

The verification process included:

- Mapping every implementation back to the assessment requirements.
- Reviewing generated business logic and transaction boundaries.
- Validating concurrency behaviour for simultaneous booking requests.
- Running the complete unit test suite after significant changes.
- Manually testing the booking workflow, including:
  - Availability check
  - Successful booking
  - Booking conflicts (`409 Conflict`)
  - Capacity updates after successful appointments

AI recommendations were challenged whenever they conflicted with production-oriented design principles. For example:

- Rejected optimistic locking for the booking workflow because it could lead to failed retries under high contention.
- Rejected introducing Kafka into the synchronous booking transaction, limiting Kafka to future asynchronous processes such as notifications and analytics.
- Simplified the public API surface to match the assessment scope while documenting future production enhancements separately.

---

### Quality Ownership

Final code quality remained my responsibility throughout the project.

Quality assurance included:

- Manual review of all AI-generated code.
- Refactoring for readability and maintainability.
- Unit tests covering the core booking scenarios.
- Verification of pessimistic locking behaviour under concurrent requests.
- Validation that all business rules matched the assessment requirements.

The submitted solution represents the final reviewed implementation rather than raw AI-generated output. AI accelerated development, but all architectural decisions, trade-offs, testing, and production considerations were validated and owned by me.
