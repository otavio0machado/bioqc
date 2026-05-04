-- ============================================================================
-- V13__reagent_status_v2.sql
-- Refator-reagentes-v2 (PR-1):
--   1) Audita transicoes de status legados antes do UPDATE.
--   2) UPDATE deterministico aplicando regra ternaria ao dominio novo
--      (em_estoque, em_uso, fora_de_estoque, vencido).
--   3) DROP COLUMN das 6 colunas obsoletas (quantity_value, stock_unit,
--      estimated_consumption, start_date, end_date, alert_threshold_days).
--   4) Promove manufacturer e expiry_date para NOT NULL.
--   5) Adiciona CHECK constraint do dominio do status.
--   6) Atualiza DEFAULT do status para 'em_estoque'.
--   7) Cria 3 indices novos (status, expiry_date, name).
--
-- Atencao a invariantes:
--   - audit_log historico (com details->>'to' = 'inativo' etc) e PRESERVADO.
--     V13 apenas INSERE registros novos com trigger='quarentena_removed_v2'.
--   - audit_log.details usa tipo `json` (nao `jsonb`). Usamos json_build_object
--     em vez de jsonb_build_object (V1__baseline_schema.sql:46, AuditLog.java:51).
--   - Ordem cruel: passo 1 (INSERT audit_log) precede passo 2 (UPDATE) para
--     que `r.status` ainda contenha o valor antigo no momento do INSERT.
--   - DROP COLUMN adquire AccessExclusiveLock — janela noturna recomendada.
-- ============================================================================

BEGIN;

-- 1) Audit log das transicoes ANTES do UPDATE.
--    O CASE no SELECT deve ser IDENTICO ao do UPDATE no passo 2 — qualquer drift
--    entre eles produz audit incorreto. WHERE final filtra no-ops.
INSERT INTO audit_log (id, user_id, action, entity_type, entity_id, details, created_at)
SELECT
    gen_random_uuid(),
    NULL,
    'REAGENT_STATUS_DERIVED',
    'ReagentLot',
    r.id,
    json_build_object(
        'from', r.status,
        'to', CASE
            WHEN r.expiry_date < CURRENT_DATE THEN 'vencido'
            WHEN r.status = 'quarentena' AND COALESCE(r.current_stock, 0) = 0 THEN 'fora_de_estoque'
            WHEN r.status = 'quarentena' AND r.current_stock > 0 THEN 'em_uso'
            WHEN r.status = 'inativo' AND COALESCE(r.current_stock, 0) = 0 THEN 'fora_de_estoque'
            WHEN r.status = 'inativo' AND r.current_stock > 0 THEN 'em_uso'
            WHEN r.status = 'ativo' AND COALESCE(r.current_stock, 0) > 0 AND r.opened_date IS NULL THEN 'em_estoque'
            WHEN r.status = 'ativo' AND COALESCE(r.current_stock, 0) > 0 AND r.opened_date IS NOT NULL THEN 'em_uso'
            WHEN r.status = 'ativo' AND COALESCE(r.current_stock, 0) = 0 THEN 'fora_de_estoque'
            WHEN r.status = 'em_uso' THEN 'em_uso'
            WHEN r.status = 'vencido' THEN 'vencido'
            ELSE r.status
        END,
        'trigger', 'quarentena_removed_v2',
        'expiryDate', r.expiry_date::text,
        'currentStock', COALESCE(r.current_stock, 0)::text
    ),
    NOW()
FROM reagent_lots r
WHERE
    -- Filtra apenas linhas com status legado E que mudam de fato (no-op nao gera audit).
    r.status IN ('ativo', 'em_uso', 'inativo', 'vencido', 'quarentena')
    AND r.status <> CASE
        WHEN r.expiry_date < CURRENT_DATE THEN 'vencido'
        WHEN r.status = 'quarentena' AND COALESCE(r.current_stock, 0) = 0 THEN 'fora_de_estoque'
        WHEN r.status = 'quarentena' AND r.current_stock > 0 THEN 'em_uso'
        WHEN r.status = 'inativo' AND COALESCE(r.current_stock, 0) = 0 THEN 'fora_de_estoque'
        WHEN r.status = 'inativo' AND r.current_stock > 0 THEN 'em_uso'
        WHEN r.status = 'ativo' AND COALESCE(r.current_stock, 0) > 0 AND r.opened_date IS NULL THEN 'em_estoque'
        WHEN r.status = 'ativo' AND COALESCE(r.current_stock, 0) > 0 AND r.opened_date IS NOT NULL THEN 'em_uso'
        WHEN r.status = 'ativo' AND COALESCE(r.current_stock, 0) = 0 THEN 'fora_de_estoque'
        WHEN r.status = 'em_uso' THEN 'em_uso'
        WHEN r.status = 'vencido' THEN 'vencido'
        ELSE r.status
    END;

-- 2) UPDATE deterministico aplicando o mesmo CASE.
UPDATE reagent_lots
SET status = CASE
    WHEN expiry_date < CURRENT_DATE THEN 'vencido'
    WHEN status = 'quarentena' AND COALESCE(current_stock, 0) = 0 THEN 'fora_de_estoque'
    WHEN status = 'quarentena' AND current_stock > 0 THEN 'em_uso'
    WHEN status = 'inativo' AND COALESCE(current_stock, 0) = 0 THEN 'fora_de_estoque'
    WHEN status = 'inativo' AND current_stock > 0 THEN 'em_uso'
    WHEN status = 'ativo' AND COALESCE(current_stock, 0) > 0 AND opened_date IS NULL THEN 'em_estoque'
    WHEN status = 'ativo' AND COALESCE(current_stock, 0) > 0 AND opened_date IS NOT NULL THEN 'em_uso'
    WHEN status = 'ativo' AND COALESCE(current_stock, 0) = 0 THEN 'fora_de_estoque'
    WHEN status = 'em_uso' THEN 'em_uso'
    WHEN status = 'vencido' THEN 'vencido'
    ELSE status
END
WHERE status IN ('ativo', 'em_uso', 'inativo', 'vencido', 'quarentena');

-- 3) DROP COLUMN das 6 colunas obsoletas.
ALTER TABLE reagent_lots
    DROP COLUMN IF EXISTS quantity_value,
    DROP COLUMN IF EXISTS stock_unit,
    DROP COLUMN IF EXISTS estimated_consumption,
    DROP COLUMN IF EXISTS start_date,
    DROP COLUMN IF EXISTS end_date,
    DROP COLUMN IF EXISTS alert_threshold_days;

-- 4) Promove manufacturer e expiry_date para NOT NULL.
--    Pre-condicao validada pelo dry-run: nao ha linhas com NULL em nenhuma das duas.
ALTER TABLE reagent_lots ALTER COLUMN manufacturer SET NOT NULL;
ALTER TABLE reagent_lots ALTER COLUMN expiry_date SET NOT NULL;

-- 5) CHECK constraint do dominio do status. Aplicada APOS o UPDATE para nao
--    rejeitar linhas legadas durante a propria migracao.
ALTER TABLE reagent_lots
    ADD CONSTRAINT chk_reagent_lots_status
    CHECK (status IN ('em_estoque', 'em_uso', 'fora_de_estoque', 'vencido'));

-- 6) Default novo do status.
ALTER TABLE reagent_lots ALTER COLUMN status SET DEFAULT 'em_estoque';

-- 7) Indices novos.
CREATE INDEX IF NOT EXISTS idx_reagent_lots_status ON reagent_lots(status);
CREATE INDEX IF NOT EXISTS idx_reagent_lots_expiry_date ON reagent_lots(expiry_date);
CREATE INDEX IF NOT EXISTS idx_reagent_lots_name ON reagent_lots(name);

COMMIT;
