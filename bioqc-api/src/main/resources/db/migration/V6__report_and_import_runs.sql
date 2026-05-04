-- V6: Historico de execucao para Relatorios e Importacoes (Sprint D)
--
-- report_runs  — uma linha por PDF/CSV gerado pelos endpoints /api/reports/*.
-- import_runs  — uma linha por execucao de /api/qc-records/batch (modo partial
--                ou legacy); guarda contagens e motivo de falha para auditoria.

CREATE TABLE IF NOT EXISTS report_runs (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,           -- QC_PDF, REAGENTS_PDF
    area VARCHAR(64),
    period_type VARCHAR(16),             -- mes, trimestre, ano, null
    month INT,
    year INT,
    report_number VARCHAR(64),
    sha256 VARCHAR(128),
    size_bytes BIGINT,
    duration_ms BIGINT,
    status VARCHAR(16) NOT NULL,         -- SUCCESS, FAILURE
    error_message TEXT,
    user_id UUID,
    username VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_runs_created_at ON report_runs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_report_runs_type ON report_runs(type);

CREATE TABLE IF NOT EXISTS import_runs (
    id UUID PRIMARY KEY,
    source VARCHAR(32) NOT NULL,         -- QC_RECORDS
    mode VARCHAR(16) NOT NULL,           -- ATOMIC, PARTIAL
    total_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    failure_rows INT NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    status VARCHAR(16) NOT NULL,         -- SUCCESS, PARTIAL, FAILURE
    error_summary TEXT,
    user_id UUID,
    username VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_import_runs_created_at ON import_runs(created_at DESC);
