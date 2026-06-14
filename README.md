# FIRE Portfolio Tracker

A backend-first portfolio analytics engine (Java / Spring Boot + PostgreSQL) for tracking
a dollar-cost-averaging strategy and projecting a financial-independence date. See
[CLAUDE.md](CLAUDE.md) for the design brief and [ROADMAP.md](ROADMAP.md) for the build plan.

> Status: **Step 1** — project skeleton, transaction CRUD, Flyway schema, Testcontainers + CI.

## Tech

Java 21 · Spring Boot 3.x (Boot 4 line) · Spring Data JPA · Flyway · PostgreSQL ·
Testcontainers · JUnit 5.

## Running locally

Requires a JDK (21+) and a PostgreSQL database. Configuration is read from the environment
(see [.env.example](.env.example)); the defaults target a local Postgres named `firetracker`.

Start a throwaway Postgres with Docker:

```bash
docker run --name firetracker-db -e POSTGRES_DB=firetracker \
  -e POSTGRES_USER=firetracker -e POSTGRES_PASSWORD=firetracker \
  -p 5432:5432 -d postgres:16
```

Then run the app (Flyway applies the schema on startup):

```bash
./mvnw spring-boot:run
```

App: <http://localhost:8080> · Health: <http://localhost:8080/actuator/health>

## API (so far)

| Method | Path                     | Description                                  |
|--------|--------------------------|----------------------------------------------|
| POST   | `/api/transactions`      | Create a transaction (validated)             |
| GET    | `/api/transactions`      | List, optional `ticker`, `from`, `to` filters |
| GET    | `/api/transactions/{id}` | Fetch one (404 if absent)                    |
| GET    | `/actuator/health`       | Liveness                                     |

Example:

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"ticker":"VWRA","type":"BUY","quantity":3.5,"pricePerUnit":100.25,"currency":"USD","fee":1.0,"transactionDate":"2026-01-15"}'
```

## Tests

```bash
./mvnw verify
```

Integration tests use Testcontainers, so a running **Docker** daemon is required locally
(CI provides one). The web-layer test (`TransactionControllerTest`) needs no Docker:

```bash
./mvnw -Dtest=TransactionControllerTest test
```
