-- ============================================================================
-- V14__reagent_units_and_archive_v3.sql
-- Refator-reagentes-v3 (PR consolidado):
--   1) Audit transicao 'fora_de_estoque' -> 'inativo' antes do UPDATE.
--   2) ADD colunas (units_in_stock, units_in_use, archived_at, archived_by,
--      needs_stock_review) em reagent_lots.
--   3) ADD colunas (previous_units_in_stock, previous_units_in_use) em stock_movements.
--   4) DROP CHECK constraint antigo de status (que ainda contem 'fora_de_estoque').
--   5) UPDATE deterministico:
--      - em_estoque  -> units_in_stock = COALESCE(current_stock,0); units_in_use=0
--      - em_uso      -> units_in_stock = COALESCE(current_stock,0); units_in_use=0;
--                       needs_stock_review = TRUE
--      - fora_de_estoque -> units_in_stock=0; units_in_use=0; status='inativo';
--                           archived_at=CURRENT_DATE; archived_by='sistema-migracao-v14'
--      - vencido     -> units_in_stock = COALESCE(current_stock,0); units_in_use=0
--   6) ADD CHECK constraint novo (status IN ('em_estoque','em_uso','vencido','inativo')).
--   7) ADD CHECK constraints (units_in_stock >= 0, units_in_use >= 0).
--   8) DROP COLUMN current_stock (sem deprecated period — segue precedente V13).
--   9) Indices novos (parciais para archived_at e needs_stock_review).
--   10) ALTER stock_movements check constraint do tipo (aceita ABERTURA/FECHAMENTO/CONSUMO).
--
-- Atencao a invariantes (audit ressalva A — INTRA-PR):
--   - audit_log historico (com details->>'to' = 'fora_de_estoque' / 'inativo' pre-v2)
--     PRESERVADO. V14 apenas INSERE registros novos com trigger='migration_v14'.
--   - audit_log.details usa tipo `json` (nao `jsonb`). Usamos json_build_object.
--   - Ordem cruel (audit ressalva A): passo 1 (INSERT audit) precede passo 5 (UPDATE)
--     para que `r.status` ainda contenha o valor antigo. DROP CHECK status antigo
--     (passo 4) precede UPDATE para `'inativo'` (passo 5) — senao quebra contra CHECK
--     antigo (que so aceita 'em_estoque','em_uso','fora_de_estoque','vencido').
--   - DROP COLUMN adquire AccessExclusiveLock — janela noturna recomendada.
-- ============================================================================

BEGIN;

-- 1) Audit log da transicao 'fora_de_estoque' -> 'inativo' ANTES do UPDATE.
--    Lotes que NAO mudam de status (em_estoque, em_uso, vencido) NAO geram audit
--    por linha — apenas a transicao de SCHEMA (drop current_stock + add units_*) e
--    documentada no release notes.
INSERT INTO audit_log (id, user_id, action, entity_type, entity_id, details, created_at)
SELECT
    gen_random_uuid(),
    NULL,
    'REAGENT_STATUS_TRANSITION_V3',
    'ReagentLot',
    r.id,
    json_build_object(
        'from', r.status,
        'to', 'inativo',
        'trigger', 'migration_v14',
        'fromCurrentStock', COALESCE(r.current_stock, 0)::text,
        'toUnitsInStock', '0',
        'toUnitsInUse', '0',
        'expiryDate', r.expiry_date::text
    ),
    NOW()
FROM reagent_lots r
WHERE r.status = 'fora_de_estoque';

-- 2) ADD COLUMN em reagent_lots (com DEFAULT para popular linhas existentes).
ALTER TABLE reagent_lots
    ADD COLUMN units_in_stock INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN units_in_use INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN archived_at DATE NULL,
    ADD COLUMN archived_by VARCHAR(128) NULL,
    ADD COLUMN needs_stock_review BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) ADD COLUMN em stock_movements (NULLABLE — movimentos pre-V14 ficam NULL).
ALTER TABLE stock_movements
    ADD COLUMN previous_units_in_stock INTEGER NULL,
    ADD COLUMN previous_units_in_use INTEGER NULL;

-- 4) DROP CHECK constraint antigo de status (V13: aceitava 'fora_de_estoque').
--    Tem que vir ANTES do UPDATE para 'inativo' (audit ressalva A — reordenacao).
ALTER TABLE reagent_lots DROP CONSTRAINT chk_reagent_lots_status;

-- 5) UPDATE deterministico aplicando regras do contrato §3.3.
UPDATE reagent_lots SET
    units_in_stock = CASE
        WHEN status = 'em_estoque'      THEN COALESCE(current_stock, 0)::INTEGER
        WHEN status = 'em_uso'          THEN COALESCE(current_stock, 0)::INTEGER
        WHEN status = 'fora_de_estoque' THEN 0
        WHEN status = 'vencido'         THEN COALESCE(current_stock, 0)::INTEGER
        ELSE 0
    END,
    units_in_use = 0,
    needs_stock_review = CASE
        WHEN status = 'em_uso' THEN TRUE
        ELSE FALSE
    END,
    archived_at = CASE
        WHEN status = 'fora_de_estoque' THEN CURRENT_DATE
        ELSE NULL
    END,
    archived_by = CASE
        WHEN status = 'fora_de_estoque' THEN 'sistema-migracao-v14'
        ELSE NULL
    END,
    status = CASE
        WHEN status = 'fora_de_estoque' THEN 'inativo'
        ELSE status
    END
WHERE status IN ('em_estoque','em_uso','fora_de_estoque','vencido');

-- 6) ADD CHECK constraint novo (status v3).
ALTER TABLE reagent_lots
    ADD CONSTRAINT chk_reagent_lots_status
    CHECK (status IN ('em_estoque','em_uso','vencido','inativo'));

-- 7) ADD CHECK constraints de unidades nao-negativas (defesa em profundidade).
ALTER TABLE reagent_lots
    ADD CONSTRAINT chk_reagent_lots_units_in_stock_nonneg
    CHECK (units_in_stock >= 0);
ALTER TABLE reagent_lots
    ADD CONSTRAINT chk_reagent_lots_units_in_use_nonneg
    CHECK (units_in_use >= 0);

-- 8) DROP COLUMN current_stock (sem deprecated period — segue precedente V13).
ALTER TABLE reagent_lots DROP COLUMN current_stock;

-- 9) Indices novos (parciais).
CREATE INDEX IF NOT EXISTS idx_reagent_lots_archived_at
    ON reagent_lots(archived_at) WHERE archived_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_reagent_lots_needs_stock_review
    ON reagent_lots(needs_stock_review) WHERE needs_stock_review = TRUE;

-- 10) Atualiza CHECK constraint do type em stock_movements para aceitar os 6 valores.
--     Como nao havia constraint anterior em stock_movements.type (V1 baseline nao
--     definiu), DROP IF EXISTS apenas como defesa contra ambientes com hotfix manual.
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_stock_movements_type;
ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_type
    CHECK (type IN ('ENTRADA','SAIDA','AJUSTE','ABERTURA','FECHAMENTO','CONSUMO'));

COMMIT;
