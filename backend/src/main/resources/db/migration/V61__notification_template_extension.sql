-- V60__notification_template_extension.sql
-- SPEC-CMS-NOTI-EXT-001: notification_template 정식 컬럼 확장 + 발송 RBAC 시드
--
-- @MX:NOTE: V16 stub(notification_template)을 정식 CRUD 대상으로 승격한다.
--           subject/body_html 신규 컬럼을 사용하며, V16 stub의 NOT NULL 제약(body_template/channel/name)을 완화한다.
-- @MX:SPEC: SPEC-CMS-NOTI-EXT-001

-- 1) 신규 컬럼 추가
ALTER TABLE notification_template
  ADD COLUMN IF NOT EXISTS subject           VARCHAR(300),
  ADD COLUMN IF NOT EXISTS body_html         TEXT,
  ADD COLUMN IF NOT EXISTS variables         JSONB         DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS language          VARCHAR(10)   NOT NULL DEFAULT 'ko',
  ADD COLUMN IF NOT EXISTS is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS email_template_id BIGINT        REFERENCES email_template(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS created_by        BIGINT        REFERENCES users(id),
  ADD COLUMN IF NOT EXISTS updated_by        BIGINT        REFERENCES users(id),
  ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now();
-- NOTE: created_at은 V16에서 이미 생성됨 — 재추가하지 않음.

-- 2) V16 stub의 NOT NULL 제약 완화 (신규 CRUD는 subject/body_html 사용)
ALTER TABLE notification_template ALTER COLUMN body_template DROP NOT NULL;
ALTER TABLE notification_template ALTER COLUMN channel       DROP NOT NULL;
ALTER TABLE notification_template ALTER COLUMN name          DROP NOT NULL;

-- 3) code 단독 UNIQUE → (code, language) 복합 UNIQUE
ALTER TABLE notification_template DROP CONSTRAINT IF EXISTS notification_template_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_notification_template_code_lang
  ON notification_template (code, language);

-- 4) 권한 시드
INSERT INTO permissions (code, resource, action, description) VALUES
  ('NOTIFICATION_TEMPLATE:READ',   'NOTIFICATION_TEMPLATE', 'READ',   '알림 템플릿 조회'),
  ('NOTIFICATION_TEMPLATE:WRITE',  'NOTIFICATION_TEMPLATE', 'WRITE',  '알림 템플릿 등록/수정'),
  ('NOTIFICATION_TEMPLATE:DELETE', 'NOTIFICATION_TEMPLATE', 'DELETE', '알림 템플릿 삭제'),
  ('POLICY:DISPATCH:READ',         'POLICY',                'READ',   '발송 예약 조회'),
  ('POLICY:DISPATCH:WRITE',        'POLICY',                'WRITE',  '발송 예약 생성/트리거/취소')
ON CONFLICT (code) DO NOTHING;

-- 5) SUPER_ADMIN 권한 매핑
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', code
FROM permissions
WHERE code IN (
  'NOTIFICATION_TEMPLATE:READ', 'NOTIFICATION_TEMPLATE:WRITE', 'NOTIFICATION_TEMPLATE:DELETE',
  'POLICY:DISPATCH:READ', 'POLICY:DISPATCH:WRITE'
)
ON CONFLICT (role_code, permission_code) DO NOTHING;
