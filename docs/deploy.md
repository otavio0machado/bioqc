# Deploy Runbook

This document describes how the BioQC demo is deployed.

> **Architecture in one line:** Vercel hosts the React SPA, Railway runs the Spring Boot Docker image, Supabase provides managed Postgres. The SPA talks to the API over HTTPS with JWT in an HttpOnly cookie.

## Why this split

Vercel is excellent for SPAs (global CDN, zero-config previews) but does not run JVM workloads. The Spring Boot backend needs a long-running container with a healthcheck — Railway's Docker builder fits that exactly, and the project already ships a tested `Dockerfile` and `railway.toml`. Supabase gives a managed Postgres with branch databases, which makes migrations safe to rehearse before production.

If you prefer to host everything on Render or Fly.io, the same `Dockerfile` works. The split here is a recommendation, not a requirement.

## Prerequisites

- GitHub account with this repository forked or cloned to your account.
- Vercel account (free tier OK).
- Railway account (free tier — sleeps after inactivity, fine for a demo).
- Supabase account (free tier — 500MB DB, fine for a demo).

## Step 1 — Provision the database (Supabase)

1. Sign up at [supabase.com](https://supabase.com) and create a new project. Choose `sa-east-1` (São Paulo) for low-latency from Brazil.
2. Wait for provisioning (~2 minutes).
3. Settings → Database → copy the **JDBC connection string**. It looks like:
   ```
   jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require
   ```
4. Settings → Database → set a strong DB password and save it.

The Flyway migrations in `bioqc-api/src/main/resources/db/migration/` will run on first boot.

## Step 2 — Deploy the backend (Railway)

1. Sign up at [railway.app](https://railway.app).
2. **New Project** → **Deploy from GitHub** → select your fork → choose the `bioqc-api/` directory as the service root.
3. Railway auto-detects the `Dockerfile`. Confirm.
4. **Variables** — set the following (use Railway's "Raw editor" for bulk):

   ```bash
   SPRING_PROFILES_ACTIVE=prod
   SUPABASE_DB_URL=jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require
   SUPABASE_DB_USER=postgres
   SUPABASE_DB_PASSWORD=<the password from step 1>
   JWT_SECRET=<generate with: openssl rand -base64 64>
   FLYWAY_BASELINE_ON_MIGRATE=true
   AUTH_REFRESH_COOKIE_SECURE=true
   AUTH_REFRESH_COOKIE_SAME_SITE=None
   CORS_ALLOWED_ORIGINS=https://bioqc-demo.vercel.app
   PORT=8080
   ```

5. Deploy. Railway assigns a public URL like `bioqc-api-production.up.railway.app`.
6. Verify: `curl https://<your-railway-url>/actuator/health` should return `{"status":"UP"}`.

## Step 3 — Deploy the frontend (Vercel)

1. Sign up at [vercel.com](https://vercel.com).
2. **Add New** → **Project** → import your GitHub fork.
3. **Framework Preset**: Vite.
4. **Root Directory**: `bioqc-web`.
5. **Environment Variables**:

   ```bash
   VITE_API_BASE_URL=https://<your-railway-url>
   ```

6. Deploy. Vercel gives you a `.vercel.app` URL.

## Step 4 — Wire CORS

Once you know the Vercel URL, go back to Railway and update:

```bash
CORS_ALLOWED_ORIGINS=https://<your-vercel-url>.vercel.app
```

Redeploy the backend.

## Step 5 — Seed demo data

The repository ships a seed script at `bioqc-api/scripts/seed-demo.sql` that inserts:
- One demo lab (`Lab Demo BioQC`)
- One admin user (`admin@demo.bioqc.dev` / `Demo123!`)
- One analyst user (`analyst@demo.bioqc.dev` / `Demo123!`)
- ~30 days of fictitious QC measurements across 3 analytes
- 5 fictitious reagent lots
- 1 fictitious analyzer

Run it once against Supabase via the Supabase SQL editor or `psql`.

> **Important:** All demo data is fictitious. There are no real patient, staff, or laboratory identifiers anywhere in this repository or its demo deployment.

## Optional — Custom domain

- Vercel: Settings → Domains → add `demo.bioqc.dev` (or your domain) → follow DNS instructions.
- Railway: Settings → Networking → Custom Domain → add `api.bioqc.dev` → CNAME instructions.

Update `VITE_API_BASE_URL` and `CORS_ALLOWED_ORIGINS` accordingly.

## Troubleshooting

- **"Invalid CORS origin"** — `CORS_ALLOWED_ORIGINS` doesn't match the exact Vercel URL (including `https://`).
- **`500 Flyway` on first boot** — Forgot `FLYWAY_BASELINE_ON_MIGRATE=true`. Set it and redeploy.
- **Cookies not sent on auth** — Cross-origin needs `SameSite=None` and `Secure=true`. Both env vars must be set.
- **JWT secret too short** — Spring Security throws on boot. Use 64+ chars: `openssl rand -base64 64`.
