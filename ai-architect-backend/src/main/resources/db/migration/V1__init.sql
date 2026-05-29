-- ============================================================
-- AI Architect — PostgreSQL Schema
-- Migration V1: Initial schema
-- Flyway: src/main/resources/db/migration/V1__init.sql
-- ============================================================

-- ── Sessions ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sessions (
    session_id      VARCHAR(36)     PRIMARY KEY,
    user_input      TEXT            NOT NULL DEFAULT '',
    mode            VARCHAR(10)     NOT NULL DEFAULT 'deep',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    current_phase   INT             NOT NULL DEFAULT 0,
    context_buffer  TEXT            NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_status     ON sessions(status);
CREATE INDEX idx_sessions_created_at ON sessions(created_at DESC);

-- ── Phase Results ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS phase_results (
    id              BIGSERIAL       PRIMARY KEY,
    session_id      VARCHAR(36)     NOT NULL REFERENCES sessions(session_id) ON DELETE CASCADE,
    phase_id        INT             NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    output          TEXT,
    provider        VARCHAR(50),
    model           VARCHAR(100),
    confidence      NUMERIC(4,2),
    latency_ms      BIGINT,
    token_count     INT,
    retries         INT             NOT NULL DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    UNIQUE (session_id, phase_id)
);

CREATE INDEX idx_phase_results_session ON phase_results(session_id);
CREATE INDEX idx_phase_results_phase   ON phase_results(phase_id);

-- ── Projects ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS projects (
    id              BIGSERIAL       PRIMARY KEY,
    session_id      VARCHAR(36)     NOT NULL REFERENCES sessions(session_id),
    name            VARCHAR(200)    NOT NULL,
    project_dir     TEXT,
    zip_path        TEXT,
    confidence_avg  NUMERIC(4,2),
    outcome         VARCHAR(20),    -- success | partial | failed
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_projects_session    ON projects(session_id);
CREATE INDEX idx_projects_created_at ON projects(created_at DESC);

-- ── Knowledge Graph ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS knowledge_patterns (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(200),
    domain      VARCHAR(100),
    stack       TEXT,
    description TEXT,
    confidence  NUMERIC(4,2),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS knowledge_risks (
    id          BIGSERIAL   PRIMARY KEY,
    domain      VARCHAR(100),
    phase_id    INT,
    description TEXT        UNIQUE,
    mitigation  TEXT,
    count       INT         NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS knowledge_model_stats (
    id          BIGSERIAL   PRIMARY KEY,
    provider    VARCHAR(50),
    phase_id    INT,
    avg_score   NUMERIC(4,2),
    call_count  INT         NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, phase_id)
);

-- ── Provider Metrics ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS provider_metrics (
    id          BIGSERIAL   PRIMARY KEY,
    provider    VARCHAR(50) NOT NULL,
    model       VARCHAR(100),
    success     BOOLEAN     NOT NULL,
    tokens      INT,
    latency_ms  BIGINT,
    phase_id    INT,
    session_id  VARCHAR(36),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provider_metrics_provider ON provider_metrics(provider);
CREATE INDEX idx_provider_metrics_recorded ON provider_metrics(recorded_at DESC);

-- ── Audit Logs ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL   PRIMARY KEY,
    session_id  VARCHAR(36),
    level       VARCHAR(10) NOT NULL, -- TRACE|DEBUG|INFO|WARN|ERROR
    component   VARCHAR(100),
    message     TEXT        NOT NULL,
    metadata    JSONB,
    logged_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_session ON audit_logs(session_id);
CREATE INDEX idx_audit_logged  ON audit_logs(logged_at DESC);
CREATE INDEX idx_audit_level   ON audit_logs(level);

-- ── Helper function: update updated_at ────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sessions_updated_at
    BEFORE UPDATE ON sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
