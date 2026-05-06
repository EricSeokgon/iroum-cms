-- SPEC-CMS-008 v0.4: 시각화 대시보드 + KPI 통합
-- 6 dashboard tables + KPI 의존 테이블 (kpi_definition / kpi_value / kpi_value_history) 사전 생성
-- 후일 SPEC-CMS-005 V14 가 이미 만들었다면 IF NOT EXISTS 가드.

-- ─── 0. KPI 모델 (SPEC-CMS-005 의존) ─────────────────────────────────────────
-- kpi_definition / kpi_value / kpi_value_history 가 V14 기준에서는 등록되어 있지 않으므로
-- SPEC-CMS-008 의 데이터 소스로 활용하기 위해 본 마이그레이션에서 선행 생성한다.
CREATE TABLE IF NOT EXISTS kpi_definition (
    id                    BIGSERIAL    PRIMARY KEY,
    code                  VARCHAR(50)  NOT NULL UNIQUE,
    name                  VARCHAR(200) NOT NULL,
    description           TEXT,
    calculation_query     TEXT         NOT NULL,
    refresh_interval_min  INTEGER      NOT NULL DEFAULT 60,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_kpi_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
COMMENT ON TABLE kpi_definition IS 'KPI 메타정보. SPEC-CMS-005 §14.1';

CREATE TABLE IF NOT EXISTS kpi_value (
    id              BIGSERIAL    PRIMARY KEY,
    kpi_id          BIGINT       NOT NULL REFERENCES kpi_definition(id) ON DELETE CASCADE,
    dimension       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    value_numeric   NUMERIC(20,4) NULL,
    value_text      TEXT         NULL,
    calculated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kpi_value UNIQUE (kpi_id, dimension)
);
CREATE INDEX IF NOT EXISTS idx_kpi_value_calc    ON kpi_value(kpi_id, calculated_at DESC);
CREATE INDEX IF NOT EXISTS idx_kpi_value_dim_gin ON kpi_value USING GIN (dimension);

CREATE TABLE IF NOT EXISTS kpi_value_history (
    id              BIGSERIAL    PRIMARY KEY,
    kpi_id          BIGINT       NOT NULL,
    dimension       JSONB        NOT NULL,
    value_numeric   NUMERIC(20,4) NULL,
    value_text      TEXT         NULL,
    calculated_at   TIMESTAMPTZ  NOT NULL,
    archived_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kpi_history_kpi_time ON kpi_value_history(kpi_id, calculated_at DESC);

-- ─── 1. dashboard_widget : 위젯 정의 (REQ-VIZ-001) ──────────────────────────
CREATE TABLE dashboard_widget (
    id                      BIGSERIAL    PRIMARY KEY,
    code                    VARCHAR(64)  NOT NULL UNIQUE,
    name                    VARCHAR(128) NOT NULL,
    description             TEXT,
    widget_type             VARCHAR(32)  NOT NULL
        CHECK (widget_type IN (
            'METRIC_CARD','LINE_CHART','BAR_CHART','PIE_CHART',
            'RADAR_CHART','MATRIX_HEATMAP','TABLE','PROGRESS_BAR','MAP_KOREA'
        )),
    data_source             VARCHAR(32)  NOT NULL
        CHECK (data_source IN ('KPI_VALUE','CUSTOM_QUERY','EXTERNAL')),
    data_source_config      JSONB        NOT NULL,
    default_config          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    available_dimensions    TEXT[]       NOT NULL DEFAULT ARRAY['period']::TEXT[],
    required_role_codes     TEXT[]       NOT NULL DEFAULT ARRAY['VIEWER']::TEXT[],
    status                  VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','DEPRECATED','HIDDEN')),
    created_by              BIGINT       REFERENCES users(id),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_widget_type   ON dashboard_widget(widget_type) WHERE status = 'ACTIVE';
CREATE INDEX idx_widget_source ON dashboard_widget(data_source);
COMMENT ON TABLE dashboard_widget IS '대시보드 위젯 정의. REQ-VIZ-001';

-- ─── 2. dashboard_layout : 사용자별 레이아웃 (REQ-VIZ-002) ──────────────────
CREATE TABLE dashboard_layout (
    id              BIGSERIAL    PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    grid_config     JSONB        NOT NULL DEFAULT '{"columns":12,"row_height":80}'::jsonb,
    shared_with     TEXT[]       NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_dashboard_owner_name UNIQUE (owner_id, name)
);
CREATE INDEX idx_dashboard_owner_default ON dashboard_layout(owner_id, is_default DESC);
CREATE UNIQUE INDEX uk_dashboard_one_default
    ON dashboard_layout(owner_id) WHERE is_default = TRUE;
COMMENT ON TABLE dashboard_layout IS '사용자별 대시보드 레이아웃. REQ-VIZ-002';

-- ─── 3. dashboard_layout_widget : 레이아웃-위젯 매핑 ────────────────────────
CREATE TABLE dashboard_layout_widget (
    layout_id        BIGINT       NOT NULL REFERENCES dashboard_layout(id) ON DELETE CASCADE,
    widget_id        BIGINT       NOT NULL REFERENCES dashboard_widget(id) ON DELETE RESTRICT,
    instance_id      VARCHAR(64)  NOT NULL,
    position         JSONB        NOT NULL,
    config_override  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    sort_order       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (layout_id, instance_id)
);
CREATE INDEX idx_layout_widget_widget ON dashboard_layout_widget(widget_id);

-- ─── 4. saved_view : 저장된 필터/뷰 (REQ-VIZ-004) ───────────────────────────
CREATE TABLE saved_view (
    id              BIGSERIAL    PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dashboard_id    BIGINT       REFERENCES dashboard_layout(id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    filter_state    JSONB        NOT NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_shared       BOOLEAN      NOT NULL DEFAULT FALSE,
    shared_with     TEXT[]       NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_view_owner_name UNIQUE (owner_id, dashboard_id, name)
);
CREATE INDEX idx_view_owner_dash ON saved_view(owner_id, dashboard_id);
CREATE INDEX idx_view_last_used  ON saved_view(owner_id, last_used_at DESC);
COMMENT ON TABLE saved_view IS '저장된 필터 뷰. REQ-VIZ-004';

-- ─── 5. chart_dataset_cache : 차트 데이터 캐시 (REQ-VIZ-005) ────────────────
CREATE TABLE chart_dataset_cache (
    id              BIGSERIAL    PRIMARY KEY,
    cache_key       VARCHAR(255) NOT NULL UNIQUE,
    widget_id       BIGINT       REFERENCES dashboard_widget(id) ON DELETE CASCADE,
    dataset         JSONB        NOT NULL,
    generated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_cache_expires
    ON chart_dataset_cache(expires_at);
CREATE INDEX idx_cache_widget ON chart_dataset_cache(widget_id);
COMMENT ON TABLE chart_dataset_cache IS '차트 데이터셋 캐시 (TTL 5분). REQ-VIZ-005-D-3';

-- ─── 6. export_history : 내보내기 이력 (REQ-VIZ-006) ────────────────────────
CREATE TABLE export_history (
    id              BIGSERIAL    PRIMARY KEY,
    requestor_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    export_type     VARCHAR(16)  NOT NULL
        CHECK (export_type IN ('EXCEL','CSV','PDF')),
    scope           JSONB        NOT NULL,
    file_path       TEXT,
    size_bytes      BIGINT,
    row_count       INT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING'
        CHECK (status IN ('PROCESSING','COMPLETED','FAILED','EXPIRED')),
    progress_pct    SMALLINT     NOT NULL DEFAULT 0
        CHECK (progress_pct BETWEEN 0 AND 100),
    error_message   TEXT,
    requested_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);
CREATE INDEX idx_export_requestor ON export_history(requestor_id, requested_at DESC);
CREATE INDEX idx_export_status    ON export_history(status, requested_at)
    WHERE status = 'PROCESSING';
CREATE INDEX idx_export_expires   ON export_history(expires_at)
    WHERE status = 'COMPLETED';
COMMENT ON TABLE export_history IS '내보내기 이력 (24시간 TTL). REQ-VIZ-006-D-5';
