-- V7: Reclassifica lotes vencidos com estoque zerado para 'inativo'.
--
-- Contexto: a regra de derivacao de status (vencido vs inativo) passou a separar
-- RISCO operacional (vencido = tem estoque, precisa descartar) de HISTORICO
-- (inativo = acabou e passou da validade). Lotes ja marcados 'vencido' cujo
-- estoque esta em 0 (ou NULL) devem migrar para 'inativo' uma unica vez para
-- refletir a nova semantica. O scheduler diario cuida do fluxo continuo dai em diante.
--
-- Idempotente por construcao: UPDATE com filtro condicional. Se rodar uma segunda vez
-- nao altera linhas ja classificadas como 'inativo' (ja foram removidas do predicado).
-- 'quarentena' e preservado (estado manual de excecao) — nao e alvo deste UPDATE.

UPDATE reagent_lots
   SET status = 'inativo'
 WHERE status = 'vencido'
   AND (current_stock IS NULL OR current_stock <= 0);
