-- SPEC-CMS-RBAC-001 REQ-RBAC-002 — 어드민 사이드바 메뉴 카탈로그 + 메뉴↔권한 매핑
-- 중대 주의: V13 menu/menu_permissions(공개 사이트 CMS 네비게이션)와 의미·물리 분리.
--            본 마이그레이션은 별도 테이블 admin_menu/admin_menu_permissions 만 다룬다.
--            기존 menu/menu_permissions 는 절대 ALTER/DROP 하지 않는다.

-- ─────────────────────────────────────────────────────────
-- 1. admin_menu (어드민 사이드바 메뉴 카탈로그)
-- ─────────────────────────────────────────────────────────
CREATE TABLE admin_menu (
    menu_key   VARCHAR(60)  PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    parent_key VARCHAR(60)  REFERENCES admin_menu(menu_key) ON DELETE CASCADE,
    route_path VARCHAR(200),
    sort_order INT          NOT NULL DEFAULT 0,
    icon       VARCHAR(60),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE  admin_menu            IS 'SPEC-CMS-RBAC-001 REQ-RBAC-002 어드민 사이드바 메뉴 카탈로그 (V13 menu 와 분리)';
COMMENT ON COLUMN admin_menu.menu_key   IS '메뉴 고유키 (예: system.roles)';
COMMENT ON COLUMN admin_menu.parent_key IS 'NULL=최상위. NOT NULL=상위 메뉴 (self FK, ON DELETE CASCADE)';
COMMENT ON COLUMN admin_menu.route_path IS 'Vue 라우트 경로. 그룹 메뉴는 NULL';

CREATE INDEX idx_admin_menu_parent ON admin_menu(parent_key) WHERE parent_key IS NOT NULL;

-- ─────────────────────────────────────────────────────────
-- 2. admin_menu_permissions (메뉴↔권한 매핑, OR 의미)
--    한 메뉴에 매핑된 권한 중 하나라도 보유하면 접근 가능.
--    매핑이 하나도 없는 메뉴는 인증된 모든 관리자에게 노출.
-- ─────────────────────────────────────────────────────────
CREATE TABLE admin_menu_permissions (
    menu_key        VARCHAR(60)  NOT NULL REFERENCES admin_menu(menu_key) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code)    ON DELETE CASCADE,
    CONSTRAINT uq_admin_menu_perm UNIQUE (menu_key, permission_code)
);

COMMENT ON TABLE admin_menu_permissions IS 'SPEC-CMS-RBAC-001 REQ-RBAC-002 메뉴별 접근 권한 요건 (OR 의미)';

CREATE INDEX idx_admin_menu_perm_menu ON admin_menu_permissions(menu_key);

-- ─────────────────────────────────────────────────────────
-- 3. 어드민 메뉴 카탈로그 시드 (현재 AdminLayout.vue 사이드바 구조 반영)
-- ─────────────────────────────────────────────────────────
INSERT INTO admin_menu (menu_key, name, parent_key, route_path, sort_order, icon, is_active) VALUES
    -- 최상위 단일 메뉴
    ('dashboard',                 '대시보드',        NULL, '/system/dashboard',          10, 'HomeFilled',       TRUE),
    ('users',                     '사용자 관리',     NULL, '/users',                     20, 'User',             TRUE),
    ('organizations',             '조직 관리',       NULL, '/organizations',             30, 'OfficeBuilding',   TRUE),
    -- 역할/권한 관리 (ROLE:READ)
    ('system.roles',              '역할/권한 관리',  NULL, '/roles',                     40, 'Lock',             TRUE),
    -- 감사 그룹
    ('audit',                     '감사',            NULL, NULL,                         50, 'DocumentChecked',  TRUE),
    ('audit.permission_changes',  '권한 변경 이력',  'audit', '/audit/permission-changes', 51, NULL,            TRUE),
    ('audit.personal_data',       '회원정보 접근 이력', 'audit', '/audit/personal-data-access', 52, NULL,        TRUE),
    ('audit.login_history',       '로그인 이력',     'audit', '/audit/login-history',     53, NULL,              TRUE),
    -- 콘텐츠/게시판 그룹
    ('board',                     '게시판/미디어',   NULL, NULL,                         60, 'Document',         TRUE),
    ('board.masters',             '게시판 관리',     'board', '/board/masters',          61, NULL,               TRUE),
    ('board.media',               '미디어 라이브러리', 'board', '/media',                62, NULL,               TRUE),
    -- 콘텐츠 관리 그룹
    ('content',                   '콘텐츠 관리',     NULL, NULL,                         70, 'Grid',             TRUE),
    ('content.pages',             '페이지 목록',     'content', '/content/pages',        71, NULL,               TRUE),
    ('content.menus',             '메뉴 관리',       'content', '/content/menus',        72, NULL,               TRUE),
    -- 시스템 관리 그룹
    ('system',                    '시스템 관리',     NULL, NULL,                         80, 'Setting',          TRUE),
    ('system.codes',              '공통 코드 관리',  'system', '/system/codes',          81, NULL,               TRUE),
    ('system.settings',           '시스템 설정',     'system', '/system/settings',       82, NULL,               TRUE),
    -- 알림
    ('notifications',             '알림 센터',       NULL, '/notification-center',       90, 'Bell',             TRUE);

-- ─────────────────────────────────────────────────────────
-- 4. 메뉴↔권한 매핑 시드
--    매핑이 없는 메뉴(dashboard, users, organizations, board.*, content.*, system.codes/settings,
--    notifications, audit.permission_changes 의 자식 외 일부)는 인증된 모든 관리자에게 노출(OR 의미 무제한).
-- ─────────────────────────────────────────────────────────
INSERT INTO admin_menu_permissions (menu_key, permission_code) VALUES
    -- 역할/권한 관리: ROLE:READ
    ('system.roles',             'ROLE:READ'),
    -- 감사 그룹 및 항목: AUDIT:READ
    ('audit',                    'AUDIT:READ'),
    ('audit.permission_changes', 'AUDIT:READ'),
    ('audit.personal_data',      'AUDIT:READ'),
    ('audit.login_history',      'AUDIT:READ'),
    -- 게시판 마스터 관리: ROLE:READ (운영 정책상 상위 관리자 전용)
    ('board.masters',            'ROLE:READ');
