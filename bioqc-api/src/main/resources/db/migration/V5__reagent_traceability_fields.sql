-- V5: Rastreabilidade forte de Reagentes (Fase 3)
--
-- Adiciona campos que hoje moram apenas no campo "notes" ou em sistemas paralelos
-- (planilhas, etiquetas fisicas) e concentram a rastreabilidade real do lote:
--   - location: onde o lote esta armazenado fisicamente (ex: "Geladeira 2, Prateleira B")
--   - supplier: fornecedor (pode diferir do fabricante)
--   - received_date: data em que o lote chegou no laboratorio
--   - opened_date: data em que o lote foi aberto para uso. Diferente de start_date
--     (inicio programado de uso); opened_date marca o momento real de abertura do
--     frasco/caixa, referencia para estabilidade pos-abertura.
--
-- Todos os campos sao opcionais para nao quebrar lotes existentes. A obrigatoriedade
-- sera discutida em ADR separado caso o laboratorio queira tornar politica.

ALTER TABLE reagent_lots ADD COLUMN IF NOT EXISTS location VARCHAR(128);
ALTER TABLE reagent_lots ADD COLUMN IF NOT EXISTS supplier VARCHAR(128);
ALTER TABLE reagent_lots ADD COLUMN IF NOT EXISTS received_date DATE;
ALTER TABLE reagent_lots ADD COLUMN IF NOT EXISTS opened_date DATE;
