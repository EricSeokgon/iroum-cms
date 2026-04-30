-- SPEC-CMS-005 v0.4: Bundle D 통계·로그·시스템관리 스키마
-- REQ-SYSTEM-001~006 핵심 테이블 6개 + 권한 시드 9개 + 공통코드 시드
-- Step 1: access_log(월별 PARTITION) / access_stat_daily / access_stat_monthly /
--         code_group / code / system_setting / maintenance

-- ─── 1. access_log (월별 PARTITION 테이블) ────────────────────────────────────
-- REQ-SYSTEM-001-D: 접속 로그 적재 + IP SHA-256 익명화
CREATE TABLE access_log (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,
    site_id          BIGINT       NOT NULL DEFAULT 1,
    user_id          BIGINT,
    session_id       VARCHAR(128),
    ip_hash          CHAR(64)     NOT NULL,       -- SHA-256 hex
    user_agent       TEXT,
    referrer         TEXT,
    page_url         TEXT         NOT NULL,
    status_code      SMALLINT     NOT NULL,
    response_time_ms INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  access_log              IS '접속 로그 (월별 파티션). REQ-SYSTEM-001-D';
COMMENT ON COLUMN access_log.ip_hash      IS 'SHA-256(IP + SALT) — 개인정보보호법 IP 익명화';
COMMENT ON COLUMN access_log.session_id   IS '세션 식별자 (unique_visitors 집계용)';
COMMENT ON COLUMN access_log.created_at   IS '파티션 키 — 월별 RANGE PARTITION';

-- 초기 파티션: 2026-04 ~ 2026-05
CREATE TABLE access_log_y2026m04 PARTITION OF access_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE access_log_y2026m05 PARTITION OF access_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

-- 인덱스 (파티션 테이블 — 각 파티션에 자동 전파)
CREATE INDEX idx_access_log_site_created ON access_log (site_id, created_at DESC);
CREATE INDEX idx_access_log_page_url     ON access_log (page_url, created_at DESC);
CREATE INDEX idx_access_log_status       ON access_log (status_code, created_at DESC);

-- ─── 2. access_stat_daily (일별 집계) ────────────────────────────────────────
-- REQ-SYSTEM-002-D: 일별 배치 집계 결과 저장
CREATE TABLE access_stat_daily (
    stat_date       DATE    NOT NULL,
    site_id         BIGINT  NOT NULL DEFAULT 1,
    total_visits    INT     NOT NULL DEFAULT 0,
    unique_visitors INT     NOT NULL DEFAULT 0,
    unique_sessions INT     NOT NULL DEFAULT 0,
    page_views      INT     NOT NULL DEFAULT 0,
    avg_response_ms INT     NOT NULL DEFAULT 0,
    error_count     INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, site_id)
);

COMMENT ON TABLE access_stat_daily IS '일별 접속 통계 집계. REQ-SYSTEM-002-D';

-- ─── 3. access_stat_monthly (월별 집계) ───────────────────────────────────────
-- REQ-SYSTEM-003-D: 월별 배치 집계 + JSONB Top 페이지/레퍼러/브라우저
CREATE TABLE access_stat_monthly (
    stat_month      CHAR(7)  NOT NULL,           -- YYYY-MM
    site_id         BIGINT   NOT NULL DEFAULT 1,
    total_visits    INT      NOT NULL DEFAULT 0,
    unique_visitors INT      NOT NULL DEFAULT 0,
    page_views      INT      NOT NULL DEFAULT 0,
    avg_response_ms INT      NOT NULL DEFAULT 0,
    error_count     INT      NOT NULL DEFAULT 0,
    top_pages       JSONB    NOT NULL DEFAULT '[]'::jsonb,
    top_referrers   JSONB    NOT NULL DEFAULT '[]'::jsonb,
    top_browsers    JSONB    NOT NULL DEFAULT '[]'::jsonb,
    PRIMARY KEY (stat_month, site_id)
);

COMMENT ON TABLE  access_stat_monthly           IS '월별 접속 통계 집계. REQ-SYSTEM-003-D';
COMMENT ON COLUMN access_stat_monthly.top_pages IS '[{page_url, count}] Top 10 페이지';
COMMENT ON COLUMN access_stat_monthly.top_referrers IS '[{referrer, count}] Top 10 레퍼러';
COMMENT ON COLUMN access_stat_monthly.top_browsers  IS '[{browser, count}] Top 10 브라우저';

-- ─── 4. code_group (공통코드 그룹) ───────────────────────────────────────────
-- REQ-SYSTEM-004-D: 공통코드 그룹 CRUD
CREATE TABLE code_group (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_code  VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_code_group_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

COMMENT ON TABLE  code_group            IS '공통코드 그룹. REQ-SYSTEM-004-D';
COMMENT ON COLUMN code_group.group_code IS '그룹 코드 (예: BOARD_TYPE, USER_STATUS)';

-- ─── 5. code (공통코드) ───────────────────────────────────────────────────────
-- REQ-SYSTEM-004-D: 공통코드 CRUD
CREATE TABLE code (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_code   VARCHAR(50)  NOT NULL REFERENCES code_group(group_code) ON DELETE RESTRICT,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    sort_order   INT          NOT NULL DEFAULT 0,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    extra_data   JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_code_group_code UNIQUE (group_code, code),
    CONSTRAINT chk_code_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

COMMENT ON TABLE  code            IS '공통코드. REQ-SYSTEM-004-D';
COMMENT ON COLUMN code.sort_order IS '정렬 순서 (오름차순 사용)';
COMMENT ON COLUMN code.extra_data IS '추가 메타데이터 (JSONB 자유 필드)';

CREATE INDEX idx_code_group_sort ON code (group_code, sort_order, status);

-- ─── 6. system_setting (시스템 설정 key-value) ─────────────────────────────────
-- REQ-SYSTEM-005-D: 시스템 설정 관리
CREATE TABLE system_setting (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT         NOT NULL,
    value_type  VARCHAR(10)  NOT NULL DEFAULT 'STRING',
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_setting_value_type CHECK (value_type IN ('STRING','INT','BOOL','JSON'))
);

COMMENT ON TABLE  system_setting            IS '시스템 설정 key-value 저장소. REQ-SYSTEM-005-D';
COMMENT ON COLUMN system_setting.value_type IS 'STRING|INT|BOOL|JSON — 값 유효성 검증 타입';

-- ─── 7. maintenance (점검 모드) ───────────────────────────────────────────────
-- REQ-SYSTEM-005-D: 점검 모드 CRUD
CREATE TABLE maintenance (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title              VARCHAR(200) NOT NULL,
    message_ko         TEXT,
    message_en         TEXT,
    start_at           TIMESTAMPTZ  NOT NULL,
    end_at             TIMESTAMPTZ  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    allow_admin_access BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by         BIGINT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_maintenance_status CHECK (status IN ('SCHEDULED','ACTIVE','COMPLETED','CANCELLED')),
    CONSTRAINT chk_maintenance_period CHECK (start_at < end_at)
);

COMMENT ON TABLE  maintenance                   IS '점검 모드 관리. REQ-SYSTEM-005-D';
COMMENT ON COLUMN maintenance.allow_admin_access IS 'false 시 ADMIN도 점검 중 차단';
COMMENT ON COLUMN maintenance.message_ko        IS '사용자에게 보여줄 한국어 점검 메시지';

-- SCHEDULED 상태의 미래 점검만 인덱싱 (활성화 조회 최적화)
CREATE INDEX idx_maintenance_active ON maintenance (start_at, end_at)
    WHERE status IN ('SCHEDULED','ACTIVE');

-- ─── 권한 시드: SYSTEM 도메인 9개 권한 ──────────────────────────────────────────
-- SPEC-CMS-002 permissions 테이블 (V6에 생성된 테이블)에 시드 추가
INSERT INTO permissions (name, description) VALUES
    ('SYSTEM:DASHBOARD',      '운영 대시보드 조회'),
    ('SYSTEM:STATS',          '통계 조회'),
    ('SYSTEM:CODE:READ',      '공통코드 조회'),
    ('SYSTEM:CODE:WRITE',     '공통코드 관리'),
    ('SYSTEM:SETTING:READ',   '시스템 설정 조회'),
    ('SYSTEM:SETTING:WRITE',  '시스템 설정 관리'),
    ('SYSTEM:MAINT:READ',     '점검 관리 조회'),
    ('SYSTEM:MAINT:WRITE',    '점검 관리'),
    ('SYSTEM:LOG:READ',       '접속로그·감사로그 조회')
ON CONFLICT (name) DO NOTHING;

-- ─── 공통코드 그룹 시드 (3개) ─────────────────────────────────────────────────
INSERT INTO code_group (group_code, name, description) VALUES
    ('BOARD_TYPE',          '게시판 유형',    'BBS 게시판 유형 코드'),
    ('USER_STATUS',         '사용자 상태',    '사용자 계정 상태 코드'),
    ('MAINTENANCE_REASON',  '점검 사유',      '점검 모드 사유 분류 코드')
ON CONFLICT (group_code) DO NOTHING;

-- ─── 공통코드 시드 ────────────────────────────────────────────────────────────
-- BOARD_TYPE (4개)
INSERT INTO code (group_code, code, name, sort_order) VALUES
    ('BOARD_TYPE', 'NOTICE',  '공지사항',  1),
    ('BOARD_TYPE', 'FAQ',     'FAQ',       2),
    ('BOARD_TYPE', 'QNA',     '질문답변',  3),
    ('BOARD_TYPE', 'GENERAL', '일반게시판', 4)
ON CONFLICT (group_code, code) DO NOTHING;

-- USER_STATUS (3개)
INSERT INTO code (group_code, code, name, sort_order) VALUES
    ('USER_STATUS', 'ACTIVE',   '정상',   1),
    ('USER_STATUS', 'LOCKED',   '잠금',   2),
    ('USER_STATUS', 'WITHDRAWN','탈퇴',   3)
ON CONFLICT (group_code, code) DO NOTHING;

-- MAINTENANCE_REASON (3개)
INSERT INTO code (group_code, code, name, sort_order) VALUES
    ('MAINTENANCE_REASON', 'DEPLOY',   '배포',     1),
    ('MAINTENANCE_REASON', 'INFRA',    '인프라',   2),
    ('MAINTENANCE_REASON', 'SECURITY', '보안패치', 3)
ON CONFLICT (group_code, code) DO NOTHING;
