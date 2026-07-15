# System Design Document

**Product:** Appointment Scheduler (Scenario A — Unified Service Scheduler)  
**Domain:** Ownership · **Stack:** Spring Boot 3, JPA, PostgreSQL, Gradle  
**Deliverable 1** — Working code & AI narrative: [README.md](../README.md)

---

## 1. Requirements → design

| # | Brief requirement | Design response |
|---|-------------------|-----------------|
| 1 | Book by vehicle, service type, dealership, desired time | `POST /api/appointments` |
| 2 | Check ServiceBay + **qualified** Technician for full duration | `POST /api/appointments/availability` + allocate on book |
| 3 | Persist confirmed record (customer, vehicle, tech, bay) | `Appointment` with status `CONFIRMED` |

Rules:

- `endTime = desiredStartTime + serviceType.durationMinutes`
- Qualified tech = row in `technician_skills`
- Only `CONFIRMED` appointments block capacity (overlap)

**In scope:** 2 APIs, domain model, pessimistic lock on book, seed, tests, Docker.  
**Out of scope:** UI, auth, cancel/list APIs, queue / rate-limit for extreme load.

---

## 2. Architecture

```mermaid
flowchart LR
  Client --> CTL[Controller]
  CTL --> Svc[AppointmentService / AvailabilityService]
  Svc --> DB[(PostgreSQL)]
```

| Layer | Role |
|-------|------|
| Controller | HTTP, `@Valid`, errors via `GlobalExceptionHandler` |
| Services | Validate, capacity check, lock + allocate, persist |
| JPA / PostgreSQL | Entities, overlap queries, `SELECT … FOR UPDATE` on book |

**Deploy:** `docker compose` → `postgres` + `api` (:8080).

---

## 3. Database diagram

![ERD — appointments, resources, skills](scheduler%20-%20scheduler%20-%20public.png)

**Notes**

- `appointments` is the hub (customer, vehicle, dealership, bay, technician, service type, time, status).
- `technician_skills` implements “qualified technician” (Req 2).
- `CONFIRMED` consumes bay/tech; `CANCELLED` does not (soft cancel ready, API not exposed).

---

## 4. API

Base: `http://localhost:8080`

**Request (both endpoints):**

```json
{
  "customerId": "<uuid>",
  "vehicleId": "<uuid>",
  "serviceTypeId": "<uuid>",
  "dealershipId": "<uuid>",
  "desiredStartTime": "2026-08-15T09:00:00Z"
}
```

| Endpoint | Result |
|----------|--------|
| `POST /api/appointments/availability` | `200` + `{ available, requestedTime, serviceType, capacity }` |
| `POST /api/appointments` | `201` confirmed appointment, or `409` if no free bay+tech |

`available` requires both free technicians and free bays &gt; 0. Max concurrent jobs ≈ `min(techs, bays)`.

Errors: `400` validation / ownership / past time · `404` unknown id · `409` slot unavailable.

---

## 5. Core flows

### Availability (no lock)

Validate → compute `endTime` → count free active bays and qualified techs without overlap → return capacity.

### Book (pessimistic lock)

```mermaid
sequenceDiagram
  participant C as Client
  participant S as AppointmentService
  participant DB as PostgreSQL

  C->>S: POST /appointments
  Note over S: @Transactional
  S->>S: validate + endTime
  S->>DB: FOR UPDATE bays then technicians (ORDER BY id)
  S->>DB: first free bay + qualified tech
  alt found
    S->>DB: INSERT CONFIRMED
    S-->>C: 201
  else none
    S-->>C: 409
  end
```

**Overlap:** `existing.start < new.end AND existing.end > new.start` (per bay and per technician).

Locks held until commit; stable `id` order avoids deadlock. Availability stays unlocked (hint only).

---

## 6. Concurrency choice

| Option | Verdict |
|--------|---------|
| Check-then-act only | Unsafe under parallel books |
| **Pessimistic `FOR UPDATE` on book** | **Chosen** — prevents double-book |
| Lock only one pair / DB exclusion / queue | Possible later; out of assessment scope |

Example: 3 bays + 2 oil-qualified techs → at most **2** Oil Change at the same time; third book → 409.

---

## 7. Technology & delivery

| Area | Choice |
|------|--------|
| Java 17 / Spring Boot 3.3 / JPA | Fast, clear REST + persistence |
| PostgreSQL 16 | Durable + `FOR UPDATE` |
| Gradle + Docker Compose | Reproducible demo |
| JUnit + H2 tests | Book / double-book / concurrent scarce skill |

Seed on first empty DB (`DataSeeder`). Tests do not use the Docker volume.

---

## 8. Limitations

- Locking all candidate bays/techs serialises bookings per dealership (OK for demo scale).
- Capacity can change between availability and book; book is authoritative.
- No cancel/reschedule HTTP API yet; no burst queue/rate-limit.

---

## Summary

Two REST endpoints, resource model (bay + skilled technician over a time range), PostgreSQL, and pessimistic locks on the write path — mapped directly to Scenario A’s three requirements.
