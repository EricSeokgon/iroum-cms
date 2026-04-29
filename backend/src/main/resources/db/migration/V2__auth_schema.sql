-- SPEC-CMS-002 인증·권한 스키마 마이그레이션 (v0.3.2)
-- REQ-AUTH-001~007, Q-4 SYSADMIN alias 정책 적용 (2026-04-29)
--
-- 주의: V1에서 pgcrypto, pg_trgm 확장이 이미 설치됨.

-- ─────────────────────────────────────────────────────────
-- 1. users (사용자 마스터)
-- ─────────────────────────────────────────────────────────
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    username            VARCHAR(50)  NOT NULL UNIQUE,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(72)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED','DELETED')),
    fail_count          INT          NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

COMMENT ON TABLE  users                      IS 'SPEC-CMS-002 REQ-AUTH-006 사용자 마스터';
COMMENT ON COLUMN users.uuid                 IS '외부 노출용 UUID (내부 PK는 bigint id)';
COMMENT ON COLUMN users.email                IS 'RED 단계: 평문. GREEN에서 AES-256-GCM 암호화(REQ-CROSS-002)';
COMMENT ON COLUMN users.password_hash        IS 'BCrypt strength=12 해시 (REQ-AUTH-004)';
COMMENT ON COLUMN users.status               IS 'ACTIVE/INACTIVE/LOCKED/DELETED';
COMMENT ON COLUMN users.fail_count           IS '연속 로그인 실패 횟수 — 5회 초과 시 LOCKED (REQ-AUTH-005)';

CREATE INDEX idx_users_status        ON users(status)       WHERE deleted_at IS NULL;
CREATE INDEX idx_users_locked_until  ON users(locked_until) WHERE locked_until IS NOT NULL;

-- ─────────────────────────────────────────────────────────
-- 2. roles (역할 마스터)  — v0.3.2 aliased_to 컬럼 포함
-- ─────────────────────────────────────────────────────────
CREATE TABLE roles (
    code        VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    aliased_to  VARCHAR(50)  REFERENCES roles(code) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  roles            IS 'SPEC-CMS-002 REQ-AUTH-007 역할 (Q-4 alias 적용)';
COMMENT ON COLUMN roles.is_system  IS '시스템 기본 역할 — 삭제 금지';
COMMENT ON COLUMN roles.aliased_to IS 'NULL=실제 역할. NOT NULL=alias — aliased_to 코드의 권한 집합으로 해석 (Q-4, 2026-04-29)';

CREATE INDEX idx_roles_aliased_to ON roles(aliased_to) WHERE aliased_to IS NOT NULL;

-- 역할 시드 데이터 (v0.3.2)
INSERT INTO roles (code, name, description, is_system, aliased_to) VALUES
    ('SUPER_ADMIN',  '시스템관리자',           '최고 관리자 권한',                            TRUE,  NULL),
    ('SYSADMIN',     '시스템관리자(legacy)',    'v0.1 alias for SUPER_ADMIN (Q-4, 2026-04-29)', TRUE,  'SUPER_ADMIN'),
    ('DEPT_ADMIN',   '부서관리자',             '부서 단위 관리 권한',                          TRUE,  NULL),
    ('EDITOR',       '편집자',                '콘텐츠 편집 권한',                             TRUE,  NULL),
    ('VIEWER',       '조회자',                '읽기 전용 권한',                               TRUE,  NULL);

-- ─────────────────────────────────────────────────────────
-- 3. user_roles (사용자-역할 N:M)
-- ─────────────────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_code  VARCHAR(50) NOT NULL REFERENCES roles(code) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by BIGINT      REFERENCES users(id),
    PRIMARY KEY (user_id, role_code)
);

CREATE INDEX idx_user_roles_role ON user_roles(role_code);

-- ─────────────────────────────────────────────────────────
-- 4. password_history (비밀번호 이력 — 재사용 금지)
-- ─────────────────────────────────────────────────────────
CREATE TABLE password_history (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(72) NOT NULL,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_history_user ON password_history(user_id, changed_at DESC);

COMMENT ON TABLE password_history IS 'SPEC-CMS-002 REQ-AUTH-009 비밀번호 재사용 금지 — 직전 5개 비교';

-- ─────────────────────────────────────────────────────────
-- 5. login_history (로그인 이력)
-- ─────────────────────────────────────────────────────────
CREATE TABLE login_history (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      REFERENCES users(id),
    username       VARCHAR(50),
    ip_address     VARCHAR(45),
    user_agent     TEXT,
    success        BOOLEAN     NOT NULL,
    failure_reason VARCHAR(50),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_history_user     ON login_history(user_id, created_at DESC);
CREATE INDEX idx_login_history_username ON login_history(username, created_at DESC);

COMMENT ON TABLE login_history          IS 'SPEC-CMS-002 REQ-AUTH-011 로그인 이력';
COMMENT ON COLUMN login_history.user_id IS 'NULL 허용 — 존재하지 않는 사용자 실패 시도 기록';

-- ─────────────────────────────────────────────────────────
-- 6. refresh_tokens (Refresh Token Rotation)
-- ─────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens(user_id)    WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_expires     ON refresh_tokens(expires_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE  refresh_tokens           IS 'SPEC-CMS-002 REQ-AUTH-002 Refresh Token Rotation';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256(Refresh JWT) — 원본 저장 없이 해시만 보존';

-- ─────────────────────────────────────────────────────────
-- 7. token_blacklist (Access Token 블랙리스트)
-- ─────────────────────────────────────────────────────────
CREATE TABLE token_blacklist (
    token_hash VARCHAR(64)  PRIMARY KEY,
    revoked_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_token_blacklist_expires ON token_blacklist(expires_at);

COMMENT ON TABLE  token_blacklist           IS 'SPEC-CMS-002 REQ-AUTH-003 로그아웃 토큰 블랙리스트';
COMMENT ON COLUMN token_blacklist.token_hash IS 'SHA-256(Access JWT) — expires_at 이후 GC 대상';
