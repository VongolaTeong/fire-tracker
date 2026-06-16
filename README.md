# FIRE Portfolio Tracker

A backend-first portfolio analytics engine (Java / Spring Boot + PostgreSQL) for tracking a
dollar-cost-averaging (DCA) strategy and projecting a financial-independence (FIRE) date. It
ingests a generic transaction ledger and computes holdings, FX-correct valuation, money-weighted
returns (XIRR/CAGR), and a Monte Carlo projection — with a thin Vue dashboard on top.

See [CLAUDE.md](CLAUDE.md) for the design brief and [ROADMAP.md](ROADMAP.md) for the build plan.

> **Status:** analytics engine complete (CSV import → holdings → valuation → XIRR/CAGR →
> scheduled price/FX ingestion → Monte Carlo projection) with a Vue dashboard and end-to-end
> integration coverage. Next up: deployment (Step 9).

## Tech

**Backend:** Java 21 · Spring Boot 4.1 · Spring Data JPA · Flyway · PostgreSQL · Testcontainers ·
JUnit 5 · AssertJ.
**Frontend:** Vue 3 (Composition API) · Vite · Chart.js (via `vue-chartjs`) · Vitest.

## Architecture

A standalone, independently deployable REST service; the Vue SPA is a separate consumer of that
API. All money is `BigDecimal` end to end; the reporting currency is SGD.

```mermaid
flowchart LR
    subgraph Frontend ["Vue 3 SPA · deployed separately"]
        UI["Dashboard:<br/>value-over-time + FIRE fan"]
    end

    subgraph Backend ["Spring Boot service"]
        API["REST controllers + CORS<br/>/api/transactions · /api/portfolio"]
        DOMAIN["Domain: holdings · FX-correct valuation<br/>XIRR / CAGR · Monte Carlo projection"]
        INGEST["Ingestion: CSV import<br/>scheduled price/FX pull (provider interfaces)"]
        API --> DOMAIN
        INGEST --> DOMAIN
    end

    DB[("PostgreSQL<br/>Flyway-managed schema")]
    EXT["Market data:<br/>Stooq prices · Frankfurter FX"]

    UI -->|JSON over HTTP| API
    DOMAIN --> DB
    INGEST --> DB
    INGEST -. "daily @Scheduled" .-> EXT
```

**Layers**

| Layer       | Responsibility                                                                 |
|-------------|--------------------------------------------------------------------------------|
| Ingestion   | CSV ledger import (idempotent via `external_id`); daily price/FX pull behind swappable `PriceProvider` / `FxProvider` interfaces, upserting on the unique constraints |
| Domain      | Net holdings; FX-correct valuation (txn-date rate for cost basis, latest rate for current value); XIRR (Newton-Raphson + bisection fallback), CAGR, unrealized P/L; Monte Carlo FIRE projection |
| Persistence | PostgreSQL via JPA; schema owned by Flyway migrations (`V1` ledger, `V2` price/FX) |
| API         | REST controllers returning JSON; RFC 9457 problem responses; CORS for the SPA origin |

### Project layout

```
src/main/java/com/firetracker/
  transaction/   ledger: entity, CSV parser, import + CRUD service/controller
  instrument/    instrument reference data
  marketdata/    price_history & fx_rate entities, providers, scheduled ingestion
  portfolio/     holdings + FX-correct valuation (+ value-over-time history)
  performance/   XIRR, CAGR, invested / current value / P&L
  projection/    Monte Carlo simulation + projection endpoint
  common/        clock, CORS/web config, global exception handler
src/main/resources/db/migration/   Flyway V1/V2
frontend/        Vue 3 + Vite dashboard (separate build)
seed.sql, sample-transactions.csv  fake demo fixtures (no real data)
```

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

Load the fake demo data (invented numbers — safe to commit and to demo with):

```bash
# either seed the DB directly...
psql "postgresql://firetracker:firetracker@localhost:5432/firetracker" -f seed.sql
# ...or import the ledger through the API (then add price/FX rows yourself):
curl -F file=@sample-transactions.csv http://localhost:8080/api/transactions/import
```

## API

Base path `/api`. All money is in SGD unless noted. Errors are returned as
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` with a `title`/
`detail` (and a field-keyed `errors` map for validation failures).

| Method | Path                                       | Description                                              |
|--------|--------------------------------------------|----------------------------------------------------------|
| POST   | `/api/transactions`                        | Create a transaction (validated)                         |
| GET    | `/api/transactions`                        | List, optional `ticker`, `from`, `to` filters            |
| GET    | `/api/transactions/{id}`                   | Fetch one (404 if absent)                                |
| POST   | `/api/transactions/import`                 | Bulk CSV import (multipart `file`; idempotent via `external_id`) |
| GET    | `/api/portfolio/holdings`                  | Net units held per instrument                            |
| GET    | `/api/portfolio/value`                     | Current SGD value + per-currency breakdown               |
| GET    | `/api/portfolio/value-history`             | SGD value at each month-end (value-over-time chart)      |
| GET    | `/api/portfolio/performance`               | XIRR, CAGR, total invested, unrealized P/L               |
| GET    | `/api/portfolio/projection?targetDate=…`   | Monte Carlo FIRE projection (p10/p50/p90 fan)            |
| GET    | `/actuator/health`                         | Liveness (the keep-alive pinger targets this)            |

**Status codes:** `400` invalid payload / malformed CSV / non-future projection target;
`404` unknown transaction id; `422` portfolio can't be valued because a held instrument has no
price or FX rate yet (`Missing market data`).

Create a transaction:

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"ticker":"VWRA","type":"BUY","quantity":3.5,"pricePerUnit":100.25,"currency":"USD","fee":1.0,"transactionDate":"2026-01-15"}'
```

Example `GET /api/portfolio/value` response (abbreviated):

```json
{
  "reportingCurrency": "SGD",
  "totalValueSgd": 9636.29,
  "positions": [
    { "ticker": "ES3",  "units": 890.0,  "currency": "SGD", "price": 3.52,   "marketValueSgd": 3132.80 },
    { "ticker": "VWRA", "units": 37.2,   "currency": "USD", "price": 129.50, "fxRate": 1.35, "marketValueSgd": 6503.49 }
  ],
  "byCurrency": [
    { "currency": "SGD", "marketValueLocal": 3132.80, "marketValueSgd": 3132.80 },
    { "currency": "USD", "marketValueLocal": 4817.40, "marketValueSgd": 6503.49 }
  ]
}
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
npm run test     # unit tests for the API-response → chart-data mappers
```

## Tests

```bash
./mvnw verify
```

A clean checkout passes with **only the fake seed data** — no real numbers or secrets are
required. Coverage spans the algorithms (XIRR, Monte Carlo) test-first, the controllers as
web-layer slices, per-service integration tests, and a full
[`EndToEndFlowIntegrationTest`](src/test/java/com/firetracker/EndToEndFlowIntegrationTest.java)
that drives import → holdings → value → performance → projection over real HTTP.

Integration tests use Testcontainers, so a running **Docker** daemon is required locally
(CI provides one). The web-layer slice tests (e.g. `TransactionControllerTest`) need no Docker:

```bash
./mvnw -Dtest=TransactionControllerTest test
```

## Data privacy

No real financial data or secrets live in the repo. The committed `seed.sql` and
`sample-transactions.csv` hold **invented** numbers so the app runs, tests pass, and demos work
without exposing anything real; the public demo points at a separate database seeded with that
fake data. All credentials (DB URL/username/password, any market-data key) come from environment
variables only — see [.env.example](.env.example) and the `.gitignore` rules.
