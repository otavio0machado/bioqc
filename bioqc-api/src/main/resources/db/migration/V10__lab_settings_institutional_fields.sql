-- V10: campos institucionais expandidos em lab_settings para cabecalho
-- completo dos laudos regulatorios (Reports V2).
--
-- Todos nullable para compat V1 (laboratorios que nao cadastraram os novos
-- campos ainda geram laudos — o LabHeaderRenderer exibe "nao cadastrado"
-- em vermelho para CNPJ e omite os demais).
--
-- Idempotente por construcao (ADD COLUMN IF NOT EXISTS).

ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS cnpj              VARCHAR(20);
ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS cnes              VARCHAR(20);
ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS registration_body VARCHAR(20);
ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS responsible_cpf   VARCHAR(20);
ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS technical_director_name   VARCHAR(200);
ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS technical_director_cpf    VARCHAR(20);
ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS technical_director_reg    VARCHAR(100);
ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS website           VARCHAR(200);
ALTER TABLE lab_settings ADD COLUMN IF NOT EXISTS sanitary_license  VARCHAR(50);
