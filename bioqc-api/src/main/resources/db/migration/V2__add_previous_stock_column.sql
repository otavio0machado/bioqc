-- V2: Adiciona coluna previous_stock para rastreabilidade de ajustes de estoque
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS previous_stock DOUBLE PRECISION;
