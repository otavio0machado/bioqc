-- ============================================================================
-- V14_dry_run.sql
-- Diagnostico pre-deploy do refator-reagentes-v3 (V14__reagent_units_and_archive_v3.sql).
--
-- Bloqueadores de deploy (release-engineer aborta se):
--   B) reagents_with_null_stock > 0 OU reagents_with_negative_stock > 0
--   C) reagents_unknown_status > 0
--   D) Algum stock_movements.type fora de
--      {ENTRADA, SAIDA, AJUSTE, ABERTURA, FECHAMENTO, CONSUMO}
--
-- Padrao: BEGIN/ROLLBACK para nao mutar nada — apenas SELECTs diagnosticos.
-- Rodar em snapshot RECENTE de producao (audit ressalva A — V14_normalize precedente).
-- ============================================================================

BEGIN;

-- A) Distribuicao por status -> destino V14.
SELECT
    r.status AS current_status,
    COUNT(*) AS row_count,
    CASE
        WHEN r.status = 'em_estoque'      THEN 'em_estoque'
        WHEN r.status = 'em_uso'          THEN 'em_uso'
        WHEN r.status = 'fora_de_estoque' THEN 'inativo'
        WHEN r.status = 'vencido'         THEN 'vencido'
        ELSE 'UNKNOWN'
    END AS target_status,
    SUM(CASE WHEN r.status = 'em_uso' THEN 1 ELSE 0 END) AS will_have_needs_stock_review_true
FROM reagent_lots r
GROUP BY r.status
ORDER BY r.status;

-- B) Reagentes com current_stock NULL ou negativo (bloqueador).
SELECT COUNT(*) AS reagents_with_null_stock
FROM reagent_lots WHERE current_stock IS NULL;

SELECT COUNT(*) AS reagents_with_negative_stock
FROM reagent_lots WHERE current_stock < 0;

-- C) Reagentes com status fora do dominio v2 (bloqueador — V13 deveria ter cuidado).
SELECT COUNT(*) AS reagents_unknown_status
FROM reagent_lots
WHERE status NOT IN ('em_estoque','em_uso','fora_de_estoque','vencido');

-- D) Stock_movements: distribuicao por type (deve estar no conjunto v3 + SAIDA legacy).
SELECT type, COUNT(*) AS movement_count
FROM stock_movements
GROUP BY type
ORDER BY type;

-- E) Snapshot de quantos lotes terao archived_at='sistema-migracao-v14' apos V14.
SELECT COUNT(*) AS reagents_will_be_archived_by_migration
FROM reagent_lots WHERE status = 'fora_de_estoque';

-- F) Snapshot de quantos lotes terao needs_stock_review=TRUE apos V14.
SELECT COUNT(*) AS reagents_will_need_stock_review
FROM reagent_lots WHERE status = 'em_uso';

ROLLBACK;
