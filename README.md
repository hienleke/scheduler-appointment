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

DB defaults: `localhost:5432` / db·user·password = `scheduler`.

On first empty database, `DataSeeder` loads demo dealerships, service types, bays, technicians (with skills), customers, and vehicles.

**Get UUIDs for API calls:**

```bash
docker exec -it $(docker ps -qf "ancestor=postgres:16-alpine") \
  psql -U scheduler -d scheduler -c "SELECT id, name FROM customers;"
docker exec -it $(docker ps -qf "ancestor=postgres:16-alpine") \
  psql -U scheduler -d scheduler -c "SELECT id, vin FROM vehicles;"
docker exec -it $(docker ps -qf "ancestor=postgres:16-alpine") \
  psql -U scheduler -d scheduler -c "SELECT id, name FROM service_types;"
docker exec -it $(docker ps -qf "ancestor=postgres:16-alpine") \
  psql -U scheduler -d scheduler -c "SELECT id, name FROM dealerships;"
```

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

How AI was used on this submission, how output was verified, and how quality was owned.

### High-level strategy for guiding the AI

1. Treated Scenario A’s three requirements as the acceptance criteria—not the AI’s default stack choices.
2. Kept a thin API surface (availability + book); removed catalogue/list/health extras after review.
3. Used AI for scaffolding and iteration; humans decided product rules (skills table, capacity response, lock-on-book).
4. Separated demo correctness from later hardening (e.g. race discussion led to pessimistic locks on book only).

### Process for verifying and refining AI output

1. Mapped each change back to the brief (e.g. “qualified” → `technician_skills`).
2. Ran `./gradlew test` after material changes (including concurrent booking expectations).
3. Manually checked availability → book → capacity drop / 409 on conflict.
4. Challenged AI designs (lock-all vs lock-pair, pool limits, burst behaviour) instead of accepting the first draft.
5. Refined the availability contract to a capacity payload for clearer demos.

### Ensuring final quality

- Owned final behaviour after local build/test (locks on book, capacity on check, 409 on failed book).
- Core logic covered in `AppointmentServiceTest` (happy path, sequential double-book, concurrent scarce skill).
- This README is enough for a grader to build, run, and test without the chat history.
- Limits are explicit: cancel status exists but no cancel API; extreme burst control is out of scope.
