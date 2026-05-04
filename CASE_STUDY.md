# Case Study — BioQC: Migrating a Clinical Lab QC System from Python to Java/React

**Author:** Otavio Machado · **Engagement type:** Solo full-stack engineer · **Duration:** ~10 months · **Status:** In production

---

## Situation

A mid-size Brazilian clinical laboratory was running its daily Quality Control (QC) operation on a homegrown Python desktop application. The system had grown organically over years and was hitting hard limits:

- **Single-machine bottleneck.** Only one analyst at a time could record measurements; results sat in local SQLite files copied via USB stick.
- **No tamper-evident audit trail.** Reports were Word documents that anyone could edit retroactively. ISO 15189 surveillance was approaching and the lab had no way to prove a report hadn't been altered after signing.
- **Westgard rules half-implemented.** Some violations were caught manually by the QC supervisor reading printouts; others slipped through.
- **Reagent traceability was an Excel spreadsheet.** Lot expiry, opening dates, and consumption per analyzer were tracked by hand. Auditors wanted lot-to-result traceability.
- **No multi-area separation.** Biochemistry, hematology, immunology, parasitology, microbiology, and urinalysis all shared one schema with implicit conventions encoded only in the Python code.

The lab director hired me to **modernize the stack and harden the regulatory surface** without disrupting daily operations.

## Task

Deliver a production-grade replacement that:

1. Runs on the web (multiple analysts in parallel, accessible from any workstation).
2. Implements Westgard multirules correctly and uniformly across all lab areas.
3. Produces digitally-signed PDF reports with cryptographic tamper evidence.
4. Tracks reagent lots end-to-end with ISO 15189-grade traceability.
5. Migrates ~5 years of historical data without loss.
6. Cuts over with **zero data-entry downtime** during business hours.

Scope: backend, frontend, database schema, deployment, observability, and analyst training material. Stack was my call.

## Action

### Stack decision
- **Spring Boot 3 + Java 21** for the backend. The domain is regulated and multi-actor — RBAC, audit, signing, and statistical engines benefit from Java's type system, mature ecosystem (Flyway, JFreeChart, ZXing, OpenPDF), and Spring Security's battle-tested cookie/JWT plumbing.
- **React 18 + TypeScript + Vite** for the frontend. TanStack Query for server state, React Hook Form + Zod for typed forms, Tailwind 4 for design system velocity.
- **PostgreSQL on Supabase** for storage. Audit queries needed real SQL; lab data is relational and slow-changing.
- **Railway** for backend hosting (Docker image + healthcheck), **Vercel** for the frontend SPA.

### Core engineering decisions

**1. Per-area QC parameters, not a god table.**
I split QC parameters by laboratory area (`AreaQcParameter`) and added a separate hematology subsystem (`HematologyQcParameter` / `HematologyQcMeasurement`) because hematology has different reference logic (per-channel CV with day/night shifts) than wet chemistry. This let me ship areas incrementally.

**2. Westgard rules as a pure function over the historical window.**
Rule evaluation (`1-2s`, `1-3s`, `2-2s`, `R-4s`, `4-1s`, `10x`) runs against a sliding window of the most recent measurements per (analyte, instrument, lot, level). Violations are persisted in `WestgardViolation` with severity, so dashboards and reports can index without recomputing. Tests cover each rule in isolation plus combinations.

**3. Reports V2 — hash-chained signature log.**
Every signed PDF gets a row in `report_signature_log` containing `sha256(pdf_bytes)`, the previous signature's hash, and the signer identity. Each link references the previous one — altering any past report invalidates every downstream hash. A QR code in the PDF points to `/verify/{hash}`, where anyone with the file can confirm authenticity. Rate limiting protects the signing endpoint (in-memory token bucket).

**4. Reagent lot lifecycle as an explicit state machine.**
States: `EM_ESPERA → ABERTO → EM_USO → FINAL_DE_USO → INATIVO` with strict transitions and `eventDate` separate from `createdAt` (a unit can be opened on Monday and recorded Wednesday — both timestamps matter for audit). Stock movements carry typed reason codes. A scheduler reclassifies expired empty lots nightly.

**5. Migration strategy — paridade-first.**
For each module (biochemistry, hematology, …), I built the new code path, then ran both systems in parallel for 1-2 weeks against the same input. Diff reports flagged any divergence. Only after paridade was clean did I cut over. Five Flyway migrations were drafted with `dry-run/V*_dry_run.sql` siblings to rehearse against a Supabase preview branch.

**6. AI-generated commentary (optional).**
For monthly QC reports, the system can call Gemini to write a plain-Portuguese summary of trends and Westgard violations. The AI output is treated as an *advisory* field, never as a source of truth — it's appended to the report alongside the deterministic statistics, with a clear disclosure label.

### Operational hardening
- **JWT + refresh token rotation** on `HttpOnly`, `SameSite=None`, `Secure` cookies (cross-origin Vercel ↔ Railway).
- **Correlation-Id filter** propagating client request IDs through logs.
- **Prometheus metrics** + structured JSON logging via logstash-logback.
- **Flyway baseline-on-migrate** for safe production bootstrap.
- **ISO 15189 download log** — every PDF download is recorded with actor, timestamp, IP.

## Result

- **Cutover with zero analyst downtime.** Old system kept read-only; new system took over writes during a Saturday window.
- **15 production migrations shipped** without rollback to date.
- **406 backend test cases + 7 frontend tests, all green** — covering every Westgard rule, the signature chain, lot lifecycle transitions, and CORS/auth surfaces.
- **Monthly regulatory reports** that auditors accept without follow-up. The QR-code-verifiable signature chain has answered every "is this report authentic?" question on the spot.
- **Multi-analyst concurrent use** unlocked productivity — 3-4 analysts entering data in parallel during peak hours instead of queuing for one workstation.
- **Reagent traceability complete** — given any patient result, the lab can now produce the reagent lot, the QC measurement that validated the run, and the analyst who released it, in seconds.

## What I'd Do Differently

- **Move Westgard evaluation into a dedicated module earlier.** It started inside `QcService`; I extracted it later but a clean module from day one would have saved test churn.
- **Use Postgres-side triggers for reagent state transitions** instead of pure JPA logic. The state machine is correct, but enforcing it at the DB level would make me sleep better against future direct-SQL operators.
- **Adopt OpenAPI generator earlier** for frontend types. I hand-wrote DTO mirrors in TypeScript for too long.
- **Write the signature chain spec before the code.** I shipped V1 of reports, then refactored heavily to V2 when audit needs surfaced. A short ADR upfront would have steered V1 closer to the eventual design.

## Tech Stack Reference

| Layer | Technology |
|-------|-----------|
| Language | Java 21, TypeScript 5.9 |
| Backend framework | Spring Boot 3.3 |
| Frontend framework | React 18 + Vite |
| Database | PostgreSQL (Supabase) |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Auth | Spring Security + JWT (jjwt) |
| PDF | OpenPDF + JFreeChart |
| QR codes | ZXing |
| State / forms | TanStack Query, React Hook Form, Zod |
| Styling | Tailwind 4 |
| Charts | Recharts (web), JFreeChart (PDF) |
| Tests | JUnit 5, MockMvc, Vitest, Testing Library |
| Observability | Micrometer + Prometheus, logstash-logback |
| Deploy | Vercel (web) + Railway (api) + Supabase (db) |

## Want to talk?

If your domain is regulated (lab/clinical, finance, audit) and you need a senior full-stack engineer who can own a system end-to-end, I'm available. **otavio100206@gmail.com**.
