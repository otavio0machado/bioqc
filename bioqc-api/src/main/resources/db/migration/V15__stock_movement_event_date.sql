-- ============================================================================
-- V15__stock_movement_event_date.sql
-- Refator-reagentes-v3.1 (incremental sobre V14):
--   1) ADD COLUMN event_date DATE NULL em stock_movements.
--      Semantica: data DECLARADA pelo operador para o evento real (abertura/fim
--      de uso da unidade), em contraste com `created_at` (timestamp do sistema
--      no momento do registro).
--   2) Justificativa de dominio:
--      - ABERTURA: hoje a data eh inferida (lot.opened_date = today se null).
--        v3.1 permite ao operador editar a data sugerida — ABERTURA pode estar
--        sendo registrada dias depois do evento real. event_date sincroniza
--        lot.opened_date na primeira abertura.
--      - CONSUMO ("Final de Uso" no UI): sem event_date hoje, so created_at do
--        movimento. v3.1 grava a data declarada do fim de uso da unidade —
--        rastreabilidade ANVISA RDC 302 art. 49 (controle de qualidade laboratorial:
--        data efetiva de uso/descarte da unidade reagente).
--   3) Compatibilidade retroativa: NAO altera linhas existentes. Movimentos
--      pre-V15 ficam com event_date IS NULL. Frontend interpreta NULL como
--      "use createdAt como fallback" — separa rastreio auditavel pre/pos v3.1.
--   4) Index parcial por event_date para consultas futuras (relatorios de
--      cronograma de uso, contagem de unidades abertas em janela X) sem onerar
--      tabelas com muito movimento legado NULL.
-- ============================================================================

BEGIN;

-- 1) Nova coluna nullable. NULL e estado valido (movimento pre-V15 ou request
--    sem event_date em CONSUMO/AJUSTE/ENTRADA/FECHAMENTO).
ALTER TABLE stock_movements
    ADD COLUMN event_date DATE NULL;

COMMENT ON COLUMN stock_movements.event_date IS
    'Data declarada pelo operador para o evento real (vs created_at do sistema). '
    'Usado em ABERTURA (sincroniza lot.opened_date na primeira abertura) e '
    'CONSUMO/Final de Uso (rastreabilidade ANVISA RDC 302 art. 49). '
    'NULL em movimentos pre-V15 (frontend usa createdAt como fallback).';

-- 2) Index parcial — somente linhas com event_date preenchido.
CREATE INDEX IF NOT EXISTS idx_stock_movements_event_date
    ON stock_movements(event_date) WHERE event_date IS NOT NULL;

COMMIT;
