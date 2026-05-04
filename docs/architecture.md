# Architecture

This document describes the high-level architecture of BioQC, the major modules, and the design decisions behind them.

## System Topology

```
┌─────────────────────────────────────────────┐
│  Browser (analyst, supervisor, admin)        │
│  React 18 + Vite + TanStack Query            │
└──────────────────┬──────────────────────────┘
                   │ HTTPS, JWT in HttpOnly cookie
                   ▼
┌─────────────────────────────────────────────┐
│  Vercel (static SPA)                         │
│  bioqc-web → bioqc-demo.vercel.app           │
└──────────────────┬──────────────────────────┘
                   │ /api/* proxied
                   ▼
┌─────────────────────────────────────────────┐
│  Railway (Spring Boot Docker)                │
│  bioqc-api → api.bioqc-demo.dev              │
│  - REST controllers (15)                     │
│  - Spring Security (JWT + refresh)           │
│  - Spring Data JPA + Flyway                  │
│  - Scheduled jobs (lot expiry reclassify)    │
│  - Prometheus metrics, structured logs       │
└──────────────────┬──────────────────────────┘
                   │ JDBC (sslmode=require)
                   ▼
┌─────────────────────────────────────────────┐
│  Supabase (managed PostgreSQL)               │
│  - 30+ tables, 15 Flyway migrations          │
│  - Row-level audit, signature chain          │
└─────────────────────────────────────────────┘
```

## Module Map

### Backend (`bioqc-api/src/main/java/com/bioqc/`)

| Package | Responsibility |
|---------|---------------|
| `controller/` | REST entrypoints. Thin — input validation, delegate to service. |
| `service/` | Business logic. Westgard evaluation, signing, lot transitions, etc. |
| `service/reports/v2/` | Reports V2 subsystem — generators, signing, storage, catalog |
| `entity/` | JPA aggregates. Some are root entities (e.g. `QcRecord`); others are value-typed children. |
| `repository/` | Spring Data interfaces + custom queries. |
| `dto/` | Request/response DTOs — never expose entities directly to HTTP. |
| `security/` | JWT issuance, cookie management, password reset tokens. |
| `config/` | CORS, CorrelationId filter, Supabase database settings parser. |
| `filter/` | Servlet filters (Correlation-Id, etc.). |
| `scheduler/` | Background jobs (e.g. nightly reagent expiry reclassification). |
| `exception/` | Domain exceptions + global `@ControllerAdvice`. |
| `util/` | Stateless helpers — `ResponseMapper`, etc. |

### Frontend (`bioqc-web/src/`)

| Folder | Purpose |
|--------|---------|
| `pages/` | Route-level components, one per top-level URL. |
| `components/` | Shared UI — forms, tables, charts, modals. |
| `services/` | Axios client + per-resource API wrappers. |
| `hooks/` | Custom hooks, mostly TanStack Query wrappers. |
| `contexts/` | Auth context, theme context. |
| `lib/` | Pure utilities — formatters, validators. |
| `types/` | Shared TS types (manually mirrored from backend DTOs — pending OpenAPI generation). |

## Key Design Decisions

### Per-area QC schema split
Hematology has fundamentally different parameters from wet chemistry (per-channel CV, day/night shifts, no Westgard in the same form). Rather than overloading a single `QcParameter` table with optional columns, hematology has its own pair: `HematologyQcParameter` + `HematologyQcMeasurement`. Other areas share the polymorphic `AreaQcParameter` + `AreaQcMeasurement` because their schemas align.

Trade-off: two code paths instead of one. Win: each subsystem stays clean, independently testable, and changes to hematology don't risk biochemistry.

### Westgard rules are stateless
Each rule is a function `(measurement, history_window) → Optional<Violation>`. The service composes them and persists the resulting `WestgardViolation` records. No mutable rule state, no in-memory accumulators. This makes tests trivial — you build a fixture history, you call the rule, you assert.

### Signature chain over hash list
Each signed report record stores `sha256(pdf_bytes)` and `previous_signature_hash`. This is hash chaining, not a Merkle tree (no batching needs). The chain root is verifiable from any point — alter one report's bytes and every downstream hash mismatches. Cheap to implement, strong guarantee.

### Reagent state machine in the service layer
Lot transitions (`EM_ESPERA → ABERTO → EM_USO → FINAL_DE_USO → INATIVO`) are enforced in `ReagentService` through explicit transition methods, not setters on the entity. The entity exposes a `status` field but the service mediates every change with rule checks (e.g. you can't reopen a lot in `FINAL_DE_USO`). A future improvement is to push these constraints into Postgres triggers for defense in depth.

### TanStack Query for server state, no Redux
The frontend has very little client-only state — almost everything is "what's on the server right now." TanStack Query gives caching, background refetch, optimistic updates, and stale-while-revalidate without the boilerplate of a global store. The few client-only states (auth context, theme) are React contexts.

### Spring profiles for environment isolation
- `local` — H2 in-memory, no SMTP, no flyway.
- `prod` — Postgres via `SPRING_DATASOURCE_*`, flyway with `baseline-on-migrate=true`, Prometheus enabled, `Secure` + `SameSite=None` cookies for cross-origin Vercel ↔ Railway.

### Correlation IDs everywhere
A servlet filter reads `X-Correlation-Id` from the client (or generates one), stamps it onto the MDC, propagates it in response headers and logs. Combined with structured JSON logging, every request can be traced end-to-end across the SPA → API → DB.

## Data Flow Examples

### Recording a QC measurement
1. Analyst submits form → SPA validates with Zod → POST `/api/qc/records`.
2. `QcRecordController` validates DTO, delegates to `QcService.recordMeasurement()`.
3. Service loads the active `QcReferenceValue` for (analyte, instrument, lot, level), persists `QcRecord`.
4. Westgard evaluator pulls the recent history window, runs all rules.
5. Any violations persist as `WestgardViolation` rows.
6. Audit log row written with actor + diff.
7. Response includes the saved record + any violations triggered.

### Signing a monthly report
1. Supervisor selects period + report type → POST `/api/v2/reports/sign`.
2. `ReportV2Controller` checks rate limit (in-memory token bucket).
3. `ReportServiceV2` builds the PDF (catalog → generator → JFreeChart embedding).
4. `ReportSigner` computes `sha256(pdf_bytes)`, fetches previous chain head from `ReportSignatureLog`.
5. New row inserted: `(hash, previous_hash, signer, ts, report_run_id)`.
6. PDF is streamed back; download recorded in `ReportDownloadLog`.

### Verifying a report
1. Anyone scans the QR code on a printed PDF → opens `/verify/{hash}` (public).
2. Frontend calls `GET /api/v2/reports/verify/{hash}`.
3. Service looks up the `ReportSignatureLog` row.
4. Public response: signed-by name, signed-at timestamp, presence in chain.
5. Frontend renders a clean confirmation page.

## What's Intentionally Not Here

- **No microservices.** The lab serves dozens of analysts, not millions of users. A monolith with clean modules is faster to ship and reason about.
- **No CQRS, no event sourcing.** Audit needs are met by an `audit_log` table + Flyway migrations. Event sourcing would be overkill for the read patterns.
- **No Kubernetes.** Railway runs the Docker image with a healthcheck. That's enough at this scale.

## Future Work

- Migrate frontend types to OpenAPI-generated.
- Push reagent state-machine constraints to Postgres triggers.
- Add OpenTelemetry tracing alongside Prometheus metrics.
- Extract Westgard evaluator into its own module with public interface for cross-area reuse.
