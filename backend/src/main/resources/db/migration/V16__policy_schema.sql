-- SPEC-CMS-007: 정책사업 지능형 매칭 + 적기 타겟팅 알림 스키마
-- 10개 핵심 테이블 + notification_template 스텁(SPEC-CMS-004 정식 구현 전 FK 제약 충족용)
--
-- @MX:NOTE: notification_template 와 departments 테이블은 후속 SPEC(SPEC-CMS-004 정식 마이그레이션, SPEC-CMS-002 조직)에서
--           정식 컬럼이 채워질 예정. 본 마이그레이션은 FK 무결성 확보 위해 최소 컬럼만 stub 으로 생성한다.
-- @MX:SPEC: SPEC-CMS-007

-- ============================================================
-- 0. notification_template 스텁 (SPEC-CMS-004 정식 마이그 전까지 임시)
-- ============================================================
CREATE TABLE IF NOT EXISTS notification_template (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    channel         VARCHAR(20)  NOT NULL,
    body_template   TEXT         NOT NULL,
    review_status   VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE notification_template IS 'SPEC-CMS-004 알림 템플릿 (V16 stub — 정식 컬럼은 SPEC-CMS-004 실 마이그에서 ALTER 적용)';

CREATE TABLE IF NOT EXISTS departments (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    parent_id   BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE departments IS 'SPEC-CMS-002 조직 부서 (V16 stub)';

-- ============================================================
-- 4.2.10 policy_data_source — 외부 OpenAPI 소스 (FK 의존성 우선)
-- ============================================================
CREATE TABLE policy_data_source (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    ministry        VARCHAR(50)  NOT NULL,
    api_endpoint    VARCHAR(500) NOT NULL,
    auth_type       VARCHAR(30)  NOT NULL DEFAULT 'API_KEY',
    auth_secret_ref VARCHAR(200),
    schedule_cron   VARCHAR(100) NOT NULL DEFAULT '0 0 3 * * *',
    last_sync_at    TIMESTAMPTZ,
    last_status     VARCHAR(20),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    owner_dept_id   BIGINT       REFERENCES departments(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_pds_status CHECK (last_status IS NULL OR last_status IN ('SUCCESS','FAILURE','PARTIAL'))
);
COMMENT ON TABLE  policy_data_source IS 'SPEC-CMS-007 외부 OpenAPI 소스';
COMMENT ON COLUMN policy_data_source.auth_secret_ref IS '인증 키 평문 금지 — Secrets Manager 참조 형식';

-- ============================================================
-- 4.2.1 policy_program — 정책사업 마스터
-- ============================================================
CREATE TABLE policy_program (
    id                          BIGSERIAL PRIMARY KEY,
    code                        VARCHAR(100) NOT NULL UNIQUE,
    ministry                    VARCHAR(50)  NOT NULL,
    program_name                VARCHAR(300) NOT NULL,
    program_name_i18n           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    description_html            TEXT,
    target_industries           TEXT[]       NOT NULL DEFAULT '{}',
    target_regions              TEXT[]       NOT NULL DEFAULT '{}',
    min_employees               INT,
    max_employees               INT,
    min_revenue                 BIGINT,
    max_revenue                 BIGINT,
    min_business_age_months     INT,
    max_business_age_months     INT,
    application_start           TIMESTAMPTZ,
    application_end             TIMESTAMPTZ,
    budget_total                BIGINT,
    budget_per_company          BIGINT,
    source_url                  VARCHAR(500),
    source_api_id               VARCHAR(200),
    source_id                   BIGINT       REFERENCES policy_data_source(id) ON DELETE SET NULL,
    last_synced_at              TIMESTAMPTZ,
    import_warnings             JSONB        DEFAULT '[]'::jsonb,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_pp_status    CHECK (status IN ('DRAFT','ACTIVE','CLOSED','EXPIRED')),
    CONSTRAINT chk_pp_revenue   CHECK (min_revenue IS NULL OR max_revenue IS NULL OR min_revenue <= max_revenue),
    CONSTRAINT chk_pp_employees CHECK (min_employees IS NULL OR max_employees IS NULL OR min_employees <= max_employees)
);
CREATE INDEX idx_pp_status_app  ON policy_program(status, application_end);
CREATE INDEX idx_pp_industries  ON policy_program USING GIN (target_industries);
CREATE INDEX idx_pp_regions     ON policy_program USING GIN (target_regions);
CREATE UNIQUE INDEX uq_pp_source_api ON policy_program(source_id, source_api_id) WHERE source_api_id IS NOT NULL;
COMMENT ON TABLE  policy_program IS 'SPEC-CMS-007 SFR-007 정책사업 마스터';

-- ============================================================
-- 4.2.2 policy_eligibility_rule — 자격요건 규칙
-- ============================================================
CREATE TABLE policy_eligibility_rule (
    id            BIGSERIAL PRIMARY KEY,
    policy_id     BIGINT       NOT NULL REFERENCES policy_program(id) ON DELETE CASCADE,
    rule_type     VARCHAR(20)  NOT NULL,
    dimension     VARCHAR(20)  NOT NULL,
    operator      VARCHAR(20)  NOT NULL,
    rule_values   JSONB        NOT NULL,
    weight        NUMERIC(3,2) NOT NULL DEFAULT 0.10,
    description   TEXT,
    description_i18n JSONB     DEFAULT '{}'::jsonb,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_per_rule_type CHECK (rule_type IN ('INCLUDE','EXCLUDE')),
    CONSTRAINT chk_per_dimension CHECK (dimension IN ('INDUSTRY','REGION','SIZE','AGE','REVENUE','CERTIFICATION','KEYWORD')),
    CONSTRAINT chk_per_operator  CHECK (operator IN ('IN','NOT_IN','BETWEEN','GTE','LTE','EQ')),
    CONSTRAINT chk_per_weight    CHECK (weight >= 0.00 AND weight <= 1.00)
);
CREATE INDEX idx_per_policy ON policy_eligibility_rule(policy_id) WHERE active = TRUE;

-- ============================================================
-- 4.2.3 policy_keyword — 정책 키워드
-- ============================================================
CREATE TABLE policy_keyword (
    id          BIGSERIAL PRIMARY KEY,
    policy_id   BIGINT       NOT NULL REFERENCES policy_program(id) ON DELETE CASCADE,
    keyword     VARCHAR(100) NOT NULL,
    category    VARCHAR(50),
    weight      NUMERIC(3,2) NOT NULL DEFAULT 0.05,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_pk_weight CHECK (weight >= 0.00 AND weight <= 1.00)
);
CREATE INDEX idx_pk_keyword ON policy_keyword(keyword);
CREATE INDEX idx_pk_policy  ON policy_keyword(policy_id);

-- ============================================================
-- 4.2.4 company_match_input — 기업 프로필 (매칭 입력)
-- ============================================================
CREATE TABLE company_match_input (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    industry_codes      TEXT[]       NOT NULL DEFAULT '{}',
    region_codes        TEXT[]       NOT NULL DEFAULT '{}',
    employee_count      INT,
    annual_revenue      BIGINT,
    business_age_months INT,
    certifications      TEXT[]       NOT NULL DEFAULT '{}',
    custom_attrs        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    last_updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_cmi_company UNIQUE (company_id)
);
CREATE INDEX idx_cmi_industries ON company_match_input USING GIN (industry_codes);
CREATE INDEX idx_cmi_regions    ON company_match_input USING GIN (region_codes);
COMMENT ON TABLE company_match_input IS '매칭 알고리즘 입력 — 기업 프로필 스냅샷';

-- ============================================================
-- 4.2.5 policy_match_score — 기업-정책 매칭 결과 (TTL 캐시)
-- ============================================================
CREATE TABLE policy_match_score (
    id               BIGSERIAL PRIMARY KEY,
    company_id       BIGINT       NOT NULL,
    policy_id        BIGINT       NOT NULL REFERENCES policy_program(id) ON DELETE CASCADE,
    score            NUMERIC(5,2) NOT NULL,
    grade            VARCHAR(2)   NOT NULL,
    score_breakdown  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    matched_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ  NOT NULL,
    viewed_at        TIMESTAMPTZ,
    applied_at       TIMESTAMPTZ,
    CONSTRAINT chk_pms_grade CHECK (grade IN ('A','B','C','D')),
    CONSTRAINT chk_pms_score CHECK (score >= 0.00 AND score <= 100.00),
    CONSTRAINT uq_pms_active UNIQUE (company_id, policy_id, matched_at)
);
CREATE INDEX idx_pms_company_score ON policy_match_score(company_id, score DESC, expires_at);
CREATE INDEX idx_pms_expires       ON policy_match_score(expires_at) WHERE applied_at IS NULL;

-- ============================================================
-- 4.2.6 notification_subscription — 수신 동의/거부
-- ============================================================
CREATE TABLE notification_subscription (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel     VARCHAR(20)  NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    opted_in    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    source      VARCHAR(20)  NOT NULL DEFAULT 'USER',
    CONSTRAINT chk_ns_channel  CHECK (channel  IN ('KAKAO','EMAIL','SMS','INAPP')),
    CONSTRAINT chk_ns_source   CHECK (source   IN ('USER','ADMIN','SYSTEM')),
    CONSTRAINT chk_ns_category CHECK (category IN ('POLICY_MATCH','ANNOUNCEMENT','REMINDER','MARKETING')),
    CONSTRAINT uq_ns_user_chan_cat UNIQUE (user_id, channel, category)
);
CREATE INDEX idx_ns_user ON notification_subscription(user_id, opted_in);
COMMENT ON TABLE notification_subscription IS '개인정보보호법 제22조의2 자기결정권 — 사용자별 채널·카테고리별 옵트인/옵트아웃';

-- ============================================================
-- 4.2.7 notification_dispatch_schedule — 발송 예약
-- ============================================================
CREATE TABLE notification_dispatch_schedule (
    id            BIGSERIAL PRIMARY KEY,
    schedule_uuid UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    policy_id     BIGINT       REFERENCES policy_program(id) ON DELETE SET NULL,
    dispatch_type VARCHAR(30)  NOT NULL,
    target_filter JSONB        NOT NULL DEFAULT '{}'::jsonb,
    scheduled_at  TIMESTAMPTZ  NOT NULL,
    channels      TEXT[]       NOT NULL,
    template_id   BIGINT       NOT NULL REFERENCES notification_template(id) ON DELETE RESTRICT,
    priority      INT          NOT NULL DEFAULT 50,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_by    BIGINT       NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    CONSTRAINT chk_nds_type   CHECK (dispatch_type IN ('APPLICATION_OPEN','CLOSING_SOON','RESULT','REMINDER','ANNOUNCEMENT')),
    CONSTRAINT chk_nds_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','CANCELLED','FAILED'))
);
CREATE INDEX idx_nds_status_sched ON notification_dispatch_schedule(status, scheduled_at) WHERE status = 'PENDING';
CREATE INDEX idx_nds_policy       ON notification_dispatch_schedule(policy_id) WHERE policy_id IS NOT NULL;

-- ============================================================
-- 4.2.8 notification_dispatch_target — 발송 대상
-- ============================================================
CREATE TABLE notification_dispatch_target (
    id              BIGSERIAL PRIMARY KEY,
    schedule_id     BIGINT       NOT NULL REFERENCES notification_dispatch_schedule(id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    send_id         BIGINT,
    idempotency_key VARCHAR(100) NOT NULL,
    channel         VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    evaluated_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    failed_reason   TEXT,
    CONSTRAINT chk_ndt_status CHECK (status IN ('PENDING','SENT','FAILED','SKIPPED_OPTOUT','CANCELLED')),
    CONSTRAINT uq_ndt_idem UNIQUE (idempotency_key)
);
CREATE INDEX idx_ndt_schedule   ON notification_dispatch_target(schedule_id, status);
CREATE INDEX idx_ndt_user       ON notification_dispatch_target(user_id);
CREATE INDEX idx_ndt_send       ON notification_dispatch_target(send_id) WHERE send_id IS NOT NULL;
COMMENT ON COLUMN notification_dispatch_target.idempotency_key IS 'SHA-256 hash(schedule_id||user_id||dispatch_type)';

-- ============================================================
-- 4.2.9 policy_application_log — 정책 신청·클릭 추적
-- ============================================================
CREATE TABLE policy_application_log (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    policy_id             BIGINT       NOT NULL REFERENCES policy_program(id) ON DELETE CASCADE,
    source                VARCHAR(30)  NOT NULL,
    notification_send_id  BIGINT,
    action                VARCHAR(30)  NOT NULL,
    occurred_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_agent            VARCHAR(300),
    ip_address            INET,
    CONSTRAINT chk_pal_source CHECK (source IN ('NOTIFICATION','SEARCH','RECOMMENDATION','DIRECT')),
    CONSTRAINT chk_pal_action CHECK (action IN ('VIEW','CLICK_APPLY','EXTERNAL_REDIRECT','SAVED'))
);
CREATE INDEX idx_pal_user_time   ON policy_application_log(user_id, occurred_at DESC);
CREATE INDEX idx_pal_policy_time ON policy_application_log(policy_id, occurred_at DESC);
CREATE INDEX idx_pal_send        ON policy_application_log(notification_send_id) WHERE notification_send_id IS NOT NULL;
COMMENT ON TABLE policy_application_log IS 'SFR-008 클릭/전환 추적, KPI POLICY_APPLY_CVR 산출 원천';
