-- SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-DROP
-- users.email 평문 컬럼 제거 — V24 에서 nullable 전환, V26 에서 최종 DROP
--
-- 변경 요약:
--   1. users.email (VARCHAR(255) nullable) DROP
--      - email_encrypted / email_hmac 경로가 표준 경로로 완전 전환됨
--      - 기존 email_hmac UNIQUE 인덱스 (idx_users_email_hmac) 유지
--   2. data_dictionary — users.email 상태를 REMOVED 로 갱신
--
-- 영향 범위:
--   - UserMapper.xml : email 컬럼 매핑 제거 (코드 동시 패치 — PR V26)
--   - UserServiceImpl: existsByEmail → existsByEmailHmac 전환 (코드 동시 패치)
--   - UserSummary     : findPage/findPageWithScope 에서 email 컬럼 반환 불가
--                       (email 은 서비스 레이어 복호화 후 메모리 주입)
--
-- 사전 조건: 모든 row 의 email_encrypted + email_hmac 이 채워져 있어야 한다.
--            미완료 row 존재 시 PiiEmailMigrationJob 을 먼저 실행해야 한다.

-- ─── 1. 평문 email 컬럼 DROP ─────────────────────────────────────────────────
ALTER TABLE users DROP COLUMN IF EXISTS email;

-- ─── 2. data_dictionary — email 컬럼 상태 REMOVED 갱신 ───────────────────────
UPDATE data_dictionary
SET status      = 'REMOVED',
    description = COALESCE(description, '') || ' [V26 DROP — email_encrypted/email_hmac 경로 사용]',
    updated_at  = CURRENT_TIMESTAMP
WHERE table_name  = 'users'
  AND column_name = 'email';
