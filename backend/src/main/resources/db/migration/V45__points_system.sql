-- SPEC-CMS-POINTS-001: 게시판/댓글 참여 포인트 지급 시스템

-- 포인트 원장 (지급/차감 이력)
CREATE TABLE user_point_ledger (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    delta       INT          NOT NULL,
    reason      VARCHAR(50)  NOT NULL,
    ref_type    VARCHAR(20)  NOT NULL,
    ref_id      BIGINT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_upl_user_id ON user_point_ledger (user_id);
CREATE INDEX idx_upl_created_at ON user_point_ledger (created_at DESC);

-- 사용자별 포인트 합계
CREATE TABLE user_point_summary (
    user_id      BIGINT PRIMARY KEY,
    total_points INT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 게시글 좋아요
CREATE TABLE bbs_post_like (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    post_id    BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bbs_post_like UNIQUE (user_id, post_id)
);

CREATE INDEX idx_bpl_post_id ON bbs_post_like (post_id);

-- 포인트 정책 시드 (기본값: 비활성, 포인트 0)
INSERT INTO system_setting (key, value, value_type, description) VALUES
    ('POINTS:ENABLED',          'false', 'BOOL',   '포인트 지급 활성화 여부'),
    ('POINTS:POST_CREATED',     '0',     'INT',    '게시글 작성 시 지급 포인트'),
    ('POINTS:COMMENT_CREATED',  '0',     'INT',    '댓글 작성 시 지급 포인트'),
    ('POINTS:LIKE_GIVEN',       '0',     'INT',    '게시글 좋아요 시 지급 포인트')
ON CONFLICT (key) DO NOTHING;

-- RBAC 권한 추가
INSERT INTO permissions (code, resource, action, description) VALUES
    ('POINTS:READ',  'POINTS', 'READ',  '포인트 정책/이력 조회'),
    ('POINTS:WRITE', 'POINTS', 'WRITE', '포인트 정책 관리')
ON CONFLICT (code) DO NOTHING;
