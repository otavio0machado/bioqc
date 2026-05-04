-- V4: Adiciona coluna reason em stock_movements para auditoria de ajustes e saidas que zeram estoque.
-- Campo novo em Fase 2 da rastreabilidade de Reagentes; valores validos sao mantidos na aplicacao
-- (MovementReason.java) para manter o contrato flexivel.
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS reason VARCHAR(32);
