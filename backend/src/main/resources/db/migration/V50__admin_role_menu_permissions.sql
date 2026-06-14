-- SPEC-CMS-RBAC-001 보완 — ADMIN 역할에 MENU 관련 권한 추가
-- 배경: V48 에서 ADMIN 역할을 생성할 때 MENU:READ, MENU:WRITE, MENU:PERMISSION:WRITE 가 누락됨.
--       MenuController POST /{id}/permissions 가 MENU:PERMISSION:WRITE 를 요구하므로
--       ADMIN 사용자가 메뉴 권한 저장 시 403 에러 발생.
-- 멱등성: ON CONFLICT DO NOTHING

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('ADMIN', 'MENU:READ'),
    ('ADMIN', 'MENU:WRITE'),
    ('ADMIN', 'MENU:PERMISSION:WRITE')
ON CONFLICT (role_code, permission_code) DO NOTHING;
