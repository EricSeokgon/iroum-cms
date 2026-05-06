-- SPEC-CMS-006: 안전경영 가이드라인 + 사고사례 매칭 스키마
-- 9개 핵심 테이블: 사고사례 / 키워드 / 동의어 / 매핑 / 기업프로필 / 매칭결과 / 템플릿 / 보고서 / 체크리스트 / 체크결과

-- ============================================================
-- 4.2.1 safety_incident — 사고사례 마스터
-- ============================================================
CREATE TABLE safety_incident (
    id                 BIGSERIAL PRIMARY KEY,
    source_type        VARCHAR(50)  NOT NULL,
    industry_code      VARCHAR(20)  NOT NULL,
    occupation_code    VARCHAR(20),
    process_type       VARCHAR(50),
    incident_type      VARCHAR(50)  NOT NULL,
    occurred_at        TIMESTAMPTZ  NOT NULL,
    severity           VARCHAR(20)  NOT NULL,
    casualties         INT          NOT NULL DEFAULT 0,
    location           VARCHAR(200),
    summary            TEXT         NOT NULL,
    detailed_cause     TEXT,
    prevention_lesson  TEXT,
    source_url         VARCHAR(500),
    search_vector      TSVECTOR,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_safety_incident_industry  ON safety_incident(industry_code, status);
CREATE INDEX idx_safety_incident_type      ON safety_incident(incident_type, severity);
CREATE INDEX idx_safety_incident_occurred  ON safety_incident(occurred_at DESC);
CREATE INDEX idx_safety_incident_search    ON safety_incident USING GIN(search_vector);

COMMENT ON TABLE  safety_incident IS '사고사례 마스터 — REQ-SAFETY-001';

-- ============================================================
-- 4.2.2 safety_keyword — 키워드 사전
-- ============================================================
CREATE TABLE safety_keyword (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(20)  NOT NULL,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    term        VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_safety_keyword_category ON safety_keyword(category, status);

CREATE TABLE safety_keyword_synonym (
    id          BIGSERIAL PRIMARY KEY,
    keyword_id  BIGINT NOT NULL REFERENCES safety_keyword(id) ON DELETE CASCADE,
    synonym     VARCHAR(100) NOT NULL,
    UNIQUE (keyword_id, synonym)
);
CREATE INDEX idx_safety_keyword_synonym ON safety_keyword_synonym(synonym);

-- ============================================================
-- 4.2.3 safety_incident_keyword — 사고-키워드 매핑
-- ============================================================
CREATE TABLE safety_incident_keyword (
    incident_id BIGINT NOT NULL REFERENCES safety_incident(id) ON DELETE CASCADE,
    keyword_id  BIGINT NOT NULL REFERENCES safety_keyword(id)  ON DELETE RESTRICT,
    weight      NUMERIC(5,2) NOT NULL DEFAULT 1.00,
    PRIMARY KEY (incident_id, keyword_id)
);
CREATE INDEX idx_safety_incident_keyword_kw ON safety_incident_keyword(keyword_id);

-- ============================================================
-- 4.2.4 company_safety_profile — 기업 안전 프로필
-- ============================================================
CREATE TABLE company_safety_profile (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    industry_code   VARCHAR(20)  NOT NULL,
    sub_industry    VARCHAR(50),
    employee_count  INT,
    primary_process VARCHAR(100),
    hazard_factors  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    risk_score      NUMERIC(5,2),
    risk_grade      VARCHAR(2),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (company_id)
);
CREATE INDEX idx_company_safety_profile_industry ON company_safety_profile(industry_code, risk_grade);

-- ============================================================
-- 4.2.5 safety_match_result — 매칭 결과 (TTL 캐시)
-- ============================================================
CREATE TABLE safety_match_result (
    id                 BIGSERIAL PRIMARY KEY,
    company_profile_id BIGINT NOT NULL REFERENCES company_safety_profile(id) ON DELETE CASCADE,
    incident_id        BIGINT NOT NULL REFERENCES safety_incident(id)         ON DELETE CASCADE,
    similarity_score   NUMERIC(5,2) NOT NULL,
    match_reason       JSONB        NOT NULL,
    generated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ  NOT NULL DEFAULT (now() + INTERVAL '1 hour')
);
CREATE INDEX idx_safety_match_profile  ON safety_match_result(company_profile_id, generated_at DESC);
CREATE INDEX idx_safety_match_expires  ON safety_match_result(expires_at);

-- ============================================================
-- 4.2.6 safety_guideline_template — 가이드라인 템플릿
-- ============================================================
CREATE TABLE safety_guideline_template (
    id                        BIGSERIAL PRIMARY KEY,
    code                      VARCHAR(50)  NOT NULL UNIQUE,
    name                      VARCHAR(200) NOT NULL,
    description               TEXT,
    applicable_industry_codes TEXT[]       NOT NULL DEFAULT ARRAY[]::TEXT[],
    applicable_grades         TEXT[]       NOT NULL DEFAULT ARRAY[]::TEXT[],
    structure                 JSONB        NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    version                   VARCHAR(20)  NOT NULL DEFAULT 'v1.0',
    review_status             VARCHAR(20),
    reviewed_by               BIGINT REFERENCES users(id),
    reviewed_at               TIMESTAMPTZ,
    created_by                BIGINT REFERENCES users(id),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_safety_template_status ON safety_guideline_template(status, version);

-- ============================================================
-- 4.2.7 safety_guideline_report — 생성된 보고서
-- ============================================================
CREATE TABLE safety_guideline_report (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    company_profile_id      BIGINT NOT NULL REFERENCES company_safety_profile(id) ON DELETE CASCADE,
    template_id             BIGINT NOT NULL REFERENCES safety_guideline_template(id),
    risk_grade              VARCHAR(2)   NOT NULL,
    matched_incidents_jsonb JSONB        NOT NULL,
    content_html            TEXT         NOT NULL,
    content_pdf_path        VARCHAR(500),
    generated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    accessed_count          INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_safety_report_profile ON safety_guideline_report(company_profile_id, generated_at DESC);
CREATE INDEX idx_safety_report_uuid    ON safety_guideline_report(uuid);

-- ============================================================
-- 4.2.8 safety_checklist_item — 체크리스트 항목
-- ============================================================
CREATE TABLE safety_checklist_item (
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES safety_guideline_template(id) ON DELETE CASCADE,
    category    VARCHAR(50) NOT NULL,
    item_text   TEXT        NOT NULL,
    severity    VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    sort_order  INT         NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX idx_safety_checklist_template ON safety_checklist_item(template_id, sort_order);

-- ============================================================
-- 4.2.9 safety_check_result — 체크 결과
-- ============================================================
CREATE TABLE safety_check_result (
    id                       BIGSERIAL PRIMARY KEY,
    report_id                BIGINT NOT NULL REFERENCES safety_guideline_report(id) ON DELETE CASCADE,
    item_id                  BIGINT NOT NULL REFERENCES safety_checklist_item(id),
    checked_by               BIGINT REFERENCES users(id),
    status                   VARCHAR(20) NOT NULL,
    evidence_text            TEXT,
    evidence_attachment_uuid UUID,
    checked_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (report_id, item_id)
);
CREATE INDEX idx_safety_check_result_report ON safety_check_result(report_id, status);
