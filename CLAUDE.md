# FIRE Portfolio Tracker — Project Brief

A backend-first portfolio analytics engine for tracking a dollar-cost-averaging (DCA)
investment strategy and projecting a financial-independence (FIRE) date. The emphasis is
production-grade backend engineering in Java/Spring — schema migrations, idempotent
scheduled jobs, money-correct math, and integration tests — with a lightweight Vue
dashboard for visualization.

## Goal & framing

This is a **generic portfolio analytics engine**, not "my personal finances." It ingests
any transaction ledger in a defined CSV format and computes valuation, returns, and
projections. Real personal data NEVER lives in the repo — see "Data privacy" below.

Primary use case: monthly DCA into a global equity ETF (e.g. VWRA) bought in USD,
reported in SGD, with a projected retirement target date ~15 years out.

## Tech stack

- **Language/Framework:** Java (recent LTS), Spring Boot 3.x
- **Persistence:** PostgreSQL, Spring Data JPA, Flyway for migrations
- **Build:** Gradle (Kotlin DSL) or Maven — pick one and stay consistent
- **Testing:** JUnit 5, Spring Boot Test, Testcontainers (Postgres) for integration tests
- **Scheduling:** Spring `@Scheduled` for daily price/FX ingestion
- **Frontend:** Vue 3 (Composition API) + Vite, with a charting library (Chart.js via
  `vue-chartjs`, or ECharts) — a thin dashboard that consumes the REST API
- **Deployment (all free tier, target $0):** Render web service (app; Koyeb as an
  alternative) + Neon (Postgres; serverless and persistent, unlike Render's expiring free
  DB) + Netlify or Cloudflare Pages (Vue static build, always-on, no cold start). A
  scheduled pinger (UptimeRobot / cron-job.org) hits `/health` every ~10 min to keep the
  JVM app warm so the demo link responds instantly. Tune JVM memory to fit Render's
  512 MB (e.g. `-XX:MaxRAMPercentage=75`).

Keep the backend a standalone, independently deployable service with a clean REST
boundary; the Vue frontend is a separate consumer of that API.

## Architecture (high level)

```
ingestion layer  -> transaction CSV import, daily price pull, daily FX pull
domain layer     -> holdings, valuation, performance (XIRR/CAGR), projection
persistence      -> Postgres via JPA + Flyway-managed schema
api layer        -> REST controllers (JSON), CORS for the frontend origin
scheduling       -> idempotent daily jobs that upsert price/FX rows
frontend         -> Vue 3 SPA (deployed separately) consuming the REST API
```

## Data model (initial)

- **instrument**: `ticker` (PK), `name`, `currency`, `type` (ETF/STOCK), `created_at`
- **transaction**: `id` (PK), `ticker` (FK), `type` (BUY/SELL/DIVIDEND), `quantity`,
  `price_per_unit`, `currency`, `fee`, `transaction_date`, `external_id` (nullable, for
  import dedup), `created_at`
- **price_history**: `ticker` (FK), `price_date`, `close_price`, `currency` —
  unique constraint on `(ticker, price_date)`
- **fx_rate**: `rate_date`, `base_currency`, `quote_currency`, `rate` —
  unique constraint on `(rate_date, base_currency, quote_currency)`

Money stored as `NUMERIC`/`BigDecimal`, never `double`. Reporting currency is SGD.

## API surface (target)

- `POST /api/transactions` — add a single transaction
- `GET  /api/transactions` — list, with optional date/ticker filters
- `POST /api/transactions/import` — bulk CSV import (idempotent via `external_id`)
- `GET  /api/portfolio/holdings` — current units held per instrument
- `GET  /api/portfolio/value` — current market value in SGD (and per-currency breakdown)
- `GET  /api/portfolio/performance` — XIRR, CAGR, total invested, unrealized P/L
- `GET  /api/portfolio/projection?targetDate=YYYY-MM-DD` — Monte Carlo projection
- `GET  /api/prices/{ticker}` — price history (debug/inspection)
- `GET  /health` — liveness check (Spring Actuator `/actuator/health` also works); the
  uptime pinger targets this to keep the app warm

## Key technical challenges (the hard parts worth getting right)

1. **XIRR engine.** Compute money-weighted return over irregular cash flows. Implement
   Newton-Raphson with a bisection fallback for non-convergence. This is the core
   algorithm — write it test-first against known reference cases (e.g. cross-check
   against a spreadsheet XIRR result).
2. **Idempotent scheduled jobs.** Daily price and FX pulls must be safe to re-run.
   Use upserts (`ON CONFLICT ... DO UPDATE`) keyed on the unique constraints above so
   retries or duplicate runs never double-insert.
3. **FX-correct valuation.** Convert each transaction at its transaction-date rate for
   cost basis, but value current holdings at the latest rate. Keep these distinct.
4. **Monte Carlo projection.** Simulate N future paths from an assumed annual return
   mean/volatility, continuing the DCA contribution schedule, and report percentile
   outcomes (p10/p50/p90) at the target date.

## Data privacy (non-negotiable — set up before first commit)

- `.gitignore` must include: `.env`, `*.db`, `data/`, `application-local.properties`,
  `application-local.yml`, and any real `*.csv` ledger files.
- All secrets (DB URL/credentials, market-data API key) via environment variables only.
  Never commit a real connection string or key.
- Commit a **fake** `seed.sql` and `sample-transactions.csv` with invented numbers so
  the app runs, tests pass, and demos work without exposing real figures.
- The public live demo points at a separate demo database seeded with fake data.
- Real personal numbers stay in a local/private instance only.

Doing secrets management correctly is itself a positive signal — treat it as a feature.

## Market data

Need a price source for VWRA and an FX source for USD→SGD. Start with **manual CSV
import of prices** so the core works with zero external dependencies, then add a free
market-data API behind an interface (so the provider is swappable) for the scheduled job.
Abstract the data source behind a `PriceProvider` / `FxProvider` interface.

## Build order (evening-sized increments)

1. Project skeleton: Spring Boot + Postgres + Flyway, first migration, transaction CRUD,
   plus `.gitignore`, env-var config, a Testcontainers slice test, and GitHub Actions CI
   from the very first commit.
2. CSV import endpoint with dedup via `external_id`; commit fake seed data.
3. Holdings + current-value endpoint (manual price/FX rows first).
4. Performance endpoint: XIRR + CAGR, written test-first.
5. Price/FX ingestion behind provider interfaces; wire up the idempotent scheduled job.
6. Monte Carlo projection endpoint.
7. Vue dashboard: portfolio value over time + FIRE projection fan chart (p10/p50/p90),
   consuming the REST API.
8. Coverage sweep: end-to-end integration tests for any flow not yet covered, plus a clean
   README with architecture diagram + API docs. (Unit/integration tests grow per step, not
   here — this is the gap-filling pass.)
9. Deploy: backend to Render + Neon, frontend to Netlify/Cloudflare Pages; configure CORS
   and an env-based API base URL; set up the keep-alive pinger; point the public demo at a
   separate demo database seeded with fake data.

## Working agreements (for Claude Code)

- Propose a plan and let me review before large changes.
- Tests land in the same step as the code they cover — never deferred. Test-first for the
  algorithms that matter (XIRR, Monte Carlo); test-alongside for controllers and wiring.
  CI runs the suite on every push from the first commit.
- Use `BigDecimal` for all monetary math.
- Keep commits small and frequent with clear messages — the history should read as a
  series of deliberate, reviewable steps.
- Flag anything that would put real financial data or secrets into version control.
