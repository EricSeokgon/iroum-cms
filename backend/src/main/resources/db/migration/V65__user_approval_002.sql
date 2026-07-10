-- SPEC-CMS-USER-APPROVAL-002 — 사용자 가입 승인 흐름 고도화
-- 기존 인프라 확장 원칙: additive 컬럼 2종 + system_setting 3종 + 이메일 템플릿 2종 시드.
-- NFR-UA2-C3 — 단일 Flyway 파일에 통합한다.

-- Step 1: users 테이블 additive 컬럼 (모두 nullable, 기존 행 백필 불요)
--   reminder_sent_at  : 승인 대기 리마인더 발송 시각(미발송=NULL) — 멱등 키
--   email_verified_at : 이메일 인증 완료 시각(NULL=미인증)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reminder_sent_at  TIMESTAMPTZ DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ DEFAULT NULL;

COMMENT ON COLUMN users.reminder_sent_at  IS '승인 대기 리마인더 발송 시각(미발송=NULL). SPEC-CMS-USER-APPROVAL-002 REQ-UA2-003';
COMMENT ON COLUMN users.email_verified_at IS '이메일 인증 완료 시각(NULL=미인증). SPEC-CMS-USER-APPROVAL-002 REQ-UA2-002';

-- Step 2: system_setting 시드 (V14 테이블 재사용, idempotent)
--   REGISTRATION_APPROVAL_REMINDER_DAYS : 대기 N일 경과 시 리마인더
--   REGISTRATION_APPROVAL_MAX_WAIT_DAYS : 초과 시 자동 거절(0=비활성, 회귀 방지 기본값)
--   REGISTRATION_EMAIL_VERIFY_REQUIRED  : 가입 시 이메일 인증 코드 필수 여부(false=기존 가입 동작 유지)
INSERT INTO system_setting (key, value, value_type, description, created_at, updated_at)
VALUES
    ('REGISTRATION_APPROVAL_REMINDER_DAYS', '3',     'INT',  '승인 대기 N일 경과 시 리마인더 발송', NOW(), NOW()),
    ('REGISTRATION_APPROVAL_MAX_WAIT_DAYS', '0',     'INT',  '승인 대기 초과 시 자동 거절(0=비활성)', NOW(), NOW()),
    ('REGISTRATION_EMAIL_VERIFY_REQUIRED',  'false', 'BOOL', '공개 가입 시 이메일 인증 코드 필수 여부', NOW(), NOW())
ON CONFLICT (key) DO NOTHING;

-- Step 3: 리마인더/자동거절 이메일 템플릿 시드 (V55 email_template 스키마)
--   template_type 은 CHECK 제약에 따라 'CUSTOM'. 변수 치환은 Thymeleaf ${var} 문법.
--   USER_APPROVAL_REMINDER      : 변수 name, pendingDays
--   USER_APPROVAL_AUTO_REJECTED : 변수 name, rejectionReason
INSERT INTO email_template (code, name, template_type, language, subject, body_html, body_text, variables, is_active, created_by)
VALUES
    ('USER_APPROVAL_REMINDER', '가입 승인 대기 리마인더 (한국어)', 'CUSTOM', 'ko',
     '[이루움 CMS] 가입 승인 대기 안내',
     '<p>안녕하세요, <span th:text="${name}">사용자</span>님.</p><p>가입 신청이 <span th:text="${pendingDays}">N</span>일째 승인 대기 중입니다.</p><p>관리자 승인 후 서비스를 이용하실 수 있습니다.</p>',
     '안녕하세요, [(${name})]님. 가입 신청이 [(${pendingDays})]일째 승인 대기 중입니다. 관리자 승인 후 서비스를 이용하실 수 있습니다.',
     '[{"name":"name","description":"사용자 이름","required":false},{"name":"pendingDays","description":"대기 경과일","required":false}]'::jsonb,
     TRUE, NULL),
    ('USER_APPROVAL_REMINDER', '가입 승인 대기 리마인더 (영어)', 'CUSTOM', 'en',
     '[Iroum CMS] Your registration is awaiting approval',
     '<p>Hello <span th:text="${name}">user</span>,</p><p>Your registration has been awaiting approval for <span th:text="${pendingDays}">N</span> day(s).</p><p>You can use the service after an administrator approves it.</p>',
     'Hello [(${name})], your registration has been awaiting approval for [(${pendingDays})] day(s). You can use the service after an administrator approves it.',
     '[{"name":"name","description":"User name","required":false},{"name":"pendingDays","description":"Pending days","required":false}]'::jsonb,
     TRUE, NULL),
    ('USER_APPROVAL_AUTO_REJECTED', '가입 자동 거절 안내 (한국어)', 'CUSTOM', 'ko',
     '[이루움 CMS] 가입 신청이 자동 거절되었습니다',
     '<p>안녕하세요, <span th:text="${name}">사용자</span>님.</p><p>가입 신청이 자동 거절되었습니다.</p><p>사유: <span th:text="${rejectionReason}">사유</span></p>',
     '안녕하세요, [(${name})]님. 가입 신청이 자동 거절되었습니다. 사유: [(${rejectionReason})]',
     '[{"name":"name","description":"사용자 이름","required":false},{"name":"rejectionReason","description":"거절 사유","required":true}]'::jsonb,
     TRUE, NULL),
    ('USER_APPROVAL_AUTO_REJECTED', '가입 자동 거절 안내 (영어)', 'CUSTOM', 'en',
     '[Iroum CMS] Your registration has been automatically rejected',
     '<p>Hello <span th:text="${name}">user</span>,</p><p>Your registration has been automatically rejected.</p><p>Reason: <span th:text="${rejectionReason}">reason</span></p>',
     'Hello [(${name})], your registration has been automatically rejected. Reason: [(${rejectionReason})]',
     '[{"name":"name","description":"User name","required":false},{"name":"rejectionReason","description":"Rejection reason","required":true}]'::jsonb,
     TRUE, NULL)
ON CONFLICT (code, language) DO NOTHING;
