-- ============================================================================
-- V1__baseline_schema.sql
-- Baseline schema for BioQC API
-- Generated from JPA entities - all PKs are UUID, no sequences used
-- Keep baseline DDL idempotent so Flyway can adopt pre-provisioned schemas
-- without requiring manual edits to flyway_schema_history.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- users (entity: User)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              UUID            NOT NULL,
    username        VARCHAR(255)    NOT NULL,
    email           VARCHAR(255),
    password_hash   VARCHAR(255)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    role            VARCHAR(255)    NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
);

-- ---------------------------------------------------------------------------
-- user_permissions (ElementCollection for User.permissions)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id     UUID            NOT NULL,
    permission  VARCHAR(255)    NOT NULL,
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- audit_log (entity: AuditLog)
-- details column uses json type (NOT jsonb) per entity annotation
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id          UUID            NOT NULL,
    user_id     UUID,
    action      VARCHAR(255)    NOT NULL,
    entity_type VARCHAR(255)    NOT NULL,
    entity_id   UUID,
    details     json,
    ip_address  VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id)
        REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- refresh_token_sessions (entity: RefreshTokenSession)
-- 4 named indexes per @Index annotations
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_token_sessions (
    id                      UUID            NOT NULL,
    user_id                 UUID            NOT NULL,
    token_id                UUID            NOT NULL,
    family_id               UUID            NOT NULL,
    rotated_from_token_id   UUID,
    token_hash              VARCHAR(64)     NOT NULL,
    expires_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at            TIMESTAMP WITH TIME ZONE,
    revoked_at              TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_token_sessions PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_sessions_token_id UNIQUE (token_id),
    CONSTRAINT uk_refresh_token_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_sessions_user FOREIGN KEY (user_id)
        REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_sessions_user ON refresh_token_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_sessions_family ON refresh_token_sessions (family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_sessions_expires ON refresh_token_sessions (expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_token_sessions_revoked ON refresh_token_sessions (revoked_at);

-- ---------------------------------------------------------------------------
-- password_reset_tokens (entity: PasswordResetToken)
-- token column: length=120, unique=true
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          UUID            NOT NULL,
    user_id     UUID            NOT NULL,
    token       VARCHAR(120)    NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at     TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_token UNIQUE (token),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- qc_exams (entity: QcExam)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS qc_exams (
    id          UUID            NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    area        VARCHAR(255)    NOT NULL DEFAULT 'bioquimica',
    unit        VARCHAR(255),
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_qc_exams PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- qc_reference_values (entity: QcReferenceValue)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS qc_reference_values (
    id                UUID                NOT NULL,
    exam_id           UUID                NOT NULL,
    name              VARCHAR(255)        NOT NULL,
    level             VARCHAR(255)        NOT NULL DEFAULT 'Normal',
    lot_number        VARCHAR(255),
    manufacturer      VARCHAR(255),
    target_value      DOUBLE PRECISION    NOT NULL DEFAULT 0,
    target_sd         DOUBLE PRECISION    NOT NULL DEFAULT 0,
    cv_max_threshold  DOUBLE PRECISION    NOT NULL DEFAULT 10,
    valid_from        DATE,
    valid_until       DATE,
    is_active         BOOLEAN             NOT NULL DEFAULT TRUE,
    notes             TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_qc_reference_values PRIMARY KEY (id),
    CONSTRAINT fk_qc_reference_values_exam FOREIGN KEY (exam_id)
        REFERENCES qc_exams (id)
);

-- ---------------------------------------------------------------------------
-- qc_records (entity: QcRecord)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS qc_records (
    id                UUID                NOT NULL,
    reference_id      UUID,
    exam_name         VARCHAR(255)        NOT NULL,
    area              VARCHAR(255)        NOT NULL DEFAULT 'bioquimica',
    date              DATE                NOT NULL,
    level             VARCHAR(255),
    lot_number        VARCHAR(255),
    value             DOUBLE PRECISION    NOT NULL,
    target_value      DOUBLE PRECISION    NOT NULL DEFAULT 0,
    target_sd         DOUBLE PRECISION    NOT NULL DEFAULT 0,
    cv                DOUBLE PRECISION    DEFAULT 0,
    cv_limit          DOUBLE PRECISION    DEFAULT 10,
    z_score           DOUBLE PRECISION    DEFAULT 0,
    equipment         VARCHAR(255),
    analyst           VARCHAR(255),
    status            VARCHAR(255)        NOT NULL DEFAULT 'APROVADO',
    needs_calibration BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_qc_records PRIMARY KEY (id),
    CONSTRAINT fk_qc_records_reference FOREIGN KEY (reference_id)
        REFERENCES qc_reference_values (id)
);

-- ---------------------------------------------------------------------------
-- westgard_violations (entity: WestgardViolation)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS westgard_violations (
    id              UUID            NOT NULL,
    qc_record_id    UUID            NOT NULL,
    rule            VARCHAR(255)    NOT NULL,
    description     TEXT            NOT NULL,
    severity        VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_westgard_violations PRIMARY KEY (id),
    CONSTRAINT fk_westgard_violations_qc_record FOREIGN KEY (qc_record_id)
        REFERENCES qc_records (id)
);

-- ---------------------------------------------------------------------------
-- post_calibration_records (entity: PostCalibrationRecord)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS post_calibration_records (
    id                       UUID                NOT NULL,
    qc_record_id             UUID                NOT NULL,
    date                     DATE                NOT NULL,
    exam_name                VARCHAR(255)        NOT NULL,
    original_value           DOUBLE PRECISION    NOT NULL,
    original_cv              DOUBLE PRECISION    DEFAULT 0,
    post_calibration_value   DOUBLE PRECISION    NOT NULL,
    post_calibration_cv      DOUBLE PRECISION    DEFAULT 0,
    target_value             DOUBLE PRECISION    DEFAULT 0,
    analyst                  VARCHAR(255),
    notes                    TEXT,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_post_calibration_records PRIMARY KEY (id),
    CONSTRAINT fk_post_calibration_records_qc_record FOREIGN KEY (qc_record_id)
        REFERENCES qc_records (id)
);

-- ---------------------------------------------------------------------------
-- reagent_lots (entity: ReagentLot)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reagent_lots (
    id                      UUID                NOT NULL,
    name                    VARCHAR(255)        NOT NULL,
    lot_number              VARCHAR(255)        NOT NULL,
    manufacturer            VARCHAR(255),
    category                VARCHAR(255),
    expiry_date             DATE,
    quantity_value          DOUBLE PRECISION    DEFAULT 0,
    stock_unit              VARCHAR(255)        DEFAULT 'unidades',
    current_stock           DOUBLE PRECISION    DEFAULT 0,
    estimated_consumption   DOUBLE PRECISION    DEFAULT 0,
    storage_temp            VARCHAR(255),
    start_date              DATE,
    end_date                DATE,
    status                  VARCHAR(255)        NOT NULL DEFAULT 'ativo',
    alert_threshold_days    INTEGER             DEFAULT 7,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_reagent_lots PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- stock_movements (entity: StockMovement)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_movements (
    id              UUID                NOT NULL,
    reagent_lot_id  UUID                NOT NULL,
    type            VARCHAR(255)        NOT NULL,
    quantity        DOUBLE PRECISION    NOT NULL,
    responsible     VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_reagent_lot FOREIGN KEY (reagent_lot_id)
        REFERENCES reagent_lots (id)
);

-- ---------------------------------------------------------------------------
-- maintenance_records (entity: MaintenanceRecord)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS maintenance_records (
    id          UUID            NOT NULL,
    equipment   VARCHAR(255)    NOT NULL,
    type        VARCHAR(255)    NOT NULL,
    date        DATE            NOT NULL,
    next_date   DATE,
    technician  VARCHAR(255),
    notes       TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_maintenance_records PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- hematology_qc_parameters (entity: HematologyQcParameter)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hematology_qc_parameters (
    id                      UUID                NOT NULL,
    analito                 VARCHAR(255)        NOT NULL,
    equipamento             VARCHAR(255),
    lote_controle           VARCHAR(255),
    nivel_controle          VARCHAR(255),
    modo                    VARCHAR(255)        NOT NULL DEFAULT 'INTERVALO',
    alvo_valor              DOUBLE PRECISION    DEFAULT 0,
    min_valor               DOUBLE PRECISION    DEFAULT 0,
    max_valor               DOUBLE PRECISION    DEFAULT 0,
    tolerancia_percentual   DOUBLE PRECISION    DEFAULT 0,
    is_active               BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_hematology_qc_parameters PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- hematology_qc_measurements (entity: HematologyQcMeasurement)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hematology_qc_measurements (
    id              UUID                NOT NULL,
    parameter_id    UUID                NOT NULL,
    data_medicao    DATE                NOT NULL,
    analito         VARCHAR(255)        NOT NULL,
    valor_medido    DOUBLE PRECISION    NOT NULL,
    modo_usado      VARCHAR(255),
    min_aplicado    DOUBLE PRECISION    DEFAULT 0,
    max_aplicado    DOUBLE PRECISION    DEFAULT 0,
    status          VARCHAR(255)        NOT NULL,
    observacao      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_hematology_qc_measurements PRIMARY KEY (id),
    CONSTRAINT fk_hematology_qc_measurements_parameter FOREIGN KEY (parameter_id)
        REFERENCES hematology_qc_parameters (id)
);

-- ---------------------------------------------------------------------------
-- hematology_bio_records (entity: HematologyBioRecord)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hematology_bio_records (
    id                  UUID                NOT NULL,
    data_bio            DATE                NOT NULL,
    data_pad            DATE,
    registro_bio        VARCHAR(255),
    registro_pad        VARCHAR(255),
    modo_ci             VARCHAR(255)        DEFAULT 'bio',
    bio_hemacias        DOUBLE PRECISION    DEFAULT 0,
    bio_hematocrito     DOUBLE PRECISION    DEFAULT 0,
    bio_hemoglobina     DOUBLE PRECISION    DEFAULT 0,
    bio_leucocitos      DOUBLE PRECISION    DEFAULT 0,
    bio_plaquetas       DOUBLE PRECISION    DEFAULT 0,
    bio_rdw             DOUBLE PRECISION    DEFAULT 0,
    bio_vpm             DOUBLE PRECISION    DEFAULT 0,
    pad_hemacias        DOUBLE PRECISION    DEFAULT 0,
    pad_hematocrito     DOUBLE PRECISION    DEFAULT 0,
    pad_hemoglobina     DOUBLE PRECISION    DEFAULT 0,
    pad_leucocitos      DOUBLE PRECISION    DEFAULT 0,
    pad_plaquetas       DOUBLE PRECISION    DEFAULT 0,
    pad_rdw             DOUBLE PRECISION    DEFAULT 0,
    pad_vpm             DOUBLE PRECISION    DEFAULT 0,
    ci_min_hemacias     DOUBLE PRECISION    DEFAULT 0,
    ci_max_hemacias     DOUBLE PRECISION    DEFAULT 0,
    ci_min_hematocrito  DOUBLE PRECISION    DEFAULT 0,
    ci_max_hematocrito  DOUBLE PRECISION    DEFAULT 0,
    ci_min_hemoglobina  DOUBLE PRECISION    DEFAULT 0,
    ci_max_hemoglobina  DOUBLE PRECISION    DEFAULT 0,
    ci_min_leucocitos   DOUBLE PRECISION    DEFAULT 0,
    ci_max_leucocitos   DOUBLE PRECISION    DEFAULT 0,
    ci_min_plaquetas    DOUBLE PRECISION    DEFAULT 0,
    ci_max_plaquetas    DOUBLE PRECISION    DEFAULT 0,
    ci_min_rdw          DOUBLE PRECISION    DEFAULT 0,
    ci_max_rdw          DOUBLE PRECISION    DEFAULT 0,
    ci_min_vpm          DOUBLE PRECISION    DEFAULT 0,
    ci_max_vpm          DOUBLE PRECISION    DEFAULT 0,
    ci_pct_hemacias     DOUBLE PRECISION    DEFAULT 0,
    ci_pct_hematocrito  DOUBLE PRECISION    DEFAULT 0,
    ci_pct_hemoglobina  DOUBLE PRECISION    DEFAULT 0,
    ci_pct_leucocitos   DOUBLE PRECISION    DEFAULT 0,
    ci_pct_plaquetas    DOUBLE PRECISION    DEFAULT 0,
    ci_pct_rdw          DOUBLE PRECISION    DEFAULT 0,
    ci_pct_vpm          DOUBLE PRECISION    DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_hematology_bio_records PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- imunologia_records (entity: ImunologiaRecord)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS imunologia_records (
    id          UUID            NOT NULL,
    controle    VARCHAR(255),
    fabricante  VARCHAR(255),
    lote        VARCHAR(255),
    data        DATE            NOT NULL,
    resultado   VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_imunologia_records PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- area_qc_parameters (entity: AreaQcParameter)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS area_qc_parameters (
    id                      UUID                NOT NULL,
    area                    VARCHAR(255)        NOT NULL,
    analito                 VARCHAR(255)        NOT NULL,
    equipamento             VARCHAR(255),
    lote_controle           VARCHAR(255),
    nivel_controle          VARCHAR(255),
    modo                    VARCHAR(255)        NOT NULL,
    alvo_valor              DOUBLE PRECISION    NOT NULL,
    min_valor               DOUBLE PRECISION,
    max_valor               DOUBLE PRECISION,
    tolerancia_percentual   DOUBLE PRECISION,
    is_active               BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_area_qc_parameters PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- area_qc_measurements (entity: AreaQcMeasurement)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS area_qc_measurements (
    id              UUID                NOT NULL,
    parameter_id    UUID                NOT NULL,
    area            VARCHAR(255)        NOT NULL,
    data_medicao    DATE                NOT NULL,
    analito         VARCHAR(255)        NOT NULL,
    valor_medido    DOUBLE PRECISION    NOT NULL,
    modo_usado      VARCHAR(255)        NOT NULL,
    min_aplicado    DOUBLE PRECISION    NOT NULL,
    max_aplicado    DOUBLE PRECISION    NOT NULL,
    status          VARCHAR(255)        NOT NULL,
    observacao      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_area_qc_measurements PRIMARY KEY (id),
    CONSTRAINT fk_area_qc_measurements_parameter FOREIGN KEY (parameter_id)
        REFERENCES area_qc_parameters (id)
);
