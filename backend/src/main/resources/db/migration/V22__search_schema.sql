-- SPEC-CMS-010 v0.1: 통합 검색 (검색 로그·인기 검색어 캐시·동의어 사전)
-- Step 1: 3개 신규 테이블 + retention_policy 시드 2건
-- 의존: V18 (retention_policy 인프라), V2 (users)
-- REQ: REQ-SEARCH-006, REQ-SEARCH-007, REQ-SEARCH-008, REQ-SEARCH-009

-- ─── 1. search_log (검색 로그, REQ-SEARCH-008) ──────────────────────────────
-- 사용자/세션/쿼리/응답시간/클릭 추적용 시계열 INSERT-ONLY 테이블
CREATE TABLE search_log (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NULL REFERENCES users(id) ON DELETE SET NULL, -- 비로그인 시 NULL
    session_id        VARCHAR(64)  NOT NULL,                      -- 비로그인 추적용 (쿠키 기반)
    query             VARCHAR(200) NOT NULL,                      -- 원본 쿼리
    normalized_query  VARCHAR(200) NOT NULL,                      -- 공백제거+소문자 정규화 (집계 키)
    expanded_query    VARCHAR(500) NULL,                          -- 동의어 확장 후 (REQ-SEARCH-009)
    result_count      INTEGER      NOT NULL DEFAULT 0,
    response_ms       INTEGER      NOT NULL DEFAULT 0,
    clicked_doc_type  VARCHAR(30)  NULL,                          -- board/content/policy/safety/media/publication
    clicked_doc_id    BIGINT       NULL,
    clicked_at        TIMESTAMPTZ  NULL,
    clicked_rank      INTEGER      NULL,                          -- 클릭된 결과의 순위 (1=최상위)
    locale            VARCHAR(10)  NOT NULL DEFAULT 'ko',
    domain_filter     VARCHAR(20)  NOT NULL DEFAULT 'ALL',        -- ALL/board/content/policy/safety/media/publication
    ip_hash           VARCHAR(64)  NULL,                          -- SHA-256
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sl_locale CHECK (locale IN ('ko','en')),
    CONSTRAINT chk_sl_domain CHECK (domain_filter IN ('ALL','board','content','policy','safety','media','publication')),
    CONSTRAINT chk_sl_clicked CHECK (
        (clicked_doc_type IS NULL AND clicked_doc_id IS NULL AND clicked_at IS NULL)
        OR (clicked_doc_type IS NOT NULL AND clicked_doc_id IS NOT NULL AND clicked_at IS NOT NULL)
    )
);

COMMENT ON TABLE  search_log                   IS 'SPEC-CMS-010 검색 로그 (REQ-SEARCH-008)';
COMMENT ON COLUMN search_log.normalized_query  IS '공백제거+소문자 정규화 (인기 검색어 집계 키)';
COMMENT ON COLUMN search_log.expanded_query    IS '동의어 확장 후 ts_query 문자열';
COMMENT ON COLUMN search_log.ip_hash           IS 'SHA-256 해시 (PII 보호)';

-- 일별 집계용 BRIN (시계열 INSERT-ONLY 최적, RISK-S-03 대용량 대응)
CREATE INDEX idx_sl_created_brin ON search_log USING BRIN(created_at);
-- normalized_query 집계용 (인기 검색어 배치)
CREATE INDEX idx_sl_normalized_time ON search_log(normalized_query, created_at DESC);
-- 사용자별 검색 이력 조회용
CREATE INDEX idx_sl_user_time      ON search_log(user_id, created_at DESC) WHERE user_id IS NOT NULL;
-- 0건 검색 모니터링용
CREATE INDEX idx_sl_zero_result    ON search_log(created_at DESC) WHERE result_count = 0;

-- ─── 2. search_popular_cache (인기 검색어 캐시, REQ-SEARCH-006/007) ─────────
-- 일/주/월별 정규화된 쿼리 빈도 캐시. 배치가 UPSERT 한다.
CREATE TABLE search_popular_cache (
    id              BIGSERIAL    PRIMARY KEY,
    period_type     VARCHAR(10)  NOT NULL,                      -- DAILY/WEEKLY/MONTHLY
    period_date     DATE         NOT NULL,                      -- DAILY=대상일, WEEKLY=주 시작 월요일, MONTHLY=월 1일
    locale          VARCHAR(10)  NOT NULL DEFAULT 'ko',
    query           VARCHAR(200) NOT NULL,                      -- normalized_query
    search_count    BIGINT       NOT NULL DEFAULT 0,
    rank            INTEGER      NOT NULL,                      -- 1..N (period_type, period_date, locale 내 순위)
    refreshed_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_spc_period_locale_query UNIQUE (period_type, period_date, locale, query),
    CONSTRAINT chk_spc_period CHECK (period_type IN ('DAILY','WEEKLY','MONTHLY')),
    CONSTRAINT chk_spc_locale CHECK (locale IN ('ko','en')),
    CONSTRAINT chk_spc_rank   CHECK (rank > 0)
);

COMMENT ON TABLE search_popular_cache IS 'SPEC-CMS-010 인기 검색어 캐시 (REQ-SEARCH-006/007)';

CREATE INDEX idx_spc_lookup    ON search_popular_cache(period_type, period_date, locale, rank);
CREATE INDEX idx_spc_refreshed ON search_popular_cache(refreshed_at DESC);

-- ─── 3. search_synonym (동의어 사전, REQ-SEARCH-009) ───────────────────────
-- 운영자가 등록한 동의어로 OR 쿼리 확장. soft delete (status=PAUSED).
CREATE TABLE search_synonym (
    id          BIGSERIAL    PRIMARY KEY,
    term        VARCHAR(100) NOT NULL,                          -- 검색어 (사용자 입력)
    synonym     VARCHAR(100) NOT NULL,                          -- 확장 동의어 (OR 매칭)
    locale      VARCHAR(10)  NOT NULL DEFAULT 'ko',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    description TEXT         NULL,                              -- 등록 사유
    created_by  BIGINT       NULL REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT       NULL REFERENCES users(id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ss_term_synonym_locale UNIQUE (term, synonym, locale),
    CONSTRAINT chk_ss_status CHECK (status IN ('ACTIVE','PAUSED')),
    CONSTRAINT chk_ss_locale CHECK (locale IN ('ko','en')),
    CONSTRAINT chk_ss_self   CHECK (term <> synonym)
);

COMMENT ON TABLE search_synonym IS 'SPEC-CMS-010 동의어 사전 (REQ-SEARCH-009)';

-- 쿼리 확장용 룩업 (term 기준 IN 절)
CREATE INDEX idx_ss_term_locale_status ON search_synonym(term, locale, status);

-- ─── 4. retention_policy 시드 (SPEC-CMS-009 통합, §4.5) ─────────────────────
-- search_log 6개월 / search_popular_cache 24개월 보존.
-- §7.1 분산: MonthlyJob 05:30 과의 락 경합 방지를 위해 RetentionJob은 05:35.
INSERT INTO retention_policy (target_table, policy_type, retention_months, schedule_cron, status, description)
VALUES
  ('search_log',           'DELETE', 6,  '0 35 5 1 * *', 'ACTIVE', 'SPEC-CMS-010 REQ-SEARCH-008 검색 로그 6개월 보존'),
  ('search_popular_cache', 'DELETE', 24, '0 40 5 1 * *', 'ACTIVE', 'SPEC-CMS-010 인기 검색어 캐시 24개월 보존')
ON CONFLICT (target_table) DO NOTHING;
