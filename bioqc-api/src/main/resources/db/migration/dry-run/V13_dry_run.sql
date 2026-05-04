-- ============================================================================
-- V13_dry_run.sql
--
-- Script auxiliar (NAO Flyway-versionado) para validacao manual antes do deploy
-- de V13__reagent_status_v2.sql. O release-engineer roda em staging com pg_dump
-- recente da producao restaurado e revisa as 3 saidas; counts > 0 nos checks
-- aborta deploy.
--
-- Equivalencia operacional dos status legados (audit ressalva 1.6):
--   inativo (legado) = vencido AND stock=0 (vigente)
--                      UNION fora_de_estoque (vigente sem CQ recente)
-- Auditores externos que consultem audit_log historico com filtro literal
-- to='inativo' nao encontrarao lotes pos-V13 — usar a uniao acima como semantica
-- equivalente.
-- ============================================================================

-- A) Distribuicao pre/pos por par (current_status, target_status). Espelha o CASE
--    final usado em V13. Saida esperada: numero de combinacoes finito, sem 'UNKNOWN'.
SELECT
    r.status AS current_status,
    COUNT(*) AS row_count,
    CASE
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
        ELSE 'UNKNOWN'
    END AS target_status
FROM reagent_lots r
GROUP BY current_status, target_status
ORDER BY current_status, target_status;

-- B) Pre-condicao NOT NULL: V13 promove manufacturer e expiry_date para NOT NULL.
--    Se algum count > 0, ABORTAR o deploy e abrir issue para corrigir os lotes.
SELECT COUNT(*) AS reagents_without_manufacturer
FROM reagent_lots
WHERE manufacturer IS NULL;

SELECT COUNT(*) AS reagents_without_expiry
FROM reagent_lots
WHERE expiry_date IS NULL;

-- C) Detecta linhas que cairiam em 'UNKNOWN' apos a derivacao (status fora do dominio
--    legado e novo). Bloqueador: se count > 0, abrir issue para o domain-auditor
--    decidir o destino dessas linhas antes do deploy.
WITH derived AS (
    SELECT
        r.id,
        CASE
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
            ELSE 'UNKNOWN'
        END AS target_status
    FROM reagent_lots r
)
SELECT COUNT(*) AS rows_with_unknown_target
FROM derived
WHERE target_status = 'UNKNOWN';

-- ============================================================================
-- Recomendacao de rollback (audit ressalva 9.1): V13 NAO e reversivel
-- trivialmente (DROP COLUMN destroi dado). Em caso de necessidade:
--   1) pg_restore do backup pre-deploy
--   2) Revert do PR-1 (codigo Java referenciando enums novos)
--   3) Re-aplicar V1-V12 a partir do dump
-- Comando de backup recomendado (executar antes do deploy):
--   pg_dump -F c -f bioqc-pre-v13-$(date +%Y%m%d).dump <DSN>
-- ============================================================================
