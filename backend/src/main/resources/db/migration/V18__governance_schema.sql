-- SPEC-CMS-009 v0.1: 데이터 거버넌스 (표준 사전·보존정책·통계 파이프라인·품질 모니터링·RTO/RPO)
-- Step 1: 13개 신규 테이블 + 시드 데이터 (retention_policy 5건 + data_quality_rule 8건)
-- 의존: SPEC-CMS-005 (V14 access_log), SPEC-CMS-002 (V9 personal_data_access_log)
-- REQ: REQ-GOV-001~012, REQ-DATA-001~008

-- ─── 1. data_dictionary (데이터 표준 사전) ────────────────────────────────────
-- REQ-GOV-001~004: 테이블·컬럼 한글명, 도메인 분류, S-Meta/DA# 호환
CREATE TABLE data_dictionary (
    id                BIGSERIAL    PRIMARY KEY,
    table_name        VARCHAR(80)  NOT NULL,
    column_name       VARCHAR(80)  NOT NULL,
    logical_name_ko   VARCHAR(200) NOT NULL,
    logical_name_en   VARCHAR(200) NULL,
    data_domain       VARCHAR(20)  NOT NULL,
    data_type         VARCHAR(50)  NOT NULL,
    description       TEXT         NULL,
    is_pii            BOOLEAN      NOT NULL DEFAULT FALSE,
    is_required       BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dd_table_col UNIQUE (table_name, column_name),
    CONSTRAINT chk_dd_domain   CHECK (data_domain IN ('MASTER','TRANSACTION','STATISTICS','LOG')),
    CONSTRAINT chk_dd_status   CHECK (status IN ('ACTIVE','DEPRECATED','REMOVED'))
);
CREATE INDEX idx_dd_domain_status ON data_dictionary(data_domain, status);
CREATE INDEX idx_dd_table         ON data_dictionary(table_name);

COMMENT ON TABLE  data_dictionary                  IS 'SPEC-CMS-009 데이터 표준 사전 (S-Meta/DA# 호환)';
COMMENT ON COLUMN data_dictionary.logical_name_ko  IS '한글 논리명';
COMMENT ON COLUMN data_dictionary.data_domain      IS 'MASTER/TRANSACTION/STATISTICS/LOG';
COMMENT ON COLUMN data_dictionary.is_pii           IS '개인정보 여부';

-- ─── 2. data_dictionary_history (변경 이력) ──────────────────────────────────
-- REQ-GOV-003: data_dictionary 변경 이력 자동 적재
CREATE TABLE data_dictionary_history (
    id                BIGSERIAL    PRIMARY KEY,
    dictionary_id     BIGINT       NOT NULL REFERENCES data_dictionary(id) ON DELETE CASCADE,
    field_changed     VARCHAR(50)  NOT NULL,
    old_value         TEXT         NULL,
    new_value         TEXT         NULL,
    changed_by        BIGINT       NULL,
    changed_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ddh_dict_time ON data_dictionary_history(dictionary_id, changed_at DESC);

COMMENT ON TABLE data_dictionary_history IS 'data_dictionary 컬럼별 변경 이력';

-- ─── 3. retention_policy (보존·이관 정책) ─────────────────────────────────────
-- REQ-GOV-006~009: 보존 정책 자동화 (DELETE/ARCHIVE/ANONYMIZE)
CREATE TABLE retention_policy (
    id                BIGSERIAL    PRIMARY KEY,
    target_table      VARCHAR(80)  NOT NULL UNIQUE,
    policy_type       VARCHAR(20)  NOT NULL,
    retention_months  INTEGER      NOT NULL,
    archive_table     VARCHAR(80)  NULL,
    anonymize_columns JSONB        NULL,
    schedule_cron     VARCHAR(50)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    description       TEXT         NULL,
    updated_by        BIGINT       NULL,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rp_type   CHECK (policy_type IN ('DELETE','ARCHIVE','ANONYMIZE')),
    CONSTRAINT chk_rp_status CHECK (status IN ('ACTIVE','PAUSED'))
);

COMMENT ON TABLE retention_policy IS '데이터 보존·이관 정책 (target_table 기준)';

-- 시드 데이터: 5개 보존 정책 (REQ-GOV-007~009 + access_log/integration_log)
INSERT INTO retention_policy (target_table, policy_type, retention_months, archive_table, schedule_cron, description) VALUES
    ('personal_data_access_log', 'ARCHIVE',  6,  'personal_data_access_log_archive', '0 0 4 1 * *', 'SPEC-CMS-002 REQ-AUTH-018-D-3 6개월 자동화'),
    ('audit_log',                'ARCHIVE', 60, 'audit_log_archive',                 '0 30 3 1 * *', 'SPEC-CMS-005 REQ-CROSS-001-D-7 5년 보존'),
    ('login_history',            'DELETE',  12, NULL,                                '0 0 5 1 * *', '로그인 이력 1년 보존'),
    ('access_log',               'DELETE',   3, NULL,                                '0 30 4 1 * *', 'SPEC-CMS-005 REQ-SYSTEM-001-D-5 3개월'),
    ('integration_log',          'ARCHIVE',  6,  'integration_log_archive',          '0 15 4 1 * *', 'SPEC-CMS-005 REQ-SYSTEM-008-D-4 자동화')
ON CONFLICT (target_table) DO NOTHING;

-- ─── 4. batch_execution_log (배치 실행 이력) ──────────────────────────────────
-- REQ-DATA-005, REQ-GOV-010: 모든 거버넌스 배치의 실행 이력 추적
CREATE TABLE batch_execution_log (
    id                BIGSERIAL    PRIMARY KEY,
    job_name          VARCHAR(100) NOT NULL,
    job_group         VARCHAR(20)  NOT NULL,
    started_at        TIMESTAMPTZ  NOT NULL,
    finished_at       TIMESTAMPTZ  NULL,
    duration_ms       INTEGER      NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    records_processed INTEGER      NOT NULL DEFAULT 0,
    records_failed    INTEGER      NOT NULL DEFAULT 0,
    error_summary     TEXT         NULL,
    retry_count       INTEGER      NOT NULL DEFAULT 0,
    triggered_by      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULE',
    operator_id       BIGINT       NULL,
    CONSTRAINT chk_bel_group  CHECK (job_group IN ('STATS','RETENTION','QUALITY','RECOVERY')),
    CONSTRAINT chk_bel_status CHECK (status IN ('RUNNING','SUCCESS','FAILURE','TIMEOUT','RETRYING','SKIPPED')),
    CONSTRAINT chk_bel_trig   CHECK (triggered_by IN ('SCHEDULE','MANUAL'))
);
CREATE INDEX idx_bel_job_time   ON batch_execution_log(job_name, started_at DESC);
CREATE INDEX idx_bel_group_time ON batch_execution_log(job_group, started_at DESC);
CREATE INDEX idx_bel_failure    ON batch_execution_log(started_at DESC) WHERE status IN ('FAILURE','TIMEOUT');

COMMENT ON TABLE batch_execution_log IS 'STATS/RETENTION/QUALITY/RECOVERY 배치 실행 이력';

-- ─── 5. board_stats_daily / board_stats_monthly ─────────────────────────────
-- REQ-DATA-001: 게시판별 일/월 통계 (access_log 기반)
CREATE TABLE board_stats_daily (
    stat_date         DATE      NOT NULL,
    board_id          BIGINT    NOT NULL,
    total_views       INTEGER   NOT NULL DEFAULT 0,
    unique_visitors   INTEGER   NOT NULL DEFAULT 0,
    post_count        INTEGER   NOT NULL DEFAULT 0,
    comment_count     INTEGER   NOT NULL DEFAULT 0,
    avg_response_ms   INTEGER   NOT NULL DEFAULT 0,
    aggregated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_date, board_id)
);
CREATE INDEX idx_bsd_board_time ON board_stats_daily(board_id, stat_date DESC);

CREATE TABLE board_stats_monthly (
    stat_month        CHAR(7)   NOT NULL,
    board_id          BIGINT    NOT NULL,
    total_views       INTEGER   NOT NULL DEFAULT 0,
    unique_visitors   INTEGER   NOT NULL DEFAULT 0,
    post_count        INTEGER   NOT NULL DEFAULT 0,
    comment_count     INTEGER   NOT NULL DEFAULT 0,
    aggregated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_month, board_id)
);

-- ─── 6. content_view_stats_daily / content_view_stats_monthly ──────────────
-- REQ-DATA-002: 콘텐츠별 일/월 조회수 + dwell time
CREATE TABLE content_view_stats_daily (
    stat_date         DATE      NOT NULL,
    content_id        BIGINT    NOT NULL,
    view_count        INTEGER   NOT NULL DEFAULT 0,
    unique_viewers    INTEGER   NOT NULL DEFAULT 0,
    avg_dwell_sec     INTEGER   NOT NULL DEFAULT 0,
    aggregated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_date, content_id)
);
CREATE INDEX idx_cvs_content_time ON content_view_stats_daily(content_id, stat_date DESC);

CREATE TABLE content_view_stats_monthly (
    stat_month        CHAR(7)   NOT NULL,
    content_id        BIGINT    NOT NULL,
    view_count        INTEGER   NOT NULL DEFAULT 0,
    unique_viewers    INTEGER   NOT NULL DEFAULT 0,
    aggregated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_month, content_id)
);

-- ─── 7. policy_match_stats_monthly ───────────────────────────────────────────
-- REQ-DATA-003: 정책사업 매칭 성공률 월별 (SPEC-CMS-007 의존)
CREATE TABLE policy_match_stats_monthly (
    stat_month            CHAR(7)        NOT NULL,
    policy_id             BIGINT         NOT NULL,
    match_count           INTEGER        NOT NULL DEFAULT 0,
    apply_count           INTEGER        NOT NULL DEFAULT 0,
    apply_conversion_rate NUMERIC(7,4)   NOT NULL DEFAULT 0,
    success_count         INTEGER        NOT NULL DEFAULT 0,
    aggregated_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_month, policy_id)
);
CREATE INDEX idx_pms_policy_time ON policy_match_stats_monthly(policy_id, stat_month DESC);

-- ─── 8. safety_stats_monthly ─────────────────────────────────────────────────
-- REQ-DATA-004: 안전사고 월별 추이 (SPEC-CMS-006 의존)
CREATE TABLE safety_stats_monthly (
    stat_month         CHAR(7)        NOT NULL,
    incident_category  VARCHAR(50)    NOT NULL,
    incident_count     INTEGER        NOT NULL DEFAULT 0,
    casualty_count     INTEGER        NOT NULL DEFAULT 0,
    severity_avg       NUMERIC(5,2)   NOT NULL DEFAULT 0,
    aggregated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_month, incident_category)
);
CREATE INDEX idx_ssm_month ON safety_stats_monthly(stat_month DESC);

-- ─── 9. data_quality_rule (품질 룰 정의) ─────────────────────────────────────
-- REQ-DATA-006~007: NULL_RATIO/RANGE/IQR/UNIQUE/FRESHNESS 5종 룰
CREATE TABLE data_quality_rule (
    id              BIGSERIAL    PRIMARY KEY,
    target_table    VARCHAR(80)  NOT NULL,
    target_column   VARCHAR(80)  NULL,
    rule_type       VARCHAR(20)  NOT NULL,
    threshold       NUMERIC(10,4) NOT NULL,
    range_min       NUMERIC(20,4) NULL,
    range_max       NUMERIC(20,4) NULL,
    severity        VARCHAR(20)  NOT NULL DEFAULT 'WARN',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    schedule_cron   VARCHAR(50)  NOT NULL DEFAULT '0 0 6 * * *',
    description     TEXT         NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dqr_type     CHECK (rule_type IN ('NULL_RATIO','RANGE','IQR','UNIQUE','FRESHNESS')),
    CONSTRAINT chk_dqr_severity CHECK (severity IN ('INFO','WARN','CRITICAL')),
    CONSTRAINT chk_dqr_status   CHECK (status IN ('ACTIVE','PAUSED'))
);
CREATE INDEX idx_dqr_table ON data_quality_rule(target_table, status);

-- 시드 데이터: 8개 기본 품질 룰 (created_at 기반 FRESHNESS, IQR/UNIQUE/NULL_RATIO 혼합)
INSERT INTO data_quality_rule (target_table, target_column, rule_type, threshold, severity, schedule_cron, description) VALUES
    ('users',           'email',         'NULL_RATIO', 0.0500, 'WARN',     '0 0 6 * * *',  'users.email NULL 비율 5% 이내'),
    ('users',           'email',         'UNIQUE',     1.0000, 'CRITICAL', '0 10 6 * * *', 'users.email 중복 0건'),
    ('access_log',       NULL,           'FRESHNESS',  6.0000, 'WARN',     '0 0 6 * * *',  'access_log 6시간 이내 신규 적재'),
    ('access_log',      'response_time_ms','IQR',      0.0500, 'INFO',     '0 30 6 * * *', 'access_log.response_time_ms IQR 이상값 5% 이내'),
    ('login_history',    NULL,           'FRESHNESS', 24.0000, 'INFO',     '0 0 6 * * *',  'login_history 24시간 이내 신규 적재'),
    ('data_dictionary',  NULL,           'FRESHNESS',720.0000, 'INFO',     '0 30 6 * * *', 'data_dictionary 30일 이내 갱신'),
    ('users',            NULL,           'FRESHNESS',720.0000, 'INFO',     '0 30 6 * * *', 'users 30일 이내 신규 가입/갱신'),
    ('users',           'username',      'UNIQUE',     1.0000, 'CRITICAL', '0 20 6 * * *', 'users.username 중복 0건')
ON CONFLICT DO NOTHING;

-- ─── 10. data_quality_report (품질 검사 리포트) ──────────────────────────────
-- REQ-DATA-007~008: 룰 실행 결과 + 위반 알림
CREATE TABLE data_quality_report (
    id              BIGSERIAL    PRIMARY KEY,
    rule_id         BIGINT       NOT NULL REFERENCES data_quality_rule(id) ON DELETE CASCADE,
    checked_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    measured_value  NUMERIC(20,4) NULL,
    violation       BOOLEAN      NOT NULL,
    detail          TEXT         NULL,
    notified        BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_dqrep_rule_time   ON data_quality_report(rule_id, checked_at DESC);
CREATE INDEX idx_dqrep_violation   ON data_quality_report(checked_at DESC) WHERE violation = TRUE;

-- ─── 11. recovery_drill_log (복구 시험 이력) ─────────────────────────────────
-- REQ-GOV-011~012: RTO 240분 / RPO 60분 목표 측정
CREATE TABLE recovery_drill_log (
    id               BIGSERIAL    PRIMARY KEY,
    drill_date       DATE         NOT NULL,
    drill_type       VARCHAR(30)  NOT NULL,
    result           VARCHAR(20)  NOT NULL,
    rto_actual_min   INTEGER      NULL,
    rpo_actual_min   INTEGER      NULL,
    rto_target_min   INTEGER      NOT NULL DEFAULT 240,
    rpo_target_min   INTEGER      NOT NULL DEFAULT 60,
    performed_by     BIGINT       NULL,
    checklist_json   JSONB        NULL,
    notes            TEXT         NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rdl_type   CHECK (drill_type IN ('BACKUP_RESTORE','FAILOVER','PITR')),
    CONSTRAINT chk_rdl_result CHECK (result IN ('PASS','FAIL','PARTIAL'))
);
CREATE INDEX idx_rdl_date ON recovery_drill_log(drill_date DESC);

COMMENT ON TABLE recovery_drill_log IS '복구 시험 이력 (DAR-009 RTO/RPO 측정)';
