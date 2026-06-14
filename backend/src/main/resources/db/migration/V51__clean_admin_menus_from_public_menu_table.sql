-- V51: 공공 웹사이트 메뉴 테이블(menu)에서 관리자 메뉴(ADMIN_*) 제거
-- 관리자 CMS 메뉴는 admin_menu 테이블에서 관리 (V49에서 생성됨)
-- menu 테이블은 공공 웹사이트 방문자에게 표시되는 메뉴 전용으로 사용

-- 자식 항목 먼저 삭제 (FK 제약 방지)
DELETE FROM menu_permissions WHERE menu_id IN (
    SELECT id FROM menu WHERE code LIKE 'ADMIN_%'
);

DELETE FROM menu WHERE code LIKE 'ADMIN_%';
