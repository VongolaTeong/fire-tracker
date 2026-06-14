# Roadmap

Incremental build plan for the FIRE Portfolio Tracker. Each step is a self-contained,
evening-sized increment that ends with something runnable and committed. Checkboxes track
progress; "Done when" is the bar for moving on.

**Conventions throughout**
- `BigDecimal` for all monetary math — never `double`.
- Test-first for the XIRR and Monte Carlo logic.
- Small, frequent commits with clear messages.
- No real financial data or secrets in version control (see Data privacy in `CLAUDE.md`).

---

## Step 1 — Project skeleton & safety rails
Goal: a running Spring Boot app talking to Postgres, with secrets and privacy handled from commit #1.

- [ ] Spring Boot 3.x project, Java 21 (pick Gradle Kotlin DSL **or** Maven and stay consistent)
- [ ] `.gitignore` in place **before** the first commit (`.env`, `*.db`, `data/`, `application-local.*`, real `*.csv`)
- [ ] DB URL/credentials via environment variables only
- [ ] Flyway wired up; `V1` migration creates `instrument` and `transaction` tables
- [ ] Transaction CRUD: `POST /api/transactions`, `GET /api/transactions` (date/ticker filters)
- [ ] `GET /health` liveness endpoint (or Spring Actuator `/actuator/health`)

Done when: app boots against a local Postgres, the migration runs cleanly, and you can create and list transactions.

## Step 2 — CSV import (idempotent)
Goal: bulk-load a transaction ledger safely and repeatably.

- [ ] `POST /api/transactions/import` parses the defined CSV format
- [ ] Dedup via `external_id` so re-running the same file never double-inserts
- [ ] Commit a **fake** `sample-transactions.csv` and `seed.sql` with invented numbers

Done when: importing the sample file twice yields the same row count the second time.

## Step 3 — Holdings & current value
Goal: turn the ledger into a current portfolio snapshot.

- [ ] `V2` migration: `price_history` and `fx_rate` tables (with their unique constraints)
- [ ] Seed manual price/FX rows (no external API yet)
- [ ] `GET /api/portfolio/holdings` — units held per instrument
- [ ] `GET /api/portfolio/value` — market value in SGD + per-currency breakdown

Done when: holdings and value endpoints return correct figures against the seeded data.

## Step 4 — Performance (XIRR + CAGR) — test-first
Goal: the core analytics. This is the headline algorithm.

- [ ] Write XIRR tests first against known reference cases (cross-check a spreadsheet XIRR)
- [ ] Implement Newton-Raphson with a bisection fallback for non-convergence
- [ ] Add CAGR, total invested, unrealized P/L
- [ ] FX-correct: transaction-date rate for cost basis, latest rate for current value (keep distinct)
- [ ] `GET /api/portfolio/performance`

Done when: XIRR matches reference values within tolerance and all performance tests pass.

## Step 5 — Price/FX ingestion (scheduled, idempotent)
Goal: keep prices and rates current without manual rows.

- [ ] `PriceProvider` / `FxProvider` interfaces (provider is swappable)
- [ ] One free market-data API implementation behind each interface
- [ ] `@Scheduled` daily job using upserts (`ON CONFLICT ... DO UPDATE`) on the unique constraints
- [ ] Verify re-running the job is safe (no duplicates)

Done when: the scheduled job populates `price_history`/`fx_rate` and is safe to run twice.

## Step 6 — Monte Carlo projection
Goal: project the FIRE date with uncertainty.

- [ ] Simulate N paths from assumed annual return mean/volatility, continuing the DCA schedule
- [ ] Report percentile outcomes (p10/p50/p90) at the target date
- [ ] `GET /api/portfolio/projection?targetDate=YYYY-MM-DD`
- [ ] Tests covering deterministic seeds / edge cases

Done when: the endpoint returns sensible percentile bands for a given target date.

## Step 7 — Vue dashboard
Goal: a thin, polished frontend that makes the live demo land.

- [ ] Vue 3 (Composition API) + Vite app, env-based API base URL
- [ ] Portfolio value-over-time chart (Chart.js via `vue-chartjs`, or ECharts)
- [ ] FIRE projection fan chart (p10/p50/p90)
- [ ] CORS configured on the backend for the frontend origin

Done when: the dashboard renders real numbers from the API locally.

## Step 8 — Tests & docs
Goal: prove it works and make it easy to read.

- [ ] Testcontainers (Postgres) integration tests across the main flows
- [ ] README: architecture diagram + API docs + run instructions
- [ ] Confirm tests pass from a clean checkout using only the fake seed data

Done when: `clean checkout → run tests → all green` with no real data or secrets.

## Step 9 — Deploy (free, $0 target)
Goal: a live link that responds instantly.

- [ ] Backend on Render free tier (tune JVM to fit 512 MB, e.g. `-XX:MaxRAMPercentage=75`)
- [ ] Postgres on Neon free tier
- [ ] Vue static build on Netlify or Cloudflare Pages
- [ ] CORS + env-based API URL wired between the two deploys
- [ ] Keep-alive pinger (UptimeRobot / cron-job.org) hitting `/health` every ~10 min
- [ ] Public demo points at a **separate** demo database seeded with fake data

Done when: the live URL loads the dashboard quickly and shows the seeded demo portfolio.
