-- V9: cadeia de custodia imutavel para Reports V2.
--
-- Duas mudancas relacionadas ao fluxo /sign:
--  1. report_runs.signed_storage_key: separa o artefato original (storage_key,
--     hash = sha256) da versao assinada (signed_storage_key, hash = signature_hash).
--     Isto mantem a cadeia de custodia intacta — o PDF original continua acessivel
--     e seu sha256 permanece valido, enquanto a versao assinada e um artefato
--     adicional e independente.
--
--  2. report_signature_log: tabela append-only que grava uma linha por assinatura.
--     Nenhum UPDATE/DELETE deve ser feito nela (repository expoe apenas save/find).
--     Este e o registro de cadeia de custodia juridica — sobrevive a qualquer
--     expiracao ou soft-delete do report_runs.
--
-- Idempotente por construcao (IF NOT EXISTS).
-- Roda apenas em Postgres (prod/staging); H2 local usa ddl-auto=update.

-- Ressalva 1: storage da versao assinada separado da versao original.
ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS signed_storage_key VARCHAR(512);

-- Ressalva 2: cadeia imutavel de assinaturas.
CREATE TABLE IF NOT EXISTS report_signature_log (
    id UUID PRIMARY KEY,
    report_run_id UUID NOT NULL REFERENCES report_runs(id),
    report_number VARCHAR(30) NOT NULL,
    original_sha256 VARCHAR(64) NOT NULL,
    signature_hash VARCHAR(64) NOT NULL,
    signed_by_user_id UUID NOT NULL,
    signed_by_name VARCHAR(255) NOT NULL,
    signer_registration VARCHAR(100) NOT NULL,
    signed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signed_storage_key VARCHAR(512) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_report_signature_log_run ON report_signature_log(report_run_id);
CREATE INDEX IF NOT EXISTS idx_report_signature_log_signed_at ON report_signature_log(signed_at DESC);
CREATE INDEX IF NOT EXISTS idx_report_signature_log_signature_hash ON report_signature_log(signature_hash);
CREATE INDEX IF NOT EXISTS idx_report_signature_log_original_sha256 ON report_signature_log(original_sha256);
