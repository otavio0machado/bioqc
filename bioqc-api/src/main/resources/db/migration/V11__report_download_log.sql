-- V11: auditoria de downloads de relatorios V2.
--
-- Motivacao regulatoria: ISO 15189:2022 item 8.4.1 exige rastreabilidade de
-- distribuicao de documentos (quem baixou, quando, qual artefato). Ate aqui
-- `ReportServiceV2.download()` nao deixava rastro — um auditor externo nao
-- consegue provar quem teve acesso ao laudo pos-assinatura.
--
-- Tabela append-only. Soft-deletion NAO e suportada (auditoria e imutavel
-- por construcao).
--
-- Idempotente: CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS report_download_log (
    id                   UUID NOT NULL,
    report_run_id        UUID NOT NULL REFERENCES report_runs(id),
    report_number        VARCHAR(30) NOT NULL,
    sha256_served        VARCHAR(64) NOT NULL,
    version_served       VARCHAR(16) NOT NULL,  -- 'original' ou 'signed'
    size_bytes           BIGINT NOT NULL,
    downloaded_by_user_id UUID,                 -- null quando nao autenticado (link publico)
    downloaded_by_name   VARCHAR(255),
    downloaded_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_ip            VARCHAR(45),           -- IPv4 ou IPv6
    user_agent           VARCHAR(500),
    correlation_id       VARCHAR(64),
    CONSTRAINT pk_report_download_log PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_report_download_log_run
    ON report_download_log(report_run_id);
CREATE INDEX IF NOT EXISTS idx_report_download_log_at
    ON report_download_log(downloaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_report_download_log_user
    ON report_download_log(downloaded_by_user_id);
CREATE INDEX IF NOT EXISTS idx_report_download_log_hash
    ON report_download_log(sha256_served);
