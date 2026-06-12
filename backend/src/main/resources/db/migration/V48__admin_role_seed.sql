-- SPEC-CMS-RBAC-001 REQ-RBAC-001 — ADMIN 역할 시드 + role_permissions 매핑
-- 배경: SecurityConfig 가 hasRole('ADMIN') 을 다수 URL 룰에 사용하나 V2 시드에 ADMIN 행이 없음(phantom role).
--       본 마이그레이션으로 ADMIN 역할과 권한 매핑을 실체화한다.
-- 멱등성: ON CONFLICT DO NOTHING — 재실행 시 중복 키 오류 없이 통과(AC-001-4).

-- 1. ADMIN 역할 추가 (is_system=TRUE, aliased_to=NULL — 실제 역할)
INSERT INTO roles (code, name, description, is_system, aliased_to)
VALUES ('ADMIN', '관리자', '일반 관리자 권한 (SUPER_ADMIN 미만)', TRUE, NULL)
ON CONFLICT (code) DO NOTHING;

-- 2. ADMIN 역할 × 권한 매핑
-- 포함: USER:READ/WRITE/UNLOCK/CHANGE_ROLE, ORGANIZATION:READ/WRITE/ASSIGN_USER,
--       ROLE:READ, PERMISSION:READ, AUDIT:READ
-- 제외(SUPER_ADMIN 전용): SYSTEM:ADMIN, USER:DELETE, ORGANIZATION:DELETE, ROLE:WRITE, USER:FORCE_LOGOUT
INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('ADMIN', 'USER:READ'),
    ('ADMIN', 'USER:WRITE'),
    ('ADMIN', 'USER:UNLOCK'),
    ('ADMIN', 'USER:CHANGE_ROLE'),
    ('ADMIN', 'ORGANIZATION:READ'),
    ('ADMIN', 'ORGANIZATION:WRITE'),
    ('ADMIN', 'ORGANIZATION:ASSIGN_USER'),
    ('ADMIN', 'ROLE:READ'),
    ('ADMIN', 'PERMISSION:READ'),
    ('ADMIN', 'AUDIT:READ')
ON CONFLICT (role_code, permission_code) DO NOTHING;
