-- SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-001/002/003
-- Email AES-256-GCM 암호화 + HMAC 격상 — V24 PII 암호화 마이그레이션
--
-- 변경 요약:
--   1. users 테이블에 4개 신규 PII 컬럼 추가 (email_encrypted/iv/tag + email_key_version)
--   2. lookup용 email_hmac (HMAC-SHA256) 컬럼 추가 + UNIQUE 인덱스
--   3. 기존 email 컬럼은 NULL 허용으로 변경 (V25에서 DROP 예정)
--   4. data_dictionary (SPEC-CMS-009) 5개 row 시드 등록
--
-- 기존 데이터는 V24 시점에는 그대로 유지되며, 일괄 암호화는 Step 5 운영 배치(PiiEmailMigrationJob)에서 수행한다.

-- ─── 1. AES-256-GCM 암호화 컬럼 (BYTEA 분리 저장) ─────────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_encrypted   BYTEA;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_iv          BYTEA;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_tag         BYTEA;

-- ─── 2. lookup용 HMAC-SHA256 (deterministic이지만 별도 키 → rainbow table 방지) ──
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_hmac        VARCHAR(64);

-- ─── 3. 키 버전 추적 (점진적 키 회전 지원) ────────────────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_key_version SMALLINT NOT NULL DEFAULT 1;

-- ─── 4. UNIQUE 제약: email_hmac (lookup 전용, 부분 인덱스) ───────────────────
-- 마이그레이션 미완료 row는 email_hmac IS NULL 이므로 부분 인덱스로 충돌 회피.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_hmac
    ON users(email_hmac)
    WHERE email_hmac IS NOT NULL;

-- ─── 5. 기존 email 컬럼 NULL 허용 (V25에서 DROP 예정) ────────────────────────
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

COMMENT ON COLUMN users.email IS
    'DEPRECATED — V25에서 제거 예정 (SPEC-CMS-SECURITY-PII-001). email_encrypted/iv/tag 사용.';
COMMENT ON COLUMN users.email_encrypted   IS 'AES-256-GCM 암호문 (가변 길이) — SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-001';
COMMENT ON COLUMN users.email_iv          IS 'GCM IV (NIST 권장 12 bytes = 96 bits)';
COMMENT ON COLUMN users.email_tag         IS 'GCM authentication tag (16 bytes = 128 bits)';
COMMENT ON COLUMN users.email_hmac        IS 'HMAC-SHA256(hmacKey, normalizedEmail) hex 64 chars — lookup 전용 (REQ-PII-EMAIL-003)';
COMMENT ON COLUMN users.email_key_version IS 'PII 암호화 키 버전 (점진적 회전 지원, 1 이상)';

-- ─── 6. data_dictionary (SPEC-CMS-009) PII 컬럼 시드 등록 ────────────────────
-- 실제 V18 스키마: (table_name, column_name, logical_name_ko, logical_name_en, data_domain, data_type, description, is_pii, is_required, status)
INSERT INTO data_dictionary
    (table_name, column_name, logical_name_ko, logical_name_en, data_domain, data_type, description, is_pii, is_required)
VALUES
    ('users', 'email_encrypted',   '암호화 이메일',         'Encrypted Email',     'MASTER', 'BYTEA',       'AES-256-GCM 암호화된 email (SPEC-CMS-SECURITY-PII-001)', TRUE,  FALSE),
    ('users', 'email_iv',          '암호화 IV',            'Encryption IV',       'MASTER', 'BYTEA',       'GCM IV (12 bytes)',                                       FALSE, FALSE),
    ('users', 'email_tag',         '인증 태그',            'Auth Tag',            'MASTER', 'BYTEA',       'GCM auth tag (16 bytes)',                                 FALSE, FALSE),
    ('users', 'email_hmac',        '이메일 HMAC',          'Email HMAC',          'MASTER', 'VARCHAR(64)', 'HMAC-SHA256(hmacKey, normalizedEmail) — lookup 키',       TRUE,  FALSE),
    ('users', 'email_key_version', 'PII 암호화 키 버전',   'PII Key Version',     'MASTER', 'SMALLINT',    'PII 암호화 키 버전 (점진적 회전 지원)',                       FALSE, TRUE)
ON CONFLICT (table_name, column_name) DO UPDATE
SET logical_name_ko = EXCLUDED.logical_name_ko,
    logical_name_en = EXCLUDED.logical_name_en,
    data_domain     = EXCLUDED.data_domain,
    data_type       = EXCLUDED.data_type,
    description     = EXCLUDED.description,
    is_pii          = EXCLUDED.is_pii,
    is_required     = EXCLUDED.is_required,
    updated_at      = CURRENT_TIMESTAMP;
