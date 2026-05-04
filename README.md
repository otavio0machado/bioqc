<div align="center">

# BioQC — Lab Quality Control Platform

**Production-grade Quality Control (QC) system for clinical laboratories**
Java/Spring Boot · React/TypeScript · PostgreSQL · ISO 15189-aware

[Live Demo](#-live-demo) · [Case Study](./CASE_STUDY.md) · [Architecture](./docs/architecture.md) · [Hire Me](#-hire-me)

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-4169E1?logo=postgresql&logoColor=white)](https://supabase.com)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE.md)

</div>

---

> **TL;DR** — A real, production-deployed lab QC platform I designed and built end-to-end. Migrated a legacy Python system to a modern Java/React stack, with Westgard rule engine, signed PDF reports, full audit trail, and ISO 15189-aware traceability. **This repository is a sanitized showcase of the live product.**

## 🧪 What This Is

BioQC is the QC backbone of a clinical laboratory's daily operation. Lab analysts run control samples on every analyzer (biochemistry, hematology, immunology, etc.) before releasing patient results. BioQC validates those measurements against statistical reference values, applies Westgard multirules, flags violations, traces reagent lots, and produces digitally-signed PDF reports for regulatory audits.

**This is not a tutorial CRUD.** It runs in production at a real Brazilian clinical lab.

## ✨ Core Features

### Quality Control Engine
- **Westgard multirule evaluation** — `1-2s`, `1-3s`, `2-2s`, `R-4s`, `4-1s`, `10x` violation detection per measurement
- **Levey-Jennings charts** rendered server-side (JFreeChart) for printable reports
- **Statistical reference values** — mean, SD, CV with configurable tolerance per analyte/instrument/lot
- **Per-area QC** — independent QC parameters and rules per laboratory area (biochemistry, hematology, immunology, microbiology, parasitology, urinalysis)
- **Post-calibration tracking** — separate workflow for measurements taken after analyzer calibration
- **Daily QC history** propagated through the regulatory report bundle

### Reagent Traceability (ISO 15189)
- Reagent lot lifecycle: receipt → opening → consumption → end-of-use, with per-unit tracking
- Stock movements with reason codes (entry, consumption, expiry, archive)
- Automatic reclassification of expired/empty lots
- Manufacturer, supplier, storage location with combobox autocomplete
- Tracking labels (1 reagent = 1 traceability section in the regulatory report)

### Signed PDF Reports (Reports V2)
- **Hash-chained signature log** — each signed report references the previous signature, tamper-evident
- **QR code verification** — scan from PDF, hits `/verify/{hash}` to confirm authenticity
- **Report numbering sequence** with year-based prefixes
- **Download audit log** — every PDF download recorded for ISO 15189 evidence
- **Rate limiting** on signing endpoints (in-memory token bucket)
- **AI-generated commentary** (optional, Gemini API) summarizing month's QC trends in plain Portuguese

### Maintenance & Calibration
- Equipment maintenance scheduling and execution log
- Calibration records linked to QC measurements (delta CV tolerance configurable)

### Operations
- **JWT authentication** with refresh token rotation, secure HttpOnly cookies (`SameSite=None`)
- **RBAC** — granular permissions per resource and action
- **Audit log** — every mutation persisted with actor, timestamp, before/after
- **Password reset** flow via SMTP
- **Prometheus metrics** + structured JSON logging (logstash-logback)
- **Correlation IDs** propagated across requests
- **Flyway migrations** — 15 versioned schema changes shipped to production

## 📐 Architecture

```
┌──────────────────────┐     HTTPS      ┌──────────────────────┐
│  React 18 + Vite     │ ─────────────► │  Spring Boot 3.3     │
│  TanStack Query      │   (JWT + cookie│  Java 21             │
│  React Hook Form     │    refresh)    │  JPA / Hibernate     │
│  Zod validation      │ ◄───────────── │  Flyway migrations   │
│  Tailwind 4 + Recharts                │  Spring Security     │
└──────────────────────┘                └──────────┬───────────┘
       deployed to                                 │
       Vercel                                      │
                                                   ▼
                                        ┌──────────────────────┐
                                        │  PostgreSQL          │
                                        │  (Supabase)          │
                                        └──────────────────────┘
```

**Why this stack:** Spring Boot for the regulated, multi-actor backend (RBAC, audit, signing) where Java's type safety and ecosystem maturity pay off. React for the analyst-facing UI where developer velocity matters. Postgres because lab data is relational, evolves slowly, and audit queries need real SQL.

See [docs/architecture.md](./docs/architecture.md) for module-by-module breakdown.

## 📊 Project Footprint

| Metric | Value |
|--------|------:|
| Java source files | **282** |
| TypeScript/TSX files | **116** |
| Backend test cases (JUnit 5 + MockMvc) | **406** (across 51 files) |
| Frontend test cases (Vitest + Testing Library) | **7+** |
| Flyway migrations shipped | **15** |
| REST controllers | **15** |
| JPA entities | **30+** |
| Service classes | **20+** |

## 🚀 Live Demo

> **Try it →** [bioqc-demo.vercel.app](https://bioqc-demo.vercel.app) *(seed data only — no real patient or staff information)*
>
> **Demo credentials**
> - **Admin:** `admin@demo.bioqc.dev` / `Demo123!`
> - **Analyst:** `analyst@demo.bioqc.dev` / `Demo123!`

The demo is a separate deployment from the production system. Database is reseeded nightly.

## 🛠 Run Locally

### Prerequisites
- Java 21+
- Node.js 20+
- Docker (optional, for Postgres)

### Backend
```bash
cd bioqc-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# H2 in-memory DB, http://localhost:8080
# H2 console: http://localhost:8080/h2-console
```

### Frontend
```bash
cd bioqc-web
npm install
npm run dev
# http://localhost:5173
```

### Run the test suites
```bash
# Backend
cd bioqc-api && ./mvnw test

# Frontend
cd bioqc-web && npm test
```

## 📖 Documentation

- [Case Study](./CASE_STUDY.md) — situation, action, result, what I'd do differently
- [Architecture deep-dive](./docs/architecture.md)
- [Deployment runbook](./docs/deploy.md)

## 🎯 What This Project Demonstrates

For technical hiring managers and prospective clients, this codebase shows I can:

- **Migrate legacy systems safely** — incremental rollout, paridad-checking against the old Python system, zero-downtime cutover
- **Build for regulated domains** — auditability, tamper-evident logs, signed artifacts, traceability are first-class, not afterthoughts
- **Own end-to-end** — from Postgres schema → Spring Boot service layer → React UI → CI/CD → production observability
- **Write tests that matter** — domain-specific invariants (Westgard rules, signature chain integrity, lot lifecycle) covered with focused unit + integration tests
- **Ship to production and iterate** — 15 migrations and counting, deployed on real lab workflow

## 📞 Hire Me

I'm **Otavio Machado**, a full-stack engineer based in Brazil specializing in regulated, data-heavy domains (lab/healthcare, finance, compliance).

- 📧 **Email:** otavio100206@gmail.com
- 💼 **LinkedIn:** [linkedin.com/in/machado-otavio](https://www.linkedin.com/in/machado-otavio/)
- 🐙 **GitHub:** [@otavio0machado](https://github.com/otavio0machado)

**Available for:** Backend (Java/Spring, Node) · Frontend (React/TypeScript) · Full-stack migrations · Lab/healthcare informatics · ISO 15189 / LIS integrations.

---

## 📄 License & Disclosure

Released under [MIT License](./LICENSE.md). Source published with explicit authorization from the laboratory client. All identifying information (names, CNPJ, lot numbers, patient data, institutional branding) has been removed or replaced with fictitious equivalents. No real medical, financial, or personal data is present in this repository or its demo deployment.

🇧🇷 **Versão em português:** [README.pt-BR.md](./README.pt-BR.md)
