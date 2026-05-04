-- ============================================================
-- V3: Configuracao do laboratorio + numeracao de laudos
-- ============================================================

-- 1) Dados fixos do laboratorio que entram na capa do PDF
CREATE TABLE IF NOT EXISTS lab_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_name VARCHAR(200) NOT NULL DEFAULT '',
    responsible_name VARCHAR(200) NOT NULL DEFAULT '',
    responsible_registration VARCHAR(100) NOT NULL DEFAULT '',
    address VARCHAR(300) NOT NULL DEFAULT '',
    phone VARCHAR(50) NOT NULL DEFAULT '',
    email VARCHAR(200) NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID
);

-- Linha singleton — so existe uma configuracao ativa
INSERT INTO lab_settings (id, lab_name, responsible_name, responsible_registration, address, phone, email)
SELECT gen_random_uuid(), '', '', '', '', '', ''
WHERE NOT EXISTS (SELECT 1 FROM lab_settings);

-- 2) Lista de emails que recebem relatorios (ADMIN/RESPONSAVEL)
CREATE TABLE IF NOT EXISTS lab_report_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(200) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3) Audit log de relatorios gerados (numeracao sequencial + hash)
CREATE TABLE IF NOT EXISTS report_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_number VARCHAR(30) NOT NULL UNIQUE,
    area VARCHAR(50),
    format VARCHAR(10) NOT NULL,
    period_label VARCHAR(100),
    sha256 VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    generated_by UUID,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_report_audit_log_generated_at ON report_audit_log (generated_at DESC);

-- 4) Contador por competencia (AAAAMM) para gerar "BIO-AAAAMM-NNNNNN"
CREATE TABLE IF NOT EXISTS report_sequence (
    period_key VARCHAR(6) PRIMARY KEY,
    last_value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
