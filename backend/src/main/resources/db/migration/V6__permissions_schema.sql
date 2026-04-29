-- REQ-AUTH-013 4단계 RBAC + Permission 카탈로그
-- SPEC-CMS-002 v0.3.2 — REQ-AUTH-013-D-2/4
-- Q-24 DEPT_ADMIN 권한 범위 제한 (자기 부서·자손)

-- ─────────────────────────────────────────────────────────
-- 1. permissions (권한 카탈로그)
-- ─────────────────────────────────────────────────────────
CREATE TABLE permissions (
    code        VARCHAR(100) PRIMARY KEY,
    resource    VARCHAR(50)  NOT NULL,
    action      VARCHAR(20)  NOT NULL
                CONSTRAINT chk_permission_action
                    CHECK (action IN ('READ','WRITE','DELETE','EXECUTE','ADMIN')),
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  permissions         IS 'REQ-AUTH-013-D-2 권한 카탈로그 (resource:action)';
COMMENT ON COLUMN permissions.code    IS 'RESOURCE:ACTION 형식의 고유 권한 코드';
COMMENT ON COLUMN permissions.action  IS 'READ/WRITE/DELETE/EXECUTE/ADMIN';

-- ─────────────────────────────────────────────────────────
-- 2. role_permissions (역할별 권한 매핑)
-- ─────────────────────────────────────────────────────────
CREATE TABLE role_permissions (
    role_code       VARCHAR(50)  NOT NULL REFERENCES roles(code) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    granted_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    granted_by      BIGINT       REFERENCES users(id),
    PRIMARY KEY (role_code, permission_code)
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role_code);
CREATE INDEX idx_role_permissions_perm ON role_permissions(permission_code);

COMMENT ON TABLE role_permissions IS 'REQ-AUTH-013-D-4 역할별 권한 매핑';

-- ─────────────────────────────────────────────────────────
-- 3. 권한 카탈로그 시드 (1차 — User, Organization, Role, Permission, Audit, System)
-- ─────────────────────────────────────────────────────────
INSERT INTO permissions (code, resource, action, description) VALUES
    ('USER:READ',               'USER',         'READ',    '사용자 조회'),
    ('USER:WRITE',              'USER',         'WRITE',   '사용자 생성·수정'),
    ('USER:DELETE',             'USER',         'DELETE',  '사용자 삭제'),
    ('USER:UNLOCK',             'USER',         'EXECUTE', '사용자 잠금 해제'),
    ('USER:FORCE_LOGOUT',       'USER',         'EXECUTE', '강제 로그아웃'),
    ('USER:CHANGE_ROLE',        'USER',         'ADMIN',   '사용자 역할 변경'),
    ('ORGANIZATION:READ',       'ORGANIZATION', 'READ',    '조직 조회'),
    ('ORGANIZATION:WRITE',      'ORGANIZATION', 'WRITE',   '조직 생성·수정'),
    ('ORGANIZATION:DELETE',     'ORGANIZATION', 'DELETE',  '조직 삭제'),
    ('ORGANIZATION:ASSIGN_USER','ORGANIZATION', 'EXECUTE', '사용자 부서 배정'),
    ('ROLE:READ',               'ROLE',         'READ',    '역할 조회'),
    ('ROLE:WRITE',              'ROLE',         'ADMIN',   '역할 생성·수정·삭제'),
    ('PERMISSION:READ',         'PERMISSION',   'READ',    '권한 조회'),
    ('AUDIT:READ',              'AUDIT',        'READ',    '감사 로그 조회'),
    ('SYSTEM:ADMIN',            'SYSTEM',       'ADMIN',   '시스템 전반 관리');

-- ─────────────────────────────────────────────────────────
-- 4. 역할 × 권한 매핑 시드
-- ─────────────────────────────────────────────────────────

-- SUPER_ADMIN: 전체 권한
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', code FROM permissions;

-- DEPT_ADMIN: 부서 범위 내 사용자/조직 관리
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('DEPT_ADMIN', 'USER:READ'),
    ('DEPT_ADMIN', 'USER:WRITE'),
    ('DEPT_ADMIN', 'USER:UNLOCK'),
    ('DEPT_ADMIN', 'ORGANIZATION:READ'),
    ('DEPT_ADMIN', 'ORGANIZATION:ASSIGN_USER'),
    ('DEPT_ADMIN', 'ROLE:READ'),
    ('DEPT_ADMIN', 'PERMISSION:READ'),
    ('DEPT_ADMIN', 'AUDIT:READ');

-- EDITOR: 콘텐츠 접근 (User/Org 읽기만)
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('EDITOR', 'USER:READ'),
    ('EDITOR', 'ORGANIZATION:READ');

-- VIEWER: 읽기 전용
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('VIEWER', 'USER:READ'),
    ('VIEWER', 'ORGANIZATION:READ');

-- SYSADMIN은 SUPER_ADMIN alias이므로 DB 매핑 불필요
-- (PermissionService.findEffectivePermissionsForRole이 alias 따라 SUPER_ADMIN 권한 반환)
