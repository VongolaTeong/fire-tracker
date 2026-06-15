# FIRE Portfolio Tracker

A backend-first portfolio analytics engine (Java / Spring Boot + PostgreSQL) for tracking
a dollar-cost-averaging strategy and projecting a financial-independence date. See
[CLAUDE.md](CLAUDE.md) for the design brief and [ROADMAP.md](ROADMAP.md) for the build plan.

> Status: **Step 7** — Vue dashboard (value-over-time + FIRE projection fan) on top of the
> full analytics engine: CSV import, holdings/valuation, XIRR/CAGR, scheduled price/FX
> ingestion, and Monte Carlo projection.

## Tech

**Backend:** Java 21 · Spring Boot 3.x (Boot 4 line) · Spring Data JPA · Flyway · PostgreSQL ·
Testcontainers · JUnit 5.
**Frontend:** Vue 3 (Composition API) · Vite · Chart.js (via `vue-chartjs`) · Vitest.

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

| Method | Path                                        | Description                                         |
|--------|---------------------------------------------|-----------------------------------------------------|
| POST   | `/api/transactions`                         | Create a transaction (validated)                    |
| GET    | `/api/transactions`                         | List, optional `ticker`, `from`, `to` filters       |
| GET    | `/api/transactions/{id}`                    | Fetch one (404 if absent)                           |
| POST   | `/api/transactions/import`                  | Bulk CSV import (idempotent via `external_id`)      |
| GET    | `/api/portfolio/holdings`                   | Net units held per instrument                       |
| GET    | `/api/portfolio/value`                      | Current SGD value + per-currency breakdown          |
| GET    | `/api/portfolio/value-history`              | SGD value at each month-end (value-over-time chart) |
| GET    | `/api/portfolio/performance`                | XIRR, CAGR, total invested, unrealized P/L          |
| GET    | `/api/portfolio/projection?targetDate=…`    | Monte Carlo FIRE projection (p10/p50/p90 fan)       |
| GET    | `/actuator/health`                          | Liveness                                            |

Example:

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"ticker":"VWRA","type":"BUY","quantity":3.5,"pricePerUnit":100.25,"currency":"USD","fee":1.0,"transactionDate":"2026-01-15"}'
```

## Frontend (Vue dashboard)

A thin Vue 3 + Vite SPA in [`frontend/`](frontend/) consumes the REST API and renders the
portfolio value-over-time line and the FIRE projection fan (p10/p50/p90), plus performance
KPIs and holdings. The API base URL is environment-driven (`VITE_API_BASE_URL`, default
`http://localhost:8080`); CORS on the backend allows the Vite dev origin by default
(override with `APP_CORS_ALLOWED_ORIGINS`).

With the backend running (and some seeded data — see `seed.sql` / `sample-transactions.csv`):

```bash
cd frontend
npm install
npm run dev      # dashboard on http://localhost:5173
```

Frontend unit tests (pure API-response → chart-data mappers):

```bash
cd frontend && npm run test
```

## Tests

```bash
./mvnw verify
```

Integration tests use Testcontainers, so a running **Docker** daemon is required locally
(CI provides one). The web-layer slice tests (e.g. `TransactionControllerTest`) need no Docker:

```bash
./mvnw -Dtest=TransactionControllerTest test
```
