-- V12: Persistencia de avisos do Reports V2.
--
-- Warnings documentam geracoes completas com ressalva operacional, como pacote
-- regulatorio parcial ou ausencia de dados no periodo. Antes desta migration,
-- esses avisos existiam apenas no response imediato de /generate e sumiam do
-- historico.

ALTER TABLE report_runs ADD COLUMN IF NOT EXISTS warnings TEXT;

CREATE INDEX IF NOT EXISTS idx_report_runs_v2_created_at
    ON report_runs(created_at DESC)
    WHERE report_code IS NOT NULL;
