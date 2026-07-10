-- SPEC-CMS-POINTS-001 — 게시판/댓글 참여 포인트 지급 시스템
-- 적립 전용(earn-only) 원장 + 비정규화 요약 + 좋아요 중복 방지 테이블 + 정책/권한 시드.
-- 기존 system_setting(key-value) 재사용으로 별도 정책 테이블 없음.

-- ─────────────────────────────────────────────────────────
-- 1. user_point_ledger — append-only 적립 거래 로그 (감사/조회 단일 진실 원천)
-- ─────────────────────────────────────────────────────────
CREATE TABLE user_point_ledger (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type    VARCHAR(30)  NOT NULL
                  CONSTRAINT chk_point_event_type
                      CHECK (event_type IN ('POST_CREATED', 'COMMENT_CREATED', 'LIKE_GIVEN')),
    reference_id  BIGINT,
    points        INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_point_ledger_user       ON user_point_ledger(user_id);
CREATE INDEX idx_point_ledger_event      ON user_point_ledger(event_type);
CREATE INDEX idx_point_ledger_created_at ON user_point_ledger(created_at);

COMMENT ON TABLE  user_point_ledger            IS 'SPEC-CMS-POINTS-001 적립 전용 포인트 원장';
COMMENT ON COLUMN user_point_ledger.event_type IS 'POST_CREATED/COMMENT_CREATED/LIKE_GIVEN';
COMMENT ON COLUMN user_point_ledger.reference_id IS '원인 행위 식별자(post_id/comment_id 등)';

-- ─────────────────────────────────────────────────────────
-- 2. user_point_summary — 사용자별 누적 총액 비정규화 (총액 조회 성능)
-- ─────────────────────────────────────────────────────────
CREATE TABLE user_point_summary (
    user_id      BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    total_points BIGINT       NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_point_summary IS 'SPEC-CMS-POINTS-001 사용자별 누적 포인트 요약';

-- ─────────────────────────────────────────────────────────
-- 3. bbs_post_like — 1인 1게시글 좋아요 추적 (UNIQUE 제약으로 중복 적립 차단)
-- ─────────────────────────────────────────────────────────
CREATE TABLE bbs_post_like (
    id          BIGSERIAL    PRIMARY KEY,
    post_id     BIGINT       NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bbs_post_like UNIQUE (user_id, post_id)
);

CREATE INDEX idx_bbs_post_like_post ON bbs_post_like(post_id);

COMMENT ON TABLE bbs_post_like IS 'SPEC-CMS-POINTS-001 게시글 좋아요(1인 1게시글) 추적';

-- ─────────────────────────────────────────────────────────
-- 4. system_setting 정책 시드 (안전 기본값: 비활성 + 0점 — 기존 거동 무영향)
-- ─────────────────────────────────────────────────────────
INSERT INTO system_setting (key, value, value_type, description, created_at, updated_at)
VALUES
    ('POINTS:ENABLED',         'false', 'BOOL', '참여 포인트 시스템 활성화 여부',       NOW(), NOW()),
    ('POINTS:POST_CREATED',    '0',     'INT',  '게시글 작성 시 적립 포인트',          NOW(), NOW()),
    ('POINTS:COMMENT_CREATED', '0',     'INT',  '댓글 작성 시 적립 포인트',            NOW(), NOW()),
    ('POINTS:LIKE_GIVEN',      '0',     'INT',  '게시글 좋아요(최초 1회) 시 적립 포인트', NOW(), NOW())
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- 5. 권한 시드 (V6 permissions: action CHECK(READ|WRITE|DELETE|EXECUTE|ADMIN))
-- ─────────────────────────────────────────────────────────
INSERT INTO permissions (code, resource, action, description, created_at)
VALUES
    ('POINTS:READ',  'POINTS', 'READ',  '포인트 내역 조회 권한',   NOW()),
    ('POINTS:WRITE', 'POINTS', 'WRITE', '포인트 정책 관리 권한',   NOW())
ON CONFLICT (code) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- 6. SUPER_ADMIN 역할에 권한 매핑 (V6 role_permissions: role_code, permission_code)
-- ─────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', p.code
FROM permissions p
WHERE p.code IN ('POINTS:READ', 'POINTS:WRITE')
ON CONFLICT (role_code, permission_code) DO NOTHING;
