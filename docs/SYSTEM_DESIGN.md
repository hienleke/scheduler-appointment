# System Design

Scale-out design for the Appointment Scheduler at 1 million registered customers. The service stays a modular monolith. Live booking state sits in Redis. PostgreSQL is the durable copy, filled later through Kafka. The book request does not wait for the database.

## 1. What must stay true

| Rule | How the design holds it |
|------|-------------------------|
| Book by customer, vehicle, service type, dealership, and start time | `POST /api/appointments` |
| A slot is free only when one active bay and one qualified technician are both free for the full duration | Redis occupancy for that dealership and time window |
| A confirmed appointment is stored | Written in Redis first. The same fact is applied to PostgreSQL by a Kafka consumer |
| Two requests cannot take the same bay or the same technician | One atomic update on the Redis primary. The database rejects a conflicting apply as a backstop |
| Availability, and reading an appointment that was just booked | Read Redis, not PostgreSQL |
| History and reporting | Read PostgreSQL after the consumer has caught up |

"1 million users" means registered customers, not 1 million concurrent bookings. Each customer books a few times a year, and browses availability far more often than they book. The read path is the large one. The write path is a short reservation plus an asynchronous copy into the database.

## 2. Why the current shape does not carry that load

Today one database transaction does every job:

1. Load customer, vehicle, service type, and dealership.
2. Lock every active bay of the dealership, then every qualified technician.
3. Insert the first free pair as `CONFIRMED`.

Availability asks the same database, once per bay and once per technician, with no lock. The database is the lock, the live read, and the archive at the same time. Pods that want different bays still wait on each other, and browsing competes with that wait.

The target design separates those jobs. Redis answers "is this slot free?" and "what did I just book?". Kafka carries the confirmed appointment to PostgreSQL. PostgreSQL keeps history. It does not decide the next booking.

## 3. Architecture

```text
  clients
     │
     ▼
  Scheduler API          stateless
     │
     │  read occupancy and appointments
     │  write a reservation atomically
     ▼
  Redis                  live state
     │
     │  AppointmentConfirmed / Cancelled
     ▼
  Kafka                  buffer
     │
     ▼
  Sync consumer          the only writer of appointments
     │
     ▼
  PostgreSQL             durable history
```

Any API instance can serve any dealership. No instance keeps slot state of its own. A read does not wait for the consumer. A book returns when Redis holds the appointment and Kafka has accepted the event.

Catalog data (who the customer is, which vehicle they own, which technicians have the skill) is reference data. It is checked before a reservation and may be cached. It is not the occupancy record.

### Availability

Availability is a Redis read.

1. Take the service duration.
2. Look at bay and technician occupancy for that dealership and window.
3. A resource is free when none of the window is already held.
4. Return whether the window is available, and how many bays and technicians are free.

A booking that has just landed in Redis is visible to the next read. There is no second copy of availability sitting in PostgreSQL for this call.

### Book

1. If this idempotency key was already reserved, return that appointment. Do not reserve again and do not publish again.
2. Reject an unknown customer, vehicle, service type, or dealership, and a vehicle that does not belong to the customer.
3. Atomically choose the first free bay and the first free qualified technician, record the occupancy, and store the appointment. If no pair is free, return a conflict. PostgreSQL is not involved.
4. Publish the confirmed appointment to Kafka.
5. If Kafka does not accept the event, undo the Redis reservation and fail the request. The client retries with the same idempotency key.
6. Return the appointment from Redis. The row in PostgreSQL arrives later.

### Read after book

The appointment just created is read from Redis. A dealership's day view is read from Redis. Both exist before PostgreSQL has the row.

PostgreSQL is the place to read appointments that have left the live horizon, once the consumer has applied them. A report that must include a booking from the last moment uses Redis for the live day and PostgreSQL for older days.

## 4. Redis

Redis is the system of record for slots that can still be booked. One primary serves the reservation and the live read, so a stale replica cannot offer a slot that was just taken. A replica exists for failover. It is not an authority for a new booking.

All keys for one dealership are kept together, so the reservation can update occupancy, the appointment, the day view, and the idempotency key in one atomic step.

Time is divided into fixed buckets. A service owns every bucket from its start through its duration. Two appointments conflict when they share a bucket on the same bay or the same technician. Walking candidates in a fixed order (bay, then technician) means two reservations cannot deadlock.

The occupancy entry is the reservation, not a short lock wrapped around a database insert. It remains until that bucket is over. If it disappeared while Kafka was still behind, another customer could take the bay before the first appointment existed anywhere durable.

What Redis holds:

| Record | Purpose |
|--------|---------|
| Bay occupancy for a dealership and bucket | The bay is taken. Readers see it |
| Technician occupancy for a dealership and bucket | The technician is taken. Readers see it |
| Appointment document | Returned to the client and served on later reads |
| Dealership day index | Which appointments fall on that local day |
| Idempotency key | Points at the appointment already created for that request |

A cancel removes the occupancy, updates the document, and publishes a cancelled event. The same consumer applies it.

## 5. Kafka

Kafka is the buffer between live state and the database. The consumer may lag. Booking stays correct while it lags, because the slot was already taken in Redis.

| Event | Effect in PostgreSQL |
|-------|----------------------|
| Appointment confirmed | Insert or update that appointment as confirmed |
| Appointment cancelled | Mark it cancelled so the historical slot is free |

The consumer upserts by appointment id, so a redelivered event does not create a second row. A database constraint that forbids two confirmed overlaps is the backstop for a broken reservation, not the way a normal conflict is detected. A normal conflict never leaves Redis.

The sync consumer is the only writer of appointments. Mail, reminders, a dealer board, and analytics are later consumer groups on the same events. They do not take a slot and they do not write the appointment row.

Kafka retains events long enough to rebuild the database. Redis does not. When a slot is over, the live document can disappear from Redis and the history remains in PostgreSQL.

## 6. PostgreSQL

PostgreSQL stores what Kafka has applied. It trails Redis by however far the consumer is behind.

| Data | Writer | Used for |
|------|--------|----------|
| Customers and vehicles | Profile changes | Checking the book request |
| Dealerships, service types, bays, technicians, skills | Admin changes | Choosing candidates. Not read once per availability call |
| Appointments | Sync consumer | History and reports |
| Applied idempotency keys | Sync consumer | Ignoring a replayed event |

The appointment store forbids two confirmed rows that overlap on the same bay or the same technician. That rule protects the archive. It is not on the request path.

Availability does not need a database replica. A replica is for history and reports. If the appointment store ever has to be split, the split key is the dealership, matching the Kafka event key, so one booking still lands in one place.

## 7. Failure

| What fails | What the client gets | What remains |
|------------|----------------------|--------------|
| No free bay and technician | Conflict | Redis unchanged, no event |
| Response lost, same idempotency key retried | The appointment already in Redis | One reservation. The consumer writes one row |
| Process dies after Redis, before Kafka accepts | Retry publishes the same appointment | Consumer treats the duplicate as the same row |
| Kafka rejects the event | Failure, reservation undone | No occupancy, no event |
| Consumer is behind | Book and availability still succeed | Redis is ahead of PostgreSQL. Live views use Redis |
| Consumer hits an overlap constraint | The book already succeeded | The event is held for repair. The Redis reservation is not dropped |
| Redis unavailable | Book and live availability fail | PostgreSQL still has what was synced. It is not used to take a new slot |
| PostgreSQL unavailable | Book still succeeds | Kafka holds the event. The consumer retries. Occupancy stays in Redis |

## 8. What this design does not do

- Lock every bay and technician row in PostgreSQL in order to book.
- Read live availability from PostgreSQL.
- Wait for the database before telling the client the appointment is confirmed.
- Split customer, inventory, and booking into separate services. One API reserves in Redis and publishes. One consumer writes appointments.
- Coordinate a lock across several independent Redis nodes.
- Run active-active writes in more than one region.
