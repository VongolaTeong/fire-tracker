# Roadmap

Incremental build plan for the FIRE Portfolio Tracker. Each step is a self-contained,
evening-sized increment that ends with something runnable, tested, and committed.
Checkboxes track progress; "Done when" is the bar for moving on.

**Conventions throughout**
- `BigDecimal` for all monetary math — never `double`.
- **Tests land in the same step (and ideally the same commit) as the logic they cover —
  never deferred to the end.** Use *test-first* for algorithmic / spec'd logic (XIRR,
  Monte Carlo, FX conversion, CSV dedup); *test-alongside* for controllers, repositories,
  and wiring.
- CI (GitHub Actions) runs the full suite on every push, from Step 1 onward.
- Small, frequent commits with clear messages.
- No real financial data or secrets in version control (see Data privacy in `CLAUDE.md`).

---

## Step 1 — Project skeleton, safety rails & CI
Goal: a running, tested Spring Boot app talking to Postgres, with secrets, privacy, and CI handled from commit #1.

- [x] Spring Boot 3.x project on a recent Java LTS (pick Gradle Kotlin DSL **or** Maven and stay consistent)
- [x] `.gitignore` in place **before** the first commit (`.env`, `*.db`, `data/`, `application-local.*`, real `*.csv`)
- [x] DB URL/credentials via environment variables only
- [x] Flyway wired up; `V1` migration creates `instrument` and `transaction` tables
- [x] Transaction CRUD: `POST /api/transactions`, `GET /api/transactions` (date/ticker filters)
- [x] `GET /health` liveness endpoint (or Spring Actuator `/actuator/health`)
- [x] Testcontainers (Postgres) wired with one thin slice test: boot the context, round-trip a transaction
- [x] GitHub Actions CI: build + run tests on every push

Done when: app boots against local Postgres, the migration runs cleanly, you can create/list transactions, and CI is green on push.

## Step 2 — CSV import (idempotent)
Goal: bulk-load a transaction ledger safely and repeatably.

- [x] `POST /api/transactions/import` parses the defined CSV format
- [x] Dedup via `external_id` so re-running the same file never double-inserts
- [x] Commit a **fake** `sample-transactions.csv` and `seed.sql` with invented numbers
- [x] Tests: re-import is idempotent (row count stable); a malformed row is rejected cleanly

Done when: importing the sample file twice yields the same row count the second time, with tests proving it.

## Step 3 — Holdings & current value
Goal: turn the ledger into a current portfolio snapshot.

- [x] `V2` migration: `price_history` and `fx_rate` tables (with their unique constraints)
- [x] Seed manual price/FX rows (no external API yet)
- [x] `GET /api/portfolio/holdings` — units held per instrument
- [x] `GET /api/portfolio/value` — market value in SGD + per-currency breakdown
- [x] Tests: holdings aggregation and SGD valuation against seeded data, incl. a multi-currency case

Done when: holdings and value endpoints return correct figures against the seeded data, covered by tests.

## Step 4 — Performance (XIRR + CAGR) — test-first
Goal: the core analytics. This is the headline algorithm.

- [x] Write XIRR tests **first** against known reference cases (cross-check a spreadsheet XIRR)
- [x] Implement Newton-Raphson with a bisection fallback for non-convergence
- [x] Add CAGR, total invested, unrealized P/L
- [x] FX-correct: transaction-date rate for cost basis, latest rate for current value (keep distinct)
- [x] `GET /api/portfolio/performance`

Done when: XIRR matches reference values within tolerance and all performance tests pass.

## Step 5 — Price/FX ingestion (scheduled, idempotent)
Goal: keep prices and rates current without manual rows.

- [x] `PriceProvider` / `FxProvider` interfaces (provider is swappable)
- [x] One free market-data API implementation behind each interface
- [x] `@Scheduled` daily job using upserts (`ON CONFLICT ... DO UPDATE`) on the unique constraints
- [x] Tests: the upsert job is idempotent (run twice, no dupes); providers are mocked in tests

Done when: the scheduled job populates `price_history`/`fx_rate`, is safe to run twice, and tests prove the idempotency.

## Step 6 — Monte Carlo projection — test-first
Goal: project the FIRE date with uncertainty.

- [x] Write tests first using a fixed RNG seed so outcomes are deterministic and assertable
- [x] Simulate N paths from assumed annual return mean/volatility, continuing the DCA schedule
- [x] Report percentile outcomes (p10/p50/p90) at the target date
- [x] `GET /api/portfolio/projection?targetDate=YYYY-MM-DD`

Done when: the endpoint returns sensible percentile bands and the seeded tests pass deterministically.

## Step 7 — Vue dashboard
Goal: a thin, polished frontend that makes the live demo land.

- [x] Vue 3 (Composition API) + Vite app, env-based API base URL
- [x] Portfolio value-over-time chart (Chart.js via `vue-chartjs`, or ECharts)
- [x] FIRE projection fan chart (p10/p50/p90)
- [x] CORS configured on the backend for the frontend origin
- [x] (Optional) component test for the API-response → chart-data mapping

Done when: the dashboard renders real numbers from the API locally.

## Step 8 — Coverage sweep & docs
Goal: close gaps and make it easy to read. (Most tests already exist from earlier steps — this is the sweep, not the start.)

- [ ] Review coverage; add end-to-end integration tests for any flow not yet covered (import → holdings → value → performance → projection)
- [ ] README: architecture diagram + API docs + run instructions
- [ ] Confirm tests pass from a clean checkout using only the fake seed data

Done when: `clean checkout → run tests → all green` with no real data or secrets, and the main flows have integration coverage.

## Step 9 — Deploy (free, $0 target)
Goal: a live link that responds instantly.

- [ ] Backend on Render free tier (tune JVM to fit 512 MB, e.g. `-XX:MaxRAMPercentage=75`)
- [ ] Postgres on Neon free tier
- [ ] Vue static build on Netlify or Cloudflare Pages
- [ ] CORS + env-based API URL wired between the two deploys
- [ ] Keep-alive pinger (UptimeRobot / cron-job.org) hitting `/health` every ~10 min
- [ ] Public demo points at a **separate** demo database seeded with fake data

Done when: the live URL loads the dashboard quickly and shows the seeded demo portfolio.
