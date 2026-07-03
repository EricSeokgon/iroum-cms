-- SPEC-CMS-REVIEW-001: 게시물 별점 리뷰 시스템 — 테이블 + bbs_post 집계 컬럼 + RBAC 권한/메뉴 시드
-- REQ-REV-001~013
-- 주의: 실제 스키마 컬럼명 검증 완료 (permissions.code, roles.code, role_permissions.role_code/permission_code,
--       admin_menu.menu_key/name/parent_key/route_path/sort_order/icon/is_active,
--       admin_menu_permissions.menu_key/permission_code).
--       모든 시드 INSERT 에 ON CONFLICT DO NOTHING 적용 → 멱등 재실행 보장.

-- ─────────────────────────────────────────────────────────
-- 1. bbs_post_review (게시물 별점 리뷰 — bbs_comment 와 분리된 독립 테이블)
--    다중 리뷰 허용 → (post_id, author_id) UNIQUE 제약 두지 않음 (REQ-REV-002)
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bbs_post_review (
    id          BIGSERIAL    PRIMARY KEY,
    post_id     BIGINT       NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    author_id   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    rating      SMALLINT     NOT NULL
                CONSTRAINT chk_bbs_post_review_rating CHECK (rating BETWEEN 1 AND 5),
    content     TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE'
                CONSTRAINT chk_bbs_post_review_status CHECK (status IN ('VISIBLE','HIDDEN','DELETED')),
    ip_address  INET,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

COMMENT ON TABLE  bbs_post_review            IS 'SPEC-CMS-REVIEW-001 게시물 별점 리뷰 (다중 리뷰 허용, status: VISIBLE/HIDDEN/DELETED)';
COMMENT ON COLUMN bbs_post_review.rating     IS '1~5 정수 별점 (REQ-REV-008)';
COMMENT ON COLUMN bbs_post_review.status     IS 'VISIBLE | HIDDEN | DELETED — DELETED 는 비가역 (REQ-REV-006)';
COMMENT ON COLUMN bbs_post_review.deleted_at IS 'DELETED 전이 시각';

CREATE INDEX IF NOT EXISTS idx_bbs_post_review_post_status ON bbs_post_review(post_id, status);
CREATE INDEX IF NOT EXISTS idx_bbs_post_review_author      ON bbs_post_review(author_id);
CREATE INDEX IF NOT EXISTS idx_bbs_post_review_created     ON bbs_post_review(created_at DESC);

-- ─────────────────────────────────────────────────────────
-- 2. bbs_post 집계 컬럼 (additive) — review_count, average_rating
--    VISIBLE 리뷰 기준 집계, 서비스 계층(ReviewServiceImpl)에서 full-recompute (REQ-REV-003)
-- ─────────────────────────────────────────────────────────
ALTER TABLE bbs_post ADD COLUMN IF NOT EXISTS review_count   INT          NOT NULL DEFAULT 0;
ALTER TABLE bbs_post ADD COLUMN IF NOT EXISTS average_rating DECIMAL(3,1) NOT NULL DEFAULT 0.0;

COMMENT ON COLUMN bbs_post.review_count   IS 'SPEC-CMS-REVIEW-001 VISIBLE 리뷰 수 (서비스 계층 집계)';
COMMENT ON COLUMN bbs_post.average_rating IS 'SPEC-CMS-REVIEW-001 VISIBLE 리뷰 평균 별점 0.0~5.0 (서비스 계층 집계)';

-- ─────────────────────────────────────────────────────────
-- 3. REVIEW 권한 시드 (3개) — action 은 CHECK(READ/WRITE/DELETE/EXECUTE/ADMIN) 준수
-- ─────────────────────────────────────────────────────────
INSERT INTO permissions (code, resource, action, description) VALUES
    ('REVIEW:READ',   'REVIEW', 'READ',   '리뷰 조회'),
    ('REVIEW:WRITE',  'REVIEW', 'WRITE',  '리뷰 작성'),
    ('REVIEW:DELETE', 'REVIEW', 'DELETE', '리뷰 숨김·삭제')
ON CONFLICT (code) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- 4. 역할 × 권한 매핑 시드
--    ADMIN/SUPER_ADMIN → 세 권한 모두 (전체 관리)
--    CONTENT_ADMIN     → REVIEW:READ/DELETE (콘텐츠 모더레이션)
--    (SUPER_ADMIN 은 V6 에서 전체 권한 자동 부여되나 명시 매핑으로 안전성 확보)
-- ─────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('ADMIN', 'SUPER_ADMIN')
  AND p.code IN ('REVIEW:READ', 'REVIEW:WRITE', 'REVIEW:DELETE')
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'CONTENT_ADMIN'
  AND p.code IN ('REVIEW:READ', 'REVIEW:DELETE')
ON CONFLICT (role_code, permission_code) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- 5. 어드민 메뉴 시드 — '리뷰 관리' (board 그룹 하위, route /admin/reviews)
--    필요 권한: REVIEW:READ
-- ─────────────────────────────────────────────────────────
INSERT INTO admin_menu (menu_key, name, parent_key, route_path, sort_order, icon, is_active) VALUES
    ('board.reviews', '리뷰 관리', 'board', '/admin/reviews', 64, NULL, TRUE)
ON CONFLICT (menu_key) DO NOTHING;

INSERT INTO admin_menu_permissions (menu_key, permission_code) VALUES
    ('board.reviews', 'REVIEW:READ')
ON CONFLICT (menu_key, permission_code) DO NOTHING;
