-- ============================================================================
-- V14_normalize_storage_temp_dry_run.sql
--
-- Script auxiliar (NAO Flyway-versionado, sem prefixo V14__) para validacao
-- manual antes de eventual deploy de uma futura V14__normalize_storage_temp.sql.
--
-- Origem: refator-reagentes-v2 G-02 (qa-review). Backend foi alinhado a partir
-- da fonte canonica do frontend (TEMPS em
--   bioqc-web/src/components/proin/reagentes/constants.ts):
--
--   '2-8°C'
--   '15-25°C (Ambiente)'
--   '-20°C'
--   '-80°C'
--
-- Hipotese operacional: como o frontend sempre foi a unica origem de escrita,
-- a coluna reagent_lots.storage_temp (VARCHAR 255) deve ja conter exclusivamente
-- estes literais. Este dry-run confirma a hipotese.
--
-- Decisao do release-engineer:
--   * Se cheque (B) retornar 0 linhas com formato legado/divergente -> V14 NAO
--     e necessaria. Documentar resultado em PR e arquivar.
--   * Se cheque (B) retornar > 0 linhas -> ABRIR PR com V14 derivada de (C),
--     revisar com domain-auditor antes do deploy (RDC ANVISA 302 / ISO 15189
--     pedem rastreabilidade — mudar literal historico precisa de audit_log).
-- ============================================================================

-- A) Distribuicao atual de storage_temp. Saida esperada: apenas os 4 literais
--    canonicos com seus respectivos counts (mais NULL, que e legitimo —
--    storage_temp nao e NOT NULL no schema atual).
SELECT
    storage_temp,
    COUNT(*) AS row_count
FROM reagent_lots
GROUP BY storage_temp
ORDER BY row_count DESC;

-- B) Detecta linhas em formato legado (sem °C ou Ambiente solto). Counts > 0
--    confirma necessidade de V14.
SELECT COUNT(*) AS legacy_format_rows
FROM reagent_lots
WHERE storage_temp IS NOT NULL
  AND storage_temp NOT IN ('2-8°C', '15-25°C (Ambiente)', '-20°C', '-80°C');

-- C) Sample de linhas em formato divergente (limite 50 para inspecao manual).
--    O release-engineer usa este resultado para construir o UPDATE em V14.
SELECT id, name, lot_number, manufacturer, storage_temp
FROM reagent_lots
WHERE storage_temp IS NOT NULL
  AND storage_temp NOT IN ('2-8°C', '15-25°C (Ambiente)', '-20°C', '-80°C')
ORDER BY id
LIMIT 50;

-- ============================================================================
-- Esboco da V14 (NAO ATIVAR sem (B) > 0 + revisao do domain-auditor):
--
--   BEGIN;
--   UPDATE reagent_lots SET storage_temp = '2-8°C'           WHERE storage_temp IN ('2-8C', '2-8 C', '2-8 °C');
--   UPDATE reagent_lots SET storage_temp = '-20°C'           WHERE storage_temp IN ('-20C', '-20 C', '-20 °C');
--   UPDATE reagent_lots SET storage_temp = '-80°C'           WHERE storage_temp IN ('-80C', '-80 C', '-80 °C');
--   UPDATE reagent_lots SET storage_temp = '15-25°C (Ambiente)' WHERE storage_temp IN ('Ambiente', 'ambiente', '15-25C', '15-25°C');
--   -- Para qualquer literal restante fora do conjunto, ABRIR issue antes de
--   -- mover para 'Geral' arbitrariamente — auditor externo precisa de
--   -- explicacao caso-a-caso (pode ser categoria nova legitima).
--   COMMIT;
--
-- Recomendacao de audit log (rastreabilidade RDC 302):
--   Para cada UPDATE acima, registrar entrada em audit_log com action
--   'REAGENT_STORAGE_TEMP_NORMALIZED' contendo from/to e migration_id='V14',
--   espelhando o que V13 faz para status (linha 56 do dry-run V13).
-- ============================================================================
