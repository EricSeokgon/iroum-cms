-- SPEC-CMS-002 REQ-AUTH-006 V4 dev/test 시드: SUPER_ADMIN 계정
-- 비밀번호: AdminP@ss123! (pgcrypto crypt 동적 생성 — $2a 포맷, Spring BCrypt 호환)
--
-- 주의: 이 마이그레이션은 dev/test 환경 전용이다.
--   운영 환경에서는 배포 후 admin 비밀번호를 즉시 변경하거나
--   spring.flyway.enabled=false 로 이 마이그레이션을 비활성화할 것.
--
-- pgcrypto 확장은 V1__init_baseline.sql에서 이미 활성화됨.

INSERT INTO users (username, email, password_hash, name, status,
                   password_changed_at, created_at, updated_at)
VALUES (
    'admin',
    'admin@iroum-cms.local',
    crypt('AdminP@ss123!', gen_salt('bf', 12)),
    '시스템관리자',
    'ACTIVE',
    NOW(),
    NOW(),
    NOW()
)
ON CONFLICT (username) DO NOTHING;

-- SUPER_ADMIN 역할 부여
INSERT INTO user_roles (user_id, role_code, granted_at, granted_by)
SELECT id, 'SUPER_ADMIN', NOW(), id
FROM users
WHERE username = 'admin'
ON CONFLICT (user_id, role_code) DO NOTHING;

COMMENT ON TABLE users IS 'SPEC-CMS-002 v0.3.2 사용자 마스터 (V4 dev 시드 적용 — admin/AdminP@ss123!)';
