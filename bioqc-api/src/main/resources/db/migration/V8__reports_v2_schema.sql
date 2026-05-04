-- V8: Infraestrutura Reports V2 (feature flag reports.v2.enabled).
--
-- Esta migration apenas adiciona colunas novas em report_runs (nullable)
-- e alinha o campo sha256 de report_audit_log com 64 chars. Nenhum dado
-- existente e alterado; V1 continua operando sem qualquer impacto.
--
-- Idempotente por construcao (ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS).
-- A migration so roda em Postgres (prod/staging); H2 local tem flyway.enabled=false
-- e o schema cresce por Hibernate ddl-auto=update.

-- ReportRun: colunas novas nullable para o fluxo V2
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS report_code      VARCHAR(64);
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS format           VARCHAR(16);
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS filters          TEXT;
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS storage_key      VARCHAR(512);
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS signed_by        UUID;
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS signed_at        TIMESTAMP WITH TIME ZONE;
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS signature_hash   VARCHAR(128);
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS expires_at       TIMESTAMP WITH TIME ZONE;
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS share_token      VARCHAR(64);
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS labels           TEXT;
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS page_count       INTEGER;
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS correlation_id   VARCHAR(64);
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS request_id       VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_report_runs_report_code ON report_runs(report_code);
CREATE INDEX IF NOT EXISTS idx_report_runs_status_created_at ON report_runs(status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_runs_share_token ON report_runs(share_token) WHERE share_token IS NOT NULL;

-- NOTA: em V3__report_configuration.sql a coluna ja foi criada como VARCHAR(64).
-- Este ALTER TYPE e defensivo para qualquer ambiente fora do versionamento Flyway.
ALTER TABLE report_audit_log ALTER COLUMN sha256 TYPE VARCHAR(64);
