-- V36__public_member_role.sql
-- 공개 시민 사이트 회원 역할
INSERT INTO roles (code, name, description, is_system, aliased_to)
VALUES ('MEMBER', '회원', '공개 사이트 회원 역할', true, NULL)
ON CONFLICT (code) DO NOTHING;
