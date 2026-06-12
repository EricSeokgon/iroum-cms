-- V52: SUPER_ADMIN 역할에 누락된 권한 동기화
-- 배경: V6 마이그레이션이 당시 permissions 테이블의 권한만 SUPER_ADMIN에 매핑했으나,
--       V13(MENU:*, PAGE:* 등 콘텐츠 권한)과 V14(SYSTEM:CODE:READ 등 시스템 권한)에서
--       추가된 권한들이 role_permissions에 반영되지 않아 SUPER_ADMIN도 403 오류 발생.
-- 수정: permissions 테이블에 존재하지만 SUPER_ADMIN에 매핑되지 않은 모든 권한 추가.
-- 멱등성: ON CONFLICT DO NOTHING

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', p.code
FROM permissions p
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_code = 'SUPER_ADMIN' AND rp.permission_code = p.code
)
ON CONFLICT (role_code, permission_code) DO NOTHING;
